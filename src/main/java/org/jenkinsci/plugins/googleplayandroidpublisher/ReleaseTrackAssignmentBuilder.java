package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import net.dongliu.apk.parser.exception.ParserException;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.UploadFile;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static hudson.Util.fixEmptyAndTrim;
import static hudson.Util.tryParseNumber;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Util.REGEX_VARIABLE;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Util.getPublisherErrorMessage;

public class ReleaseTrackAssignmentBuilder extends GooglePlayBuilder {

    private Boolean fromVersionCode;
    private String applicationId;
    private String versionCodes;
    private String filesPattern;
    private String trackName;
    private String releaseName;
    private String rolloutPercentage;
    private String inAppUpdatePriority;

    // This field was used before AAB support was introduced; it will be migrated to `filesPattern` for Freestyle jobs
    @Deprecated private transient String apkFilesPattern;
    // This can still be used in Pipeline jobs as a convenience (i.e. defining rollout as a number instead of a string);
    // but the `rolloutPercentage` field will take precedence, if defined
    @Deprecated private transient Double rolloutPercent;

    @DataBoundConstructor
    public ReleaseTrackAssignmentBuilder() {
        // No parameters here are mandatory, though the credentials in the parent class are
    }

    @SuppressWarnings("unused")
    protected Object readResolve() {
        // Migrate Freestyle jobs from old `apkFilesPattern` field to `filesPattern`
        if (apkFilesPattern != null) {
            setFilesPattern(apkFilesPattern);
            apkFilesPattern = null;
        }

        // Migrate Freestyle jobs back from `rolloutPercent` number to `rolloutPercentage` string
        if (rolloutPercent != null) {
            // Call the old setter, which updates `rolloutPercentage` if necessary
            setRolloutPercent(rolloutPercent);
            rolloutPercent = null;
        }

        return this;
    }

    @DataBoundSetter
    public void setFromVersionCode(Boolean fromVersionCode) {
        this.fromVersionCode = fromVersionCode;
    }

    public Boolean getFromVersionCode() {
        return fromVersionCode;
    }

    public boolean isFromVersionCode() {
        return fromVersionCode == null || fromVersionCode;
    }

    @DataBoundSetter
    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    @DataBoundSetter
    public void setVersionCodes(String versionCodes) {
        this.versionCodes = versionCodes;
    }

    public String getVersionCodes() {
        return versionCodes;
    }

    // Required for Pipeline builds still using `apkFilesPattern`
    @Deprecated
    @DataBoundSetter
    public void setApkFilesPattern(String value) {
        setFilesPattern(value);
    }

    // Required for the Snippet Generator, since the field has a @DataBoundSetter
    @Deprecated
    public String getApkFilesPattern() {
        return getFilesPattern();
    }

    @DataBoundSetter
    public void setFilesPattern(@Nonnull String pattern) {
        this.filesPattern = DescriptorImpl.defaultFilesPattern.equals(pattern) ? null : pattern;
    }

    @Nonnull
    public String getFilesPattern() {
        return fixEmptyAndTrim(filesPattern) == null ? DescriptorImpl.defaultFilesPattern : filesPattern;
    }

    @DataBoundSetter
    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public void setRolloutPercentage(@Nonnull String percentage) {
        // If the value is an expression, just store it directly
        if (percentage.matches(REGEX_VARIABLE)) {
            this.rolloutPercentage = percentage;
            return;
        }

        // Otherwise try and parse it as a number
        String pctStr = percentage.replace("%", "").trim();
        Number pct = tryParseNumber(pctStr, Double.NaN);

        // If it can't be parsed, save it, and we'll show an error at build time
        if (Double.isNaN(pct.doubleValue())) {
            this.rolloutPercentage = percentage;
            return;
        }
        this.rolloutPercentage = pctStr;
    }

    @Nullable
    public String getRolloutPercentage() {
        return fixEmptyAndTrim(rolloutPercentage);
    }

    // Required for Pipeline builds using the deprecated `rolloutPercent` option
    @Deprecated
    @DataBoundSetter
    public void setRolloutPercent(Double percent) {
        // If a job somehow has both `rolloutPercent` and `rolloutPercentage` defined,
        // let the latter, non-deprecated field take precedence, i.e. don't overwrite it
        if (rolloutPercentage != null || percent == null) {
            return;
        }
        setRolloutPercentage(percent.toString());
    }

