package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.LocalizedText;
import com.google.api.services.androidpublisher.model.Track;
import com.google.api.services.androidpublisher.model.TrackRelease;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.DumbSlave;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.AndroidUtil;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.JenkinsUtil;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestHttpTransport;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestUtilImpl;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses.FakeAssignTrackResponse;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses.FakeCommitResponse;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses.FakeListApksResponse;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses.FakeListBundlesResponse;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses.FakeListTracksResponse;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses.FakePostEditsResponse;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestsHelper.assertLogLines;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestsHelper.assertResultWithLogLines;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestsHelper.createAndroidPublisher;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestsHelper.getRequestBodyForUrl;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestsHelper.release;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestsHelper.setUpCredentials;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestsHelper.track;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class ReleaseTrackAssignmentBuilderTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private AndroidUtil mockAndroid = mock(AndroidUtil.class);
    private JenkinsUtil jenkinsUtil = spy(TestUtilImpl.class);

    private TestHttpTransport transport = new TestHttpTransport();

    @Before
    public void setUp() throws Exception {
        when(mockAndroid.getApkVersionCode(any())).thenReturn(42);
        when(mockAndroid.getApkPackageName(any())).thenReturn("org.jenkins.appId");

        // Create fake AndroidPublisher client
        AndroidPublisher androidClient = createAndroidPublisher(transport);
        when(jenkinsUtil.createPublisherClient(any(), anyString())).thenReturn(androidClient);

        Util.setAndroidUtil(mockAndroid);
        Util.setJenkinsUtil(jenkinsUtil);
    }

    @After
    public void tearDown() throws Exception {
        transport.dumpRequests();
    }

    @Test
    public void configRoundtripWorks() throws Exception {
        // Given that a few credentials have been set up
        setUpCredentials("credential-a");
        setUpCredentials("credential-b");
        setUpCredentials("credential-c");

        // And we have a job configured with the builder, which includes all possible configuration options
        FreeStyleProject project = j.createFreeStyleProject();

        ReleaseTrackAssignmentBuilder builder = new ReleaseTrackAssignmentBuilder();
        // Choose the second credential, so that when the config page loads, we can differentiate between the dropdown
        // working as expected vs just appearing to work because the first credential would be selected by default
        builder.setGoogleCredentialsId("credential-b");
        builder.setFromVersionCode(false);
        builder.setApplicationId("org.jenkins.appId");
        builder.setVersionCodes("42");
        builder.setFilesPattern("**/*.apk");
        builder.setTrackName("production");
        builder.setReleaseName("1.2.3");
        builder.setRolloutPercentage("5");
        project.getBuildersList().add(builder);

        // When we open and save the configuration page for this job
        project = j.configRoundtrip(project);

        // Then the publisher object should have been serialised and deserialised, without any changes
        j.assertEqualDataBoundBeans(builder, project.getBuildersList().get(0));
    }

    @Test
    public void movingApkTrackWithoutTrackNameFails() throws Exception {
        // Given a job where the track name is not provided
        FreeStyleProject p = j.createFreeStyleProject();
        ReleaseTrackAssignmentBuilder builder = createBuilder();
        builder.setTrackName(null);
        p.getBuildersList().add(builder);

        // And the prerequisites are in place
        setUpCredentials("test-credentials");
        setUpTransportForSuccess();

        // When a build occurs
        // Then it should fail as the track name has not been specified
        assertResultWithLogLines(j, p, Result.FAILURE, "Release track was not specified");
    }

    @Test
    public void movingApkTrackWithEmptyTrackNameFails() throws Exception {
        // Given a job where the track name is empty (e.g. saved without entering a value, or an empty parameter value)
        FreeStyleProject p = j.createFreeStyleProject();
        ReleaseTrackAssignmentBuilder builder = createBuilder();
        builder.setTrackName("");
        p.getBuildersList().add(builder);

        // And the prerequisites are in place
        setUpCredentials("test-credentials");
        setUpTransportForSuccess();

        // When a build occurs
        // Then it should fail as the track name has not been specified
        assertResultWithLogLines(j, p, Result.FAILURE, "Release track was not specified");
    }

    @Test
    public void movingApkTrackWithoutRolloutPercentageFails() throws Exception {
        // Given a job where the rollout percentage is not provided
        FreeStyleProject p = j.createFreeStyleProject();
        ReleaseTrackAssignmentBuilder builder = new ReleaseTrackAssignmentBuilder();
        setUpCredentials("test-credentials");
        builder.setGoogleCredentialsId("test-credentials");
        builder.setApplicationId("org.jenkins.appId");
        builder.setVersionCodes("42");
        builder.setTrackName("production");

        p.getBuildersList().add(builder);

        // And the prerequisites are in place
        setUpCredentials("test-credentials");
        setUpTransportForSuccess();

        // When a build occurs
        // Then it should fail as the rollout percentage has not been specified
        assertResultWithLogLines(j, p, Result.FAILURE, "Rollout percentage was not specified");
    }

    @Test
    public void movingApkTrackWithEmptyRolloutPercentageFails() throws Exception {
        // Given a job where the percentage is empty (e.g. saved without entering a value, or an empty parameter value)
        FreeStyleProject p = j.createFreeStyleProject();
        ReleaseTrackAssignmentBuilder builder = createBuilder();
        builder.setRolloutPercentage("");
        p.getBuildersList().add(builder);

        // And the prerequisites are in place
        setUpCredentials("test-credentials");
        setUpTransportForSuccess();

        // When a build occurs
        // Then it should fail as the rollout percentage has not been specified
        assertResultWithLogLines(j, p, Result.FAILURE, "Rollout percentage was not specified");
    }

    @Test
    public void movingApkTrackWithInvalidRolloutPercentageFails() throws Exception {
        // Given a job where the rollout percentage can't be parsed
        FreeStyleProject p = j.createFreeStyleProject();
        ReleaseTrackAssignmentBuilder builder = createBuilder();
        builder.setRolloutPercentage("everyone");
        p.getBuildersList().add(builder);

        // And the prerequisites are in place
        setUpCredentials("test-credentials");
        setUpTransportForSuccess();

        // When a build occurs
        // Then it should fail as the rollout percentage is not valid
        assertResultWithLogLines(j, p, Result.FAILURE, "'everyone' is not a valid rollout percentage");
    }

    @Test
    public void moveApkTrack_whenVersionCodeDoesNotExist_buildFails() throws Exception {
        transport
                .withResponse("/edits",
                        new FakePostEditsResponse().setEditId("the-edit-id"))
                .withResponse("/edits/the-edit-id/tracks",
                        new FakeListTracksResponse().setTracks(
                            new ArrayList<Track>() {{
                                add(track("production"));
                            }}
                        ))
                .withResponse("/edits/the-edit-id/apks",
                        new FakeListApksResponse().setEmptyApks())
                .withResponse("/edits/the-edit-id/bundles",
                        new FakeListBundlesResponse().setEmptyBundles())
        ;

        FreeStyleProject p = j.createFreeStyleProject("moveReleaseTrack");

        ReleaseTrackAssignmentBuilder builder = createBuilder();

        p.getBuildersList().add(builder);
        QueueTaskFuture<FreeStyleBuild> scheduled = p.scheduleBuild2(0);
        j.assertBuildStatus(Result.FAILURE, scheduled);
        j.assertLogContains("Assignment will fail, as these versions do not exist on Google Play: 42", scheduled.get());
    }

    @Test
    public void moveApkTrack_succeeds() throws Exception {
        setUpTransportForSuccess();

        FreeStyleProject p = j.createFreeStyleProject("moveReleaseTrack");
        ReleaseTrackAssignmentBuilder builder = createBuilder();
        p.getBuildersList().add(builder);
        QueueTaskFuture<FreeStyleBuild> scheduled = p.scheduleBuild2(0);
        j.assertBuildStatusSuccess(scheduled);

        assertLogLines(j, scheduled,
            "Updating release track 'production':",
            "- Application ID:  org.jenkins.appId",
            "- Version codes:   42",
            "- Staged rollout:  5%",
            "- Update priority: (default)",
            "- Release name:    (default)",
            "- Release notes:   de-DE, en-GB",
            "Changes were successfully applied to Google Play"
        );

        // And we should have included the existing release notes when updating the new track
        Track track = getRequestBodyForUrl(
            transport, "/org.jenkins.appId/edits/the-edit-id/tracks/production", Track.class
        );
        List<LocalizedText> releaseNotes = track.getReleases().get(0).getReleaseNotes();
        assertNotNull(releaseNotes);
        assertEquals(2, releaseNotes.size());
        assertEquals("Notes: en-GB", releaseNotes.get(0).getText());
        assertEquals("Notes: de-DE", releaseNotes.get(1).getText());
    }

    @Test
    public void movingApkTrackAsDraftSucceeds() throws Exception {
        // Given a job, configured to upload as a draft
        FreeStyleProject p = j.createFreeStyleProject();
        ReleaseTrackAssignmentBuilder builder = createBuilder();
        builder.setRolloutPercentage("0%");
        p.getBuildersList().add(builder);

        // And the prerequisites are in place
        setUpCredentials("test-credentials");
        setUpTransportForSuccess();

        // When a build occurs, it should create the release in the target track as a draft
        assertResultWithLogLines(j, p, Result.SUCCESS,
            "Updating release track 'production':",
            "- Application ID:  org.jenkins.appId",
            "- Version codes:   42",
            "- Staged rollout:  0% (draft)",
            "- Update priority: (default)",
            "- Release name:    (default)",
            "- Release notes:   de-DE, en-GB",
            "Changes were successfully applied to Google Play"
        );

        // And we should have set draft status when updating the track
        Track track = getRequestBodyForUrl(
            transport, "/org.jenkins.appId/edits/the-edit-id/tracks/production", Track.class
        );
        TrackRelease release = track.getReleases().get(0);
        assertEquals("draft", release.getStatus());
        assertNull(release.getUserFraction());
    }

    @Test
    public void movingApkToCustomTrackSucceeds() throws Exception {
        // Given a job, configured to move APKs to a custom release track
        FreeStyleProject p = j.createFreeStyleProject();
        ReleaseTrackAssignmentBuilder builder = createBuilder();
        builder.setTrackName("DogFood"); // case should not matter
        p.getBuildersList().add(builder);

        // And the prerequisites are in place
        setUpCredentials("test-credentials");
        setUpTransportForSuccess("dogfood");

        // When a build occurs
        // Then the APK should be successfully assigned to the custom track
        assertResultWithLogLines(j, p, Result.SUCCESS,
            "Updating release track 'dogfood':",
            "- Staged rollout:  5%",
            "Changes were successfully applied to Google Play"
        );
    }

    @Test
    public void movingApkToUnpublishedCustomTrackSucceeds() throws Exception {
        // Given a job, configured to move APKs to a custom release track
        FreeStyleProject p = j.createFreeStyleProject();
        ReleaseTrackAssignmentBuilder builder = createBuilder();
        builder.setTrackName("dogfood");
        p.getBuildersList().add(builder);

        // And the prerequisites are in place
        // But the initial 'tracks' response won't include the track, as it doesn't yet have any releases
        setUpCredentials("test-credentials");
        setUpTransportForSuccess("dogfood", false);

        // When a build occurs
        // Then the APK should be successfully assigned to the custom track
        // And we should have seen the warning about the track not being returned by Google Play
        assertResultWithLogLines(j, p, Result.SUCCESS,
            "Release track 'dogfood' could not be found on Google Play",
            "Updating release track 'dogfood':",
            "- Staged rollout:  5%",
            "Changes were successfully applied to Google Play"
        );
    }

    @Test
    public void movingApkToNonExistentCustomTrackFails() throws Exception {
        // Given a job, configured to move APKs to a custom release track
        // But the track does not exist on the backend
        FreeStyleProject p = j.createFreeStyleProject();
        ReleaseTrackAssignmentBuilder builder = createBuilder();
        builder.setTrackName("non-existent-track");
        p.getBuildersList().add(builder);

        // And the prerequisites are in place
        setUpCredentials("test-credentials");
        setUpTransportForSuccess();

        // When a build occurs
        // Then it should fail with a message about the missing track
        assertResultWithLogLines(j, p, Result.FAILURE, "Release track 'non-existent-track' could not be found");
    }

    @Test
    public void movingApkTrackWithPipelineWithoutTrackNameFails() throws Exception {
        // Given a Pipeline where the track name is not provided
        String stepDefinition = "androidApkMove googleCredentialsId: 'test-credentials'";

        // When a build occurs
        // Then it should fail as the track name has not been specified
        moveApkTrackWithPipelineAndAssertFailure(stepDefinition, "Release track was not specified");
    }

    @Test
    public void movingApkTrackWithPipelineWithEmptyTrackNameFails() throws Exception {
        // Given a Pipeline where the track name is empty (e.g. an empty parameter value)
        String stepDefinition = "androidApkMove googleCredentialsId: 'test-credentials'";

        // When a build occurs
        // Then it should fail as the track name has not been specified
        moveApkTrackWithPipelineAndAssertFailure(stepDefinition, "Release track was not specified");
    }


    @Test
    public void movingApkTrackWithPipelineWithInvalidInAppUpdatePriorityFails() throws Exception {
        // Given a Pipeline where the inAppUpdatePriority is not provided
        String stepDefinition = "androidApkMove googleCredentialsId: 'test-credentials',\n" +
                "  rolloutPercentage: '100',\n"+
                "  inAppUpdatePriority: 'fake'";

        // When a build occurs
        // Then it should fail as inAppUpdatePriority is invalid
        moveApkTrackWithPipelineAndAssertFailure(stepDefinition, "'fake' is not a valid update priority");
    }

    @Test
    public void movingApkTrackWithPipelineWithoutRolloutPercentageFails() throws Exception {
        // Given a Pipeline where the rollout percentage is not provided
        String stepDefinition =
            "  androidApkMove googleCredentialsId: 'test-credentials',\n" +
            "    trackName: 'production',\n" +
            "    fromVersionCode: true,\n" +
            "    applicationId: 'org.jenkins.appId',\n" +
            "    versionCodes: '42'";

        // When a build occurs
        // Then it should fail as the track name has not been specified
        moveApkTrackWithPipelineAndAssertFailure(stepDefinition, "Rollout percentage was not specified");
    }

    @Test
    public void movingApkTrackWithPipelineWithEmptyRolloutPercentageFails() throws Exception {
        // Given a Pipeline where the rollout percentage is empty
        String stepDefinition =
            "  androidApkMove googleCredentialsId: 'test-credentials',\n" +
            "    trackName: 'production',\n" +
            "    fromVersionCode: true,\n" +
            "    applicationId: 'org.jenkins.appId',\n" +
            "    versionCodes: '42',\n" +
            "    rolloutPercentage: ''";

        // When a build occurs
        // Then it should fail as the track name has not been specified
        moveApkTrackWithPipelineAndAssertFailure(stepDefinition, "Rollout percentage was not specified");
    }

    @Test
    public void moveApkTrackWithPipeline_succeeds_with_inAppUpdatePriority() throws Exception {
        String stepDefinition =
            "  androidApkMove googleCredentialsId: 'test-credentials',\n" +
            "    trackName: 'production',\n" +
            "    fromVersionCode: true,\n" +
            "    applicationId: 'org.jenkins.appId',\n" +
            "    versionCodes: '42',\n" +
            "    rolloutPercentage: '100',\n"+
            "    inAppUpdatePriority: '2'\n";

        moveApkTrackWithPipelineAndAssertSuccess(
            stepDefinition,
            "Updating release track 'production':",
            "- Application ID:  org.jenkins.appId",
            "- Version codes:   42",
            "- Staged rollout:  100%",
            "- Update priority: 2",
            "Changes were successfully applied to Google Play"
        );
    }

    @Test
    public void moveApkTrackWithPipeline_succeeds() throws Exception {
        String stepDefinition =
            "  androidApkMove googleCredentialsId: 'test-credentials',\n" +
            "    trackName: 'production',\n" +
            "    fromVersionCode: true,\n" +
            "    applicationId: 'org.jenkins.appId',\n" +
            "    versionCodes: '42',\n" +
            "    rolloutPercentage: '100'";

        moveApkTrackWithPipelineAndAssertSuccess(
            stepDefinition,
            "Updating release track 'production':",
            "- Staged rollout:  100%",
            "Changes were successfully applied to Google Play"
        );
    }

    @Test
    public void moveApkTrackWithPipeline_withRolloutPercent() throws Exception {
        // Given a step with a deprecated `rolloutPercent` value
        String stepDefinition =
            "androidApkMove googleCredentialsId: 'test-credentials',\n" +
            "  trackName: 'production',\n" +
            "  fromVersionCode: true,\n" +
            "  applicationId: 'org.jenkins.appId',\n" +
            "  versionCodes: '42',\n" +
            "  rolloutPercent: 12.34";

        // When a build occurs, it should roll out to that percentage
        moveApkTrackWithPipelineAndAssertSuccess(
            stepDefinition,
            "Updating release track 'production':",
            "- Staged rollout:  12.34%",
            "Changes were successfully applied to Google Play"
        );
    }

    @Test
    public void moveApkTrackWithPipeline_withBothRolloutFormats_usesRolloutPercentage() throws Exception {
        // Given a step with both the deprecated `rolloutPercent`, and a verbose `rolloutPercentage` value
        String stepDefinition =
            "androidApkMove googleCredentialsId: 'test-credentials',\n" +
            "  trackName: 'production',\n" +
            "  fromVersionCode: true,\n" +
            "  applicationId: 'org.jenkins.appId',\n" +
            "  versionCodes: '42',\n" +
            "  rolloutPercent: 12.3456,\n" +
            "  rolloutPercentage: '56.789%'";

        // When a build occurs, it should prefer the string `rolloutPercentage` value
        moveApkTrackWithPipelineAndAssertSuccess(
            stepDefinition,
            "Updating release track 'production':",
            "- Staged rollout:  56.789%",
            "Changes were successfully applied to Google Play"
        );
    }

    @Test
    public void movingApkWithPipelineAsDraftSucceeds() throws Exception {
        // Given a step with the rollout percentage set to zero
        String stepDefinition =
            "androidApkMove googleCredentialsId: 'test-credentials',\n" +
            "  trackName: 'production',\n" +
            "  fromVersionCode: true,\n" +
            "  applicationId: 'org.jenkins.appId',\n" +
            "  versionCodes: '42',\n" +
            "  rolloutPercentage: '0%'";

        // When a build occurs, it should upload as a draft
        moveApkTrackWithPipelineAndAssertSuccess(
            stepDefinition,
            "Updating release track 'production':",
            "- Staged rollout:  0% (draft)"
        );

        // And we should have set draft status when updating the track
        Track track = getRequestBodyForUrl(
            transport, "/org.jenkins.appId/edits/the-edit-id/tracks/production", Track.class
        );
        TrackRelease release = track.getReleases().get(0);
        assertEquals("draft", release.getStatus());
        assertNull(release.getUserFraction());
    }

    @Test
    public void movingApkWithPipelineToCustomTrackSucceeds() throws Exception {
        // Given a step that wants to move to a custom release track
        String stepDefinition =
            "androidApkMove googleCredentialsId: 'test-credentials',\n" +
            "  trackName: 'DogFood',\n" +
            "  fromVersionCode: true,\n" +
            "  applicationId: 'org.jenkins.appId',\n" +
            "  versionCodes: '42',\n" +
            "  rolloutPercentage: '5%'";

        // And the backend will recognise the custom track
        setUpTransportForSuccess("dogfood");

        // When a build occurs
        // Then the APK should be successfully assigned to the custom track
        moveApkTrackWithPipelineAndAssertSuccess(
            stepDefinition,
            "Updating release track 'dogfood':",
            "- Staged rollout:  5%"
        );
    }

    @Test
    public void movingApkWithPipelineToNonExistentCustomTrackFails() throws Exception {
        // Given a step that wants to move to a custom release track
        // But the track does not exist on the backend
        String stepDefinition =
            "androidApkMove googleCredentialsId: 'test-credentials',\n" +
            "  trackName: 'non-existent-track',\n" +
            "  fromVersionCode: true,\n" +
            "  applicationId: 'org.jenkins.appId',\n" +
            "  versionCodes: '42',\n" +
            "  rolloutPercentage: '5%'";

        // And the backend does not know about the custom track
        setUpTransportForSuccess();

        // When a build occurs
        // Then it should fail with a message about the missing track
        moveApkTrackWithPipelineAndAssertFailure(
            stepDefinition,
            "Release track 'non-existent-track' could not be found"
        );
    }

    private void moveApkTrackWithPipelineAndAssertFailure(
        String stepDefinition, String... expectedLogLines
    ) throws Exception {
        moveApkTrackWithPipelineAndAssertResult(stepDefinition, Result.FAILURE, expectedLogLines);
    }

    private void moveApkTrackWithPipelineAndAssertSuccess(
        String stepDefinition, String... expectedLogLines
    ) throws Exception {
        String[] commonLogLines = {
            "Changes were successfully applied to Google Play"
        };
        String[] allExpectedLogLines = Stream.concat(Arrays.stream(commonLogLines), Arrays.stream(expectedLogLines))
                .toArray(String[]::new);
        moveApkTrackWithPipelineAndAssertResult(stepDefinition, Result.SUCCESS, allExpectedLogLines);
    }

    private void moveApkTrackWithPipelineAndAssertResult(
        String stepDefinition, Result expectedResult, String... expectedLogLines
    ) throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("" +
            "node {\n" +
            "  " + stepDefinition + "\n" +
            "}", true
        ));

        setUpCredentials("test-credentials");
        if (transport.responses.isEmpty()) {
            setUpTransportForSuccess();
        }

        assertResultWithLogLines(j, p, expectedResult, expectedLogLines);
    }

    @Test
    @Ignore("Test does not work on a remote slave")
    public void moveApkTrack_fromSlave_succeeds() throws Exception {
        transport
                .withResponse("/edits",
                        new FakePostEditsResponse().setEditId("the-edit-id"))
                .withResponse("/edits/the-edit-id/apks",
                        new FakeListApksResponse().setApks(42))
                .withResponse("/edits/the-edit-id/bundles",
                        new FakeListBundlesResponse().setEmptyBundles())
                .withResponse("/edits/the-edit-id/tracks/production",
                        new FakeAssignTrackResponse().success("production", 42))
                .withResponse("/edits/the-edit-id:commit",
                        new FakeCommitResponse().success())
        ;

        DumbSlave agent = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject("moveReleaseTrack");
        p.setAssignedNode(agent);

        ReleaseTrackAssignmentBuilder builder = createBuilder();
        p.getBuildersList().add(builder);
        QueueTaskFuture<FreeStyleBuild> scheduled = p.scheduleBuild2(0);
        j.assertBuildStatusSuccess(scheduled);

        assertLogLines(j, scheduled,
            "Updating release track 'production':",
            "- Staged rollout:  5%"
        );
    }

    private void setUpTransportForSuccess() {
        setUpTransportForSuccess("production");
    }

    private void setUpTransportForSuccess(String trackName) {
        setUpTransportForSuccess(trackName, true);
    }

    private void setUpTransportForSuccess(String trackName, boolean includeTrackInList) {
        transport
            .withResponse("/edits",
                    new FakePostEditsResponse().setEditId("the-edit-id"))
            .withResponse("/edits/the-edit-id/apks",
                    new FakeListApksResponse().setApks(42))
            .withResponse("/edits/the-edit-id/bundles",
                    new FakeListBundlesResponse().setEmptyBundles())
            .withResponse("/edits/the-edit-id/tracks",
                    new FakeListTracksResponse().setTracks(
                        new ArrayList<Track>() {{
                            add(track("production"));
                            add(track("beta", release(42, "en-GB", "de-DE")));
                            add(track("alpha"));
                            add(track("internal"));
                            if (includeTrackInList) {
                                add(track(trackName));
                            }
                        }}
                    ))
            .withResponse("/edits/the-edit-id/tracks/" + trackName,
                    new FakeAssignTrackResponse().success(trackName, 42))
            .withResponse("/edits/the-edit-id:commit",
                    new FakeCommitResponse().success())
        ;
    }

    private ReleaseTrackAssignmentBuilder createBuilder() throws Exception {
        ReleaseTrackAssignmentBuilder builder = new ReleaseTrackAssignmentBuilder();
        setUpCredentials("test-credentials");
        builder.setGoogleCredentialsId("test-credentials");
        builder.setApplicationId("org.jenkins.appId");
        builder.setVersionCodes("42");
        builder.setRolloutPercentage("5%");
        builder.setTrackName("production");
        return builder;
    }
}
