package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.services.androidpublisher.model.Apk;
import com.google.api.services.androidpublisher.model.Bundle;
import com.google.api.services.androidpublisher.model.ExpansionFile;
import com.google.api.services.androidpublisher.model.ExpansionFilesUploadResponse;
import com.google.api.services.androidpublisher.model.LocalizedText;
import com.google.api.services.androidpublisher.model.Track;
import com.google.api.services.androidpublisher.model.TrackRelease;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.AppFileFormat;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.UploadFile;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static hudson.Functions.humanReadableByteSize;
import static org.jenkinsci.plugins.googleplayandroidpublisher.ApkPublisher.ExpansionFileSet;
import static org.jenkinsci.plugins.googleplayandroidpublisher.ApkPublisher.RecentChanges;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Constants.DEOBFUSCATION_FILE_TYPE_PROGUARD;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Constants.OBB_FILE_TYPE_MAIN;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Constants.OBB_FILE_TYPE_PATCH;

class ApkUploadTask extends TrackPublisherTask<Boolean> {

    private final FilePath workspace;
    private final List<UploadFile> appFilesToUpload;
    private final Map<Long, ExpansionFileSet> expansionFiles;
    private final boolean usePreviousExpansionFilesIfMissing;
    private final RecentChanges[] recentChangeList;
    private final List<Long> existingVersionCodes;
    private long latestMainExpansionFileVersionCode;
    private long latestPatchExpansionFileVersionCode;