    // Since the `rolloutPercent` field has a @DataBoundSetter, this method needs to exist in order
    // for the Snippet Generator to work for this publisher, even although this method is otherwise unused
    @Deprecated
    public Double getRolloutPercent() {
        return null;
    }

    @DataBoundSetter
    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    @Nullable
    public String getTrackName() {
        return fixEmptyAndTrim(trackName);
    }

    @DataBoundSetter
    public void setReleaseName(String releaseName) {
        this.releaseName = releaseName;
    }

    @Nullable
    public String getReleaseName() {
        return releaseName;
    }

    @DataBoundSetter
    public void setInAppUpdatePriority(@Nullable String priorityStr) {
        this.inAppUpdatePriority = priorityStr;
    }

    @Nullable
    public String getInAppUpdatePriority() {
        return fixEmptyAndTrim(inAppUpdatePriority);
    }

    private String getExpandedApplicationId() throws IOException, InterruptedException {
        return expand(getApplicationId());
    }

    private String getExpandedVersionCodes() throws IOException, InterruptedException {
        return expand(getVersionCodes());
    }

    private String getExpandedFilesPattern() throws IOException, InterruptedException {
        return expand(getFilesPattern());
    }

    private String getCanonicalTrackName() throws IOException, InterruptedException {
        return expand(getTrackName());
    }

    private String getExpandedReleaseName() throws IOException, InterruptedException {
        return expand(getReleaseName());
    }

    @Nullable
    private String getExpandedRolloutPercentageString() throws IOException, InterruptedException {
        return expand(getRolloutPercentage());
    }

    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private double getExpandedRolloutPercentage() throws IOException, InterruptedException {
        final String pctStr = getExpandedRolloutPercentageString();
        if (pctStr == null) {
            return Double.NaN;
        }
        return tryParseNumber(pctStr.replace("%", "").trim(), Double.NaN).doubleValue();
    }

    private String getExpandedInAppUpdatePriorityString() throws IOException, InterruptedException {
        return expand(getInAppUpdatePriority());
    }

    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private Integer getExpandedInAppUpdatePriority() throws IOException, InterruptedException {
        String prioStr = getExpandedInAppUpdatePriorityString();
        int priority = tryParseNumber(prioStr, Integer.MIN_VALUE).intValue();
        if (priority == Integer.MIN_VALUE) {
            return null;
        }
        return priority;
    }

