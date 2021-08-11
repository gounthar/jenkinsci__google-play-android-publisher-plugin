package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.api.services.androidpublisher.model.LocalizedText;
import com.google.api.services.androidpublisher.model.Track;
import com.google.api.services.androidpublisher.model.TrackRelease;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.model.TaskListener;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    /** Assigns a release, which contains a list of version codes, to a release track. */
    void assignAppFilesToTrack(
        String trackName, double rolloutFraction, List<Long> versionCodes, @Nullable Integer inAppUpdatePriority,
        @Nullable String releaseName, @Nullable List<LocalizedText> releaseNotes
    ) throws IOException {
        // Prepare to assign the release to the desired track
        final TrackRelease release = Util.buildRelease(
            versionCodes, releaseName, rolloutFraction, inAppUpdatePriority, releaseNotes
        );
        final Track trackToAssign = new Track()
            .setTrack(trackName)
            .setReleases(Collections.singletonList(release));

        // Log what's happening
        final boolean isDraft = release.getStatus().equals("draft");
        logger.printf("Updating release track '%s':%n", trackName);
        logger.printf("- Application ID:  %s%n", applicationId);
        logger.printf("- Version codes:   %s%n", join(versionCodes.stream().sorted().collect(Collectors.toList()), ", "));
        logger.printf("- Staged rollout:  %s%% %s%n", PERCENTAGE_FORMATTER.format(rolloutFraction * 100), isDraft ? "(draft)" : "");
        logger.printf("- Update priority: %s%n", inAppUpdatePriority == null ? "(default)" : inAppUpdatePriority);
        logger.printf("- Release name:    %s%n", releaseName == null ? "(default)" : releaseName);
        logger.printf("- Release notes:   %s%n%n", joinReleaseNoteLanguages(releaseNotes));

        // Update the track
        editService.tracks().update(applicationId, editId, trackToAssign.getTrack(), trackToAssign).execute();
    }

    private static String joinReleaseNoteLanguages(List<LocalizedText> releaseNotes) {
        if (releaseNotes == null) {
            return "(none)";
        }
        List<String> list = releaseNotes.stream().map(LocalizedText::getLanguage).sorted().collect(Collectors.toList());
        return join(list, ", ");
    }

}
