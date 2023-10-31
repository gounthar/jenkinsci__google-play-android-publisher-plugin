package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.services.androidpublisher.model.Apk;
import com.google.api.services.androidpublisher.model.Bundle;
import com.google.api.services.androidpublisher.model.ExpansionFile;
import com.google.api.services.androidpublisher.model.ExpansionFilesUploadResponse;
import com.google.api.services.androidpublisher.model.LocalizedText;
import com.google.api.services.androidpublisher.model.Track;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.AppFileFormat;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.UploadFile;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static hudson.Functions.humanReadableByteSize;
import static hudson.Util.join;
import static org.jenkinsci.plugins.googleplayandroidpublisher.ApkPublisher.ExpansionFileSet;
import static org.jenkinsci.plugins.googleplayandroidpublisher.ApkPublisher.RecentChanges;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Constants.DEOBFUSCATION_FILE_TYPE_NATIVE_CODE;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Constants.DEOBFUSCATION_FILE_TYPE_PROGUARD;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Constants.OBB_FILE_TYPE_MAIN;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Constants.OBB_FILE_TYPE_PATCH;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Util.getRelativeFileName;

class ApkUploadTask extends TrackPublisherTask<Boolean> {

    private final FilePath workspace;
    private final List<UploadFile> appFilesToUpload;
    private final Map<Long, ExpansionFileSet> expansionFiles;
    private final boolean usePreviousExpansionFilesIfMissing;
    private final RecentChanges[] recentChangeList;
    private final List<Long> additionalVersionCodes;
    private final List<Long> existingVersionCodes;
    private long latestMainExpansionFileVersionCode;
    private long latestPatchExpansionFileVersionCode;

    // TODO: Could be renamed
    ApkUploadTask(TaskListener listener, GoogleRobotCredentials credentials, String applicationId,
                  FilePath workspace, List<UploadFile> appFilesToUpload, Map<Long, ExpansionFileSet> expansionFiles,
                  boolean usePreviousExpansionFilesIfMissing, String trackName, String releaseName, double rolloutPercentage,
                  ApkPublisher.RecentChanges[] recentChangeList, Integer inAppUpdatePriority, List<Long> additionalVersionCodes) {
        super(listener, credentials, applicationId, trackName, releaseName, rolloutPercentage, inAppUpdatePriority);
        this.workspace = workspace;
        this.appFilesToUpload = appFilesToUpload;
        this.expansionFiles = expansionFiles;
        this.usePreviousExpansionFilesIfMissing = usePreviousExpansionFilesIfMissing;
        this.recentChangeList = recentChangeList;
        this.additionalVersionCodes = additionalVersionCodes;
        this.existingVersionCodes = new ArrayList<>();
    }