    private boolean isConfigValid(PrintStream logger) throws IOException, InterruptedException {
        final List<String> errors = new ArrayList<>();

        // Check whether the relevant values were provided, based on the method chosen
        if (isFromVersionCode()) {
            if (getExpandedApplicationId() == null) {
                errors.add("No application ID was specified");
            }
            if (getExpandedVersionCodes() == null) {
                errors.add("No version codes were specified");
            }
        } else if (getExpandedFilesPattern() == null) {
            errors.add("Path or pattern to AAB/APK file(s) was not specified");
        }

        // Track name is also required
        final String trackName = getCanonicalTrackName();
        if (trackName == null) {
            errors.add("Release track was not specified; this is now a mandatory parameter");
        }

        // Check for valid rollout percentage
        final String pctStr = getExpandedRolloutPercentageString();
        if (pctStr == null) {
            errors.add("Rollout percentage was not specified; this is now a mandatory parameter");
        } else {
            double pct = getExpandedRolloutPercentage();
            if (Double.isNaN(pct) || Double.compare(pct, 0) < 0 || Double.compare(pct, 100) > 0) {
                errors.add(String.format("'%s' is not a valid rollout percentage", pctStr));
            }
        }

        // Check whether in-app priority could be parsed to a number
        if (getExpandedInAppUpdatePriorityString() != null && getExpandedInAppUpdatePriority() == null) {
            errors.add(String.format("'%s' is not a valid update priority", getExpandedInAppUpdatePriorityString()));
        }

        // Print accumulated errors
        if (!errors.isEmpty()) {
            logger.println("Cannot make changes to Google Play:");
            for (String error : errors) {
                logger.print("- ");
                logger.println(error);
            }
        }

        return errors.isEmpty();
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {
        super.perform(run, workspace, launcher, listener);

        // Calling assignAppFiles logs the reason when a failure occurs, so in that case we just need to throw here
        if (!assignAppFiles(run, workspace, listener)) {
            throw new AbortException("Assignment failed");
        }
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private boolean assignAppFiles(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull TaskListener listener)
            throws IOException, InterruptedException {
        final PrintStream logger = listener.getLogger();

        // Check that the job has been configured correctly
        if (!isConfigValid(logger)) {
            return false;
        }

        // Figure out the list of version codes to assign
        String applicationId;
        Collection<Long> versionCodeList = new TreeSet<>();
        if (isFromVersionCode()) {
            applicationId = getExpandedApplicationId();
            String codes = getExpandedVersionCodes();
            for (String s : codes.split("[,\\s]+")) {
                long versionCode = tryParseNumber(s.trim(), -1).longValue();
                if (versionCode != -1) {
                    versionCodeList.add(versionCode);
                }
            }
        } else {
            AppInfo info = getApplicationInfoForAppFiles(workspace, logger, getExpandedFilesPattern());
            if (info == null) {
                return false;
            }
            applicationId = info.applicationId;
            versionCodeList.addAll(info.versionCodes);
        }

        // Assign the APKs to the desired track
        try {
            GoogleRobotCredentials credentials = getCredentialsHandler().getServiceAccountCredentials(run.getParent());
            return workspace.act(new TrackAssignmentTask(listener, credentials, applicationId, versionCodeList,
                            getCanonicalTrackName(), getExpandedReleaseName(), getExpandedRolloutPercentage(),
                            getExpandedInAppUpdatePriority()));
        } catch (UploadException e) {
            logger.println(String.format("Assignment failed: %s", getPublisherErrorMessage(e)));
            logger.println("No changes have been applied to the Google Play account");
        }
        return false;
    }

    private AppInfo getApplicationInfoForAppFiles(FilePath workspace, PrintStream logger, String appFilesPattern)
            throws IOException, InterruptedException {
        // Find the filename(s) which match the pattern after variable expansion
        List<String> relativePaths = workspace.act(new FindFilesTask(appFilesPattern));
        if (relativePaths.isEmpty()) {
            logger.println(String.format("No AAB/APK files matching the pattern '%s' could be found", appFilesPattern));
            return null;
        }

        // Read the metadata from each file found
        final List<UploadFile> appFilesToMove = new ArrayList<>();
        for (String path : relativePaths) {
            FilePath file = workspace.child(path);
            try {
                // Attempt to parse the file as an Android app
                UploadFile appFile = new UploadFile(file);
                appFilesToMove.add(appFile);
                logger.println(String.format("Found %s file with version code %d: %s",
                        appFile.getFileFormat(), appFile.getVersionCode(), path));
            } catch (ParserException | IOException e) {
                throw new IOException(String.format("File does not appear to be valid: %s", file.getRemote()), e);
            }
        }

        // If there are multiple matches, ensure that all have the same application ID
        final Set<String> applicationIds = appFilesToMove.stream()
                .map(UploadFile::getApplicationId).collect(Collectors.toSet());
        if (applicationIds.size() != 1) {
            logger.println(String.format("Multiple files matched the pattern '%s', " +
                    "but they have inconsistent application IDs:", appFilesPattern));
            for (String id : applicationIds) {
                logger.print("- ");
                logger.println(id);
            }
            return null;
        }

        final Set<Long> versionCodes = appFilesToMove.stream()
                .map(UploadFile::getVersionCode).collect(Collectors.toSet());
        return new AppInfo(applicationIds.iterator().next(), new ArrayList<>(versionCodes));
    }

    private static final class AppInfo {
        final String applicationId;
        final List<Long> versionCodes;

        AppInfo(String applicationId, List<Long> versionCodes) {
            this.applicationId = applicationId;
            this.versionCodes = versionCodes;
        }
    }

    @Symbol("androidApkMove")
    @Extension
    public static final class DescriptorImpl extends GooglePlayBuildStepDescriptor<Builder> {
        public static final String defaultFilesPattern = "**/build/outputs/**/*.aab, **/build/outputs/**/*.apk";

        public String getDisplayName() {
            return "Move Android apps to a different release track";
        }

    }

}