    // TODO: Could be renamed
    ApkUploadTask(TaskListener listener, GoogleRobotCredentials credentials, String applicationId,
                  FilePath workspace, List<UploadFile> appFilesToUpload, Map<Long, ExpansionFileSet> expansionFiles,
                  boolean usePreviousExpansionFilesIfMissing, String trackName, String releaseName, double rolloutPercentage,
                  ApkPublisher.RecentChanges[] recentChangeList, Integer inAppUpdatePriority) {
        super(listener, credentials, applicationId, trackName, releaseName, rolloutPercentage, inAppUpdatePriority);
        this.workspace = workspace;
        this.appFilesToUpload = appFilesToUpload;
        this.expansionFiles = expansionFiles;
        this.usePreviousExpansionFilesIfMissing = usePreviousExpansionFilesIfMissing;
        this.recentChangeList = recentChangeList;
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
        logger.println(String.format("Uploading %d file(s) with application ID: %s%n", appFilesToUpload.size(), applicationId));
        final AppFileFormat fileFormat = appFilesToUpload.get(0).getFileFormat();
        final ArrayList<Long> uploadedVersionCodes = new ArrayList<>();
        for (UploadFile appFile : appFilesToUpload) {
            // Log some useful information about the file that will be uploaded
            final String fileType = (fileFormat == AppFileFormat.BUNDLE) ? "AAB" : "APK";
            logger.println(String.format("      %s file: %s", fileType, getRelativeFileName(appFile.getFilePath())));
            logger.println(String.format("     File size: %s", humanReadableByteSize(appFile.getFilePath().length())));
            logger.println(String.format("    SHA-1 hash: %s", appFile.getSha1Hash()));
            logger.println(String.format("   versionCode: %d", appFile.getVersionCode()));
            logger.println(String.format("   versionName: %s", appFile.getVersionName()));
            logger.println(String.format(" minSdkVersion: %s", appFile.getMinSdkVersion()));

            // Check whether this file already exists on the server (i.e. uploading it would fail)
            for (String hash : existingAppFileHashes) {
                if (hash.toLowerCase(Locale.ROOT).equals(appFile.getSha1Hash())) {
                    logger.println();
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
            if (mappingFile != null) {
                final String relativeFileName = getRelativeFileName(mappingFile);

                // Google Play API doesn't accept empty mapping files
                logger.println(String.format(" Mapping file size: %s", mappingFile.length()));
                if (mappingFile.length() == 0) {
                    logger.println(String.format(" Ignoring empty ProGuard mapping file: %s", relativeFileName));
                } else {
                    logger.println(String.format(" Uploading associated ProGuard mapping file: %s", relativeFileName));
                    FileContent mapping =
                            new FileContent("application/octet-stream", new File(mappingFile.getRemote()));
                    editService.deobfuscationfiles().upload(applicationId, editId, Math.toIntExact(uploadedVersionCode),
                            DEOBFUSCATION_FILE_TYPE_PROGUARD, mapping).execute();
                }
            }
            logger.println("");
        }

        // Upload the expansion files, or associate the previous ones, if configured
        if (!expansionFiles.isEmpty() || usePreviousExpansionFilesIfMissing) {
            if (fileFormat == AppFileFormat.APK) {
                handleExpansionFiles(uploadedVersionCodes);
            } else {
                logger.println("Ignoring expansion file settings, as we are uploading AAB file(s)");
            }
        }

        if (inAppUpdatePriority != null) {
            logger.println(String.format("Setting in-app update priority to %d", inAppUpdatePriority));
        }

        // Assign all uploaded app files to the configured track
        List<LocalizedText> releaseNotes = Util.transformReleaseNotes(recentChangeList);
        TrackRelease release =
                Util.buildRelease(uploadedVersionCodes, releaseName, rolloutFraction, inAppUpdatePriority, releaseNotes);
        assignAppFilesToTrack(trackName, rolloutFraction, release);

        // Commit all the changes
        try {
            logger.println("Applying changes to Google Play...");
            editService.commit(applicationId, editId).execute();
        } catch (SocketTimeoutException e) {
            // The API is quite prone to timing out for no apparent reason,
            // despite having successfully committed the changes on the backend.
            // So here we check whether the files uploaded were actually committed
            logger.println(String.format("- An error occurred while applying changes: %s", e));
            logger.println("- Checking whether the changes have been applied anyway...\n");
            if (!wereAppFilesUploaded(uploadedVersionCodes)) {
                logger.println("The files that were uploaded were not found on Google Play");
                logger.println("- No changes have been applied to the Google Play account");
                return false;
            }
        }

        // If committing didn't throw an exception, everything worked fine
        logger.println("Changes were successfully applied to Google Play");
        return true;
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
            logger.println();
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

    /**
     * Starts a new API session and determines whether a list of version codes were successfully uploaded.
     *
     * @param uploadedVersionCodes The list to be checked for existence.
     * @return {@code true} if the version codes in the list were found to now exist on Google Play.
     */
    private boolean wereAppFilesUploaded(Collection<Long> uploadedVersionCodes) throws IOException {
        // Last edit is finished; create a new one to get the current state
        createEdit(applicationId);

        // Get the current list of version codes from Google Play
        List<Long> currentVersionCodes = new ArrayList<>();
        List<Apk> currentApks = editService.apks().list(applicationId, editId).execute().getApks();
        if (currentApks == null) currentApks = Collections.emptyList();
        for (Apk apk : currentApks) {
            currentVersionCodes.add(Long.valueOf(apk.getVersionCode()));
        }
        List<Bundle> currentBundles = editService.bundles().list(applicationId, editId).execute().getBundles();
        if (currentBundles == null) currentBundles = Collections.emptyList();
        for (Bundle bundle : currentBundles) {
            currentVersionCodes.add(Long.valueOf(bundle.getVersionCode()));
        }

        // The upload succeeded if the current list of version codes intersects with the list we tried to upload
        return uploadedVersionCodes.removeAll(currentVersionCodes);
    }

    /** @return The path to the given file, relative to the build workspace. */
    private String getRelativeFileName(FilePath file) {
        final String ws = workspace.getRemote();
        String path = file.getRemote();
        if (path.startsWith(ws) && path.length() > ws.length()) {
            path = path.substring(ws.length());
        }
        if (path.charAt(0) == File.separatorChar && path.length() > 1) {
            path = path.substring(1);
        }
        return path;
    }

}