    protected Boolean execute() throws IOException, InterruptedException {
        // Open an edit via the Google Play API, thereby ensuring that our credentials etc. are working
        logger.println(String.format("Authenticating to Google Play API...%n" +
                        "- Credential:     %s%n" +
                        "- Application ID: %s%n", getCredentialName(), applicationId));
        createEdit(applicationId);

        // Before doing anything else, verify that the desired track exists
        // TODO: Refactor this and the weird class hierarchy
        List<Track> tracks = editService.tracks().list(applicationId, editId).execute().getTracks();
        String canonicalTrackName = tracks.stream()
            .filter(it -> it.getTrack().equalsIgnoreCase(trackName))
            .map(Track::getTrack)
            .findFirst()
            .orElse(null);
        if (canonicalTrackName == null) {
            // If you ask Google Play for the list of tracks, it won't include any which don't yet have a release…
            // TODO: I don't yet know whether Google Play also ignores built-in tracks, if they have no releases;
            //       but we can make things a little bit smoother by avoiding doing this check for built-in track names,
            //       and ensuring we use the lowercase track name for those
            String msgFormat = "Release track '%s' could not be found on Google Play%n" +
                "- This may be because this track does not yet have any releases, so we will continue… %n" +
                "- Note: Custom track names are case-sensitive; double-check your configuration, if this build fails%n";
            logger.println(String.format(msgFormat, trackName));
        } else {
            // Track names are case-sensitive, so override the user-provided value from the job config
            trackName = canonicalTrackName;
        }

        // Fetch information about the app files that already exist on Google Play
        Set<String> existingAppFileHashes = new HashSet<>();
        List<Bundle> existingBundles = editService.bundles().list(applicationId, editId).execute().getBundles();
        if (existingBundles != null) {
            for (Bundle bundle : existingBundles) {
                existingVersionCodes.add((long) bundle.getVersionCode());
                existingAppFileHashes.add(bundle.getSha1());
            }
        }
        List<Apk> existingApks = editService.apks().list(applicationId, editId).execute().getApks();
        if (existingApks != null) {
            for (Apk apk : existingApks) {
                existingVersionCodes.add((long) apk.getVersionCode());
                existingAppFileHashes.add(apk.getBinary().getSha1());
            }
        }

        // Upload each of the files
        logger.printf("Uploading %d file(s) with application ID: %s%n%n", appFilesToUpload.size(), applicationId);
        final AppFileFormat fileFormat = appFilesToUpload.get(0).getFileFormat();
        final ArrayList<Long> uploadedVersionCodes = new ArrayList<>();
        for (UploadFile appFile : appFilesToUpload) {
            // Log some useful information about the file that will be uploaded
            final String fileType = (fileFormat == AppFileFormat.BUNDLE) ? "AAB" : "APK";
            logger.printf("         %s file: %s%n", fileType, getRelativeFileName(workspace, appFile.getFilePath()));
            logger.printf("        File size: %s%n", humanReadableByteSize(appFile.getFilePath().length()));
            logger.printf("       SHA-1 hash: %s%n", appFile.getSha1Hash());
            logger.printf("      versionCode: %d%n", appFile.getVersionCode());
            logger.printf("      versionName: %s%n", appFile.getVersionName());
            logger.printf("    minSdkVersion: %s%n", appFile.getMinSdkVersion());

            // Check whether this file already exists on the server (i.e. uploading it would fail)
            for (String hash : existingAppFileHashes) {
                if (hash.toLowerCase(Locale.ROOT).equals(appFile.getSha1Hash())) {
                    logger.printf(" %n");
                    logger.println("This file already exists in the Google Play account; it cannot be uploaded again");
                    return false;
                }
            }

            // If not, we can upload the file
            File fileToUpload = new File(appFile.getFilePath().getRemote());
            FileContent fileContent = new FileContent("application/octet-stream", fileToUpload);
            final long uploadedVersionCode;
            if (fileFormat == AppFileFormat.BUNDLE) {
                Bundle uploadedBundle = editService.bundles().upload(applicationId, editId, fileContent)
                        // Prevent Google Play error when uploading large bundles
                        .setAckBundleInstallationWarning(true)
                        .execute();
                uploadedVersionCode = uploadedBundle.getVersionCode();
                uploadedVersionCodes.add(uploadedVersionCode);
            } else {
                Apk uploadedApk = editService.apks().upload(applicationId, editId, fileContent).execute();
                uploadedVersionCode = uploadedApk.getVersionCode();
                uploadedVersionCodes.add(uploadedVersionCode);
            }

            // Upload the ProGuard mapping file for this file, if there is one
            final FilePath mappingFile = appFile.getMappingFile();
            handleMappingFile(uploadedVersionCode, mappingFile, DEOBFUSCATION_FILE_TYPE_PROGUARD, "ProGuard mapping");

            // Upload the native debug symbol file for this file, if there is one
            final FilePath symbolsFile = appFile.getNativeDebugSymbolFile();
            handleMappingFile(uploadedVersionCode, symbolsFile, DEOBFUSCATION_FILE_TYPE_NATIVE_CODE, "Native symbols");
            logger.printf(" %n");
        }

        // Upload the expansion files, or associate the previous ones, if configured
        if (!expansionFiles.isEmpty() || usePreviousExpansionFilesIfMissing) {
            if (fileFormat == AppFileFormat.APK) {
                handleExpansionFiles(uploadedVersionCodes);
            } else {
                logger.println("Ignoring expansion file settings, as we are uploading AAB file(s)");
            }
            logger.printf(" %n");
        }

        if (!additionalVersionCodes.isEmpty()) {
            logger.printf("Including existing version codes: %s", join(additionalVersionCodes, ", "));
            logger.printf(" %n");
            uploadedVersionCodes.addAll(additionalVersionCodes);
        }

        if (inAppUpdatePriority != null) {
            logger.printf("Setting in-app update priority to %d%n", inAppUpdatePriority);
            logger.printf(" %n");
        }

        // Assign all uploaded app files to the configured track
        final String expandedReleaseName = expandReleaseName(releaseName, appFilesToUpload);
        final List<LocalizedText> releaseNotes = Util.transformReleaseNotes(recentChangeList);
        assignAppFilesToTrack(
            trackName, rolloutFraction, uploadedVersionCodes, inAppUpdatePriority, expandedReleaseName, releaseNotes
        );

        // Commit the changes, which will throw an exception if there is a problem
        commit();
        return true;
    }

