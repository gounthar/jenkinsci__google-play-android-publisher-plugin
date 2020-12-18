package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.api.services.androidpublisher.model.Track;
import com.google.api.services.androidpublisher.model.TrackRelease;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Collections;
import static hudson.Util.join;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Constants.PERCENTAGE_FORMATTER;

abstract class TrackPublisherTask<V> extends AbstractPublisherTask<V> {

    protected final String applicationId;
    protected String trackName;
    protected final String releaseName;
    protected final double rolloutFraction;
    protected final Integer inAppUpdatePriority;

    TrackPublisherTask(TaskListener listener, GoogleRobotCredentials credentials, String applicationId,
                       String trackName, String releaseName, double rolloutPercentage, Integer inAppUpdatePriority) {
        super(listener, credentials);
        this.applicationId = applicationId;
        this.trackName = trackName;
        this.releaseName = releaseName;
        this.rolloutFraction = rolloutPercentage / 100d;
        this.inAppUpdatePriority = inAppUpdatePriority;
    }

    /**
     * Assigns a release, which contains a list of version codes, to a release track.
     *
     * @param trackName The track to which the version codes should be assigned.
     * @param rolloutFraction The rollout fraction, if track is a staged rollout.
     */
    void assignAppFilesToTrack(String trackName, double rolloutFraction, TrackRelease release) throws IOException {
        // Prepare to assign the release to the desired track
        final Track trackToAssign = new Track()
                .setTrack(trackName)
                .setReleases(Collections.singletonList(release));

        final boolean isDraft = release.getStatus().equals("draft");
        if (!isDraft) {
            logger.println(String.format("Setting rollout to target %s%% of '%s' track users",
                    PERCENTAGE_FORMATTER.format(rolloutFraction * 100), trackName));
        }

        // Assign the new file(s) to the desired track
        Track updatedTrack =
                editService.tracks().update(applicationId, editId, trackToAssign.getTrack(), trackToAssign).execute();

        final String msgFormat;
        if (isDraft) {
            msgFormat = "New '%s' draft release created, with the version code(s): %s%n";
        } else {
            msgFormat = "The '%s' release track will now contain the version code(s): %s%n";
        }
        logger.println(String.format(msgFormat, trackName,
                join(updatedTrack.getReleases().get(0).getVersionCodes(), ", ")));

        if (releaseName != null && !releaseName.isEmpty()) {
            logger.println(String.format("Using name '%s' for this release", releaseName));
        } else {
            logger.println("Using default name for this release");
        }
    }

}
