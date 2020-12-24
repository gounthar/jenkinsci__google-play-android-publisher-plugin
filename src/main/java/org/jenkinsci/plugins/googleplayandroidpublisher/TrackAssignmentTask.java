package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.api.services.androidpublisher.model.Apk;
import com.google.api.services.androidpublisher.model.Bundle;
import com.google.api.services.androidpublisher.model.LocalizedText;
import com.google.api.services.androidpublisher.model.Track;
import com.google.api.services.androidpublisher.model.TrackRelease;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.model.TaskListener;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static hudson.Util.join;

class TrackAssignmentTask extends TrackPublisherTask<Boolean> {

    private final List<Long> versionCodes;

    TrackAssignmentTask(TaskListener listener, GoogleRobotCredentials credentials, String applicationId,
                        Collection<Long> versionCodes, String trackName, String releaseName, double rolloutPercentage,
                        Integer inAppUpdatePriority) {
        super(listener, credentials, applicationId, trackName, releaseName, rolloutPercentage, inAppUpdatePriority);
        this.versionCodes = new ArrayList<>(versionCodes);
    }

    protected Boolean execute() throws IOException {
        // Open an edit via the Google Play API, thereby ensuring that our credentials etc. are working
        logger.println(String.format("Authenticating to Google Play API...%n- Credential:     %s%n- Application ID: %s",
                getCredentialName(), applicationId));
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

        // Log some useful information
        logger.println(String.format("Assigning %d version(s) with application ID %s to '%s' release track",
                versionCodes.size(), applicationId, trackName));

        // Check that all version codes to assign actually exist already on the server
        // (We could remove this block since Google Play does this check nowadays, but its error messages are
        //  slightly misleading, as they always refer to APK files, even if we're trying to assign AAB files)
        ArrayList<Long> missingVersionCodes = new ArrayList<>(versionCodes);
        List<Apk> existingApks = editService.apks().list(applicationId, editId).execute().getApks();
        if (existingApks == null) existingApks = Collections.emptyList();
        for (Apk apk : existingApks) {
            missingVersionCodes.remove(Long.valueOf(apk.getVersionCode()));
        }
        List<Bundle> existingBundles = editService.bundles().list(applicationId, editId).execute().getBundles();
        if (existingBundles == null) existingBundles = Collections.emptyList();
        for (Bundle bundle : existingBundles) {
            missingVersionCodes.remove(Long.valueOf(bundle.getVersionCode()));
        }
        if (!missingVersionCodes.isEmpty()) {
            logger.println(String.format("Assignment will fail, as these versions do not exist on Google Play: %s",
                    join(missingVersionCodes, ", ")));
            return false;
        }

        if (inAppUpdatePriority != null) {
            logger.println(String.format("Setting in-app update priority to %d", inAppUpdatePriority));
        }

        // Attempt to locate any release notes already uploaded for these files, so we can assign them to the new track
        final Long latestVersion = versionCodes.stream().max(Long::compareTo).orElse(0L);
        List<LocalizedText> releaseNotes = editService.tracks().list(applicationId, editId).execute().getTracks()
            .stream()
            .flatMap(track -> Optional.ofNullable(track.getReleases()).map(Collection::stream).orElseGet(Stream::empty))
            .map(release -> {
                List<Long> versionCodes = release.getVersionCodes();
                return versionCodes != null && versionCodes.contains(latestVersion) ? release.getReleaseNotes() : null;
            })
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        // Assign the version codes to the configured track
        assignAppFilesToTrack(trackName, rolloutFraction, versionCodes, inAppUpdatePriority, releaseName, releaseNotes);

        // Commit the changes
        try {
            logger.println("Applying changes to Google Play...");
            editService.commit(applicationId, editId).execute();
        } catch (SocketTimeoutException e) {
            // TODO: Check, in a new session, whether the given version codes are now in the desired track
            logger.println(String.format("- An error occurred while applying changes: %s", e));
            return false;
        }

        // If committing didn't throw an exception, everything worked fine
        logger.println("Changes were successfully applied to Google Play");
        return true;
    }

}