    private void handleMappingFile(
        long versionCode, @Nullable FilePath mappingFile, String mappingFileTypeId, String mappingFileTypeName
    ) throws IOException, InterruptedException {
        if (mappingFile == null) {
            return;
        }

        // Google Play API doesn't accept empty mapping files
        final String relativeFileName = getRelativeFileName(workspace, mappingFile);
        if (mappingFile.length() == 0) {
            logger.printf(" Ignoring empty %s file: %s%n", mappingFileTypeName, relativeFileName);
        } else {
            logger.printf(" %16s: %s%n", mappingFileTypeName, relativeFileName);
            FileContent mapping = new FileContent("application/octet-stream", new File(mappingFile.getRemote()));
            editService.deobfuscationfiles().upload(applicationId, editId, Math.toIntExact(versionCode),
                    mappingFileTypeId, mapping).execute();
        }
    }

    /** Applies the appropriate expansion file to each given APK version. */
    private void handleExpansionFiles(Collection<Long> uploadedVersionCodes) throws IOException {
        // Ensure that the version codes are sorted in ascending order, as this allows us to
        // upload an expansion file with the lowest version, and re-use it for subsequent APKs
        SortedSet<Long> sortedVersionCodes = new TreeSet<>(uploadedVersionCodes);

        // If we want to re-use existing expansion files, figure out what the latest values are
        if (usePreviousExpansionFilesIfMissing) {
            fetchLatestExpansionFileVersionCodes();
        }

        // Upload or apply the expansion files for each APK we've uploaded
        for (long versionCode : sortedVersionCodes) {
            ExpansionFileSet fileSet = expansionFiles.get(versionCode);
            FilePath mainFile = fileSet == null ? null : fileSet.getMainFile();
            FilePath patchFile = fileSet == null ? null : fileSet.getPatchFile();

            logger.println(String.format("Handling expansion files for versionCode %d", versionCode));
            applyExpansionFile(versionCode, OBB_FILE_TYPE_MAIN, mainFile, usePreviousExpansionFilesIfMissing);
            applyExpansionFile(versionCode, OBB_FILE_TYPE_PATCH, patchFile, usePreviousExpansionFilesIfMissing);
            logger.printf(" %n");
        }
    }

