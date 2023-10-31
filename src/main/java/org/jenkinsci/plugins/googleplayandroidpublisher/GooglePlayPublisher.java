package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;

@RequiresDomain(value = AndroidPublisherScopeRequirement.class)
public abstract class GooglePlayPublisher extends Recorder implements SimpleBuildStep {

    protected static transient final ThreadLocal<Run<?, ?>> currentBuild = new ThreadLocal<>();
    protected static transient final ThreadLocal<TaskListener> currentListener = new ThreadLocal<>();

    private transient CredentialsHandler credentialsHandler;

    private String googleCredentialsId;

    @DataBoundSetter
    public void setGoogleCredentialsId(String googleCredentialsId) {
        this.googleCredentialsId = googleCredentialsId;
    }

    public final String getGoogleCredentialsId() {
        return googleCredentialsId;
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull Launcher launcher,
                        @NonNull TaskListener listener) throws InterruptedException, IOException {
        currentBuild.set(run);
        currentListener.set(listener);
    }

    protected CredentialsHandler getCredentialsHandler() throws CredentialsException, IOException,
            InterruptedException {
        if (credentialsHandler == null) {
            String id = expand(googleCredentialsId);
            credentialsHandler = new CredentialsHandler(id);
        }
        return credentialsHandler;
    }

    /** @return An expanded value, using the build and environment variables, plus token macro expansion. */
    @Nullable
    protected String expand(String value) throws IOException, InterruptedException {
        return Util.expand(currentBuild.get(), currentListener.get(), value);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

}