    /** Applies an expansion file to an APK, whether from a given file, or by using previously-uploaded file. */
    private void applyExpansionFile(long versionCode, String type, FilePath filePath, boolean usePreviousIfMissing)
            throws IOException {
        // If there was a file provided, simply upload it
        if (filePath != null) {
            logger.println(String.format("- Uploading new %s expansion file: %s", type, filePath.getName()));
            uploadExpansionFile(versionCode, type, filePath);
            return;
        }

        // Otherwise, check whether we should reuse an existing expansion file
        if (usePreviousIfMissing) {
            // If there is no previous APK with this type of expansion file, there's nothing we can do
            final long latestVersionCodeWithExpansion = type.equals(OBB_FILE_TYPE_MAIN) ?
                    latestMainExpansionFileVersionCode : latestPatchExpansionFileVersionCode;
            if (latestVersionCodeWithExpansion == -1) {
                logger.println(String.format("- No %1$s expansion file to apply, and no existing APK with a %1$s " +
                        "expansion file was found", type));
                return;
            }

            // Otherwise, associate the latest expansion file of this type with the new APK
            logger.println(String.format("- Applying %s expansion file from previous APK: %d", type,
                    latestVersionCodeWithExpansion));
            ExpansionFile fileRef = new ExpansionFile().setReferencesVersion(Math.toIntExact(latestVersionCodeWithExpansion));
            editService.expansionfiles().update(applicationId, editId, Math.toIntExact(versionCode), type, fileRef).execute();
            return;
        }

        // If we don't want to reuse an existing file, then there's nothing to do
        logger.println(String.format("- No %s expansion file to apply", type));
    }

    /** Determines whether there are already-existing APKs for this app which have expansion files associated. */
    private void fetchLatestExpansionFileVersionCodes() throws IOException {
        // Find the latest APK with a main expansion file, and the latest with a patch expansion file
        latestMainExpansionFileVersionCode = fetchLatestExpansionFileVersionCode(OBB_FILE_TYPE_MAIN);
        latestPatchExpansionFileVersionCode = fetchLatestExpansionFileVersionCode(OBB_FILE_TYPE_PATCH);
    }

    /** @return The version code of the newest APK which has an expansion file of this type, else {@code -1}. */
    private long fetchLatestExpansionFileVersionCode(String type) throws IOException {
        // Find the latest APK with an expansion file, i.e. sort version codes in descending order
        SortedSet<Long> newestVersionCodes = new TreeSet<>((a, b) -> ((int) (b - a)));
        newestVersionCodes.addAll(existingVersionCodes);
        for (long versionCode : newestVersionCodes) {
            ExpansionFile file = getExpansionFile(versionCode, type);
            if (file == null) {
                continue;
            }
            if (file.getFileSize() != null && file.getFileSize() > 0) {
                return versionCode;
            }
            if (file.getReferencesVersion() != null && file.getReferencesVersion() > 0) {
                return file.getReferencesVersion();
            }
        }

        // There's no existing expansion file of this type
        return -1;
    }

    /** @return The expansion file API info for the given criteria, or {@code null} if no such file exists. */
    private ExpansionFile getExpansionFile(long versionCode, String type) throws IOException {
        try {
            return editService.expansionfiles().get(applicationId, editId, Math.toIntExact(versionCode), type).execute();
        } catch (GoogleJsonResponseException e) {
            // A 404 response from the API means that there is no such expansion file/reference
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Uploads the given file as an certain type expansion file, associating it with a given APK.
     *
     * @return The expansion file API response.
     */
    private ExpansionFilesUploadResponse uploadExpansionFile(long versionCode, String type, FilePath filePath)
            throws IOException {
        // Upload the file
        FileContent file = new FileContent("application/octet-stream", new File(filePath.getRemote()));
        ExpansionFilesUploadResponse response = editService.expansionfiles()
                .upload(applicationId, editId, Math.toIntExact(versionCode), type, file).execute();

        // Keep track of the now-latest APK with an expansion file, so we can associate the
        // same expansion file with subsequent APKs that were uploaded in this session
        if (type.equals(OBB_FILE_TYPE_MAIN)) {
            latestMainExpansionFileVersionCode = versionCode;
        } else {
            latestPatchExpansionFileVersionCode = versionCode;
        }

        return response;
    }

    @Nullable
    private static String expandReleaseName(@Nullable String releaseName, @NonNull List<UploadFile> appFilesToUpload) {
        if (releaseName == null) {
            return null;
        }
        return releaseName
            .replace("{versionCode}", String.valueOf(appFilesToUpload.get(0).getVersionCode()))
            .replace("{versionName}", appFilesToUpload.get(0).getVersionName());
    }

}
