package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.LocalizedText;
import com.google.api.services.androidpublisher.model.TrackRelease;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.AndroidUtil;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.AppFileMetadata;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.JenkinsUtil;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.UtilsImpl;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static hudson.Util.fixEmptyAndTrim;

public class Util {
    private static JenkinsUtil sJenkins = UtilsImpl.getInstance();
    private static AndroidUtil sAndroid = UtilsImpl.getInstance();

    /** Regex for the BCP 47 language codes used by Google Play. */
    static final String REGEX_LANGUAGE = "[a-z]{2,3}([-_][0-9A-Z]{2,})?";

    // From hudson.Util.VARIABLE
    static final String REGEX_VARIABLE = "\\$([A-Za-z0-9_]+|\\{[A-Za-z0-9_]+\\}|\\$)";

    /** A (potentially non-exhaustive) list of languages supported by Google Play for app description text etc.. */
    // See here for the list: https://support.google.com/googleplay/android-developer/answer/9844778
    static final String[] SUPPORTED_LANGUAGES = {
        "af", "am", "ar", "az-AZ", "be", "bg", "bn-BD", "ca", "cs-CZ", "da-DK", "de-DE", "el-GR", "en-AU", "en-CA",
        "en-GB", "en-IN", "en-SG", "en-US", "en-ZA", "es-419", "es-ES", "es-US", "et", "eu-ES", "fa", "fa-AE", "fa-AF",
        "fa-IR", "fi-FI", "fil", "fr-CA", "fr-FR", "gl-ES", "gu", "hi-IN", "hr", "hu-HU", "hy-AM", "id", "is-IS",
        "it-IT", "iw-IL", "ja-JP", "ka-GE", "kk", "km-KH", "kn-IN", "ko-KR", "ky-KG", "lo-LA", "lt", "lv", "mk-MK",
        "ml-IN", "mn-MN", "mr-IN", "ms", "ms-MY", "my-MM", "ne-NP", "nl-NL", "no-NO", "pa", "pl-PL", "pt-BR", "pt-PT",
        "rm", "ro", "ru-RU", "si-LK", "sk", "sl", "sq", "sr", "sv-SE", "sw", "ta-IN", "te-IN", "th", "tr-TR", "uk",
        "ur", "vi", "zh-CN", "zh-HK", "zh-TW", "zu"
    };

    /** @return The version of this Jenkins plugin, e.g. "1.0" or "1.1-SNAPSHOT" (for dev releases). */
    public static String getPluginVersion() {
        return sJenkins.getPluginVersion();
    }

    public static final class GetAppFileMetadataTask extends MasterToSlaveFileCallable<AppFileMetadata> {
        @Override
        public AppFileMetadata invoke(File file, VirtualChannel virtualChannel) throws IOException {
            return sAndroid.getAppFileMetadata(file);
        }
    }

    /** @return The given value with variables expanded and trimmed; {@code null} if that results in an empty string. */
    @Nullable
    static String expand(Run<?, ?> run, TaskListener listener, String value)
            throws InterruptedException, IOException {
        // If this is a pipeline run, there's no need to expand tokens
        if (!(run instanceof AbstractBuild)) {
            return value;
        }

        try {
            final AbstractBuild build = (AbstractBuild) run;
            return fixEmptyAndTrim(TokenMacro.expandAll(build, listener, value));
        } catch (MacroEvaluationException e) {
            listener.getLogger().println(e.getMessage());
            return value;
        }
    }

    /** @return A user-friendly(ish) Google Play API error message, if one could be found in the given exception. */
    static String getPublisherErrorMessage(UploadException e) {
        if (e instanceof CredentialsException) {
            return e.getMessage();
        }
        if (e instanceof PublisherApiException) {
            // TODO: Here we could map error reasons like "apkUpgradeVersionConflict" to better (and localised) text
            List<String> errors = ((PublisherApiException) e).getErrorMessages();
            if (errors == null || errors.isEmpty()) {
                return "Unknown error: " + e.getCause();
            }
            StringBuilder message = new StringBuilder("\n");
            for (String error : errors) {
                message.append("- ");
                message.append(error);
                message.append('\n');
            }
            return message.toString();
        }

        // Otherwise print the whole stacktrace, as it's something unrelated to this plugin
        return Throwables.getStackTraceAsString(e);
    }

    /**
     * @return An Android Publisher client, using the configured credentials.
     * @throws GeneralSecurityException If reading the service account credentials failed.
     */
    static AndroidPublisher getPublisherClient(GoogleRobotCredentials credentials, String pluginVersion)
            throws GeneralSecurityException {
        return sJenkins.createPublisherClient(credentials, pluginVersion);
    }

    @Nullable
    static List<LocalizedText> transformReleaseNotes(@Nullable ApkPublisher.RecentChanges[] list) {
        if (list != null) {
            return Arrays.stream(list).map(it -> {
                if (it == null) return null;
                return new LocalizedText()
                        .setLanguage(it.language)
                        .setText(it.text);
            }).collect(Collectors.toList());
        }
        return null;
    }

    static TrackRelease buildRelease(
        List<Long> versionCodes, String releaseName, double userFraction, Integer inAppUpdatePriority, @Nullable List<LocalizedText> releaseNotes
    ) {
        final String status;
        final Double fraction;

        boolean isDraftRelease = Double.compare(userFraction, 0) == 0;
        boolean isFullRollout = Double.compare(userFraction, 1) == 0;

        if (isDraftRelease) {
            status = "draft";
            fraction = null;
        } else if (isFullRollout) {
            status = "completed";
            fraction = null;
        } else {
            status = "inProgress";
            fraction = userFraction;
        }

        TrackRelease release = new TrackRelease()
                .setVersionCodes(versionCodes)
                .setName(releaseName)
                .setUserFraction(fraction)
                .setInAppUpdatePriority(inAppUpdatePriority)
                .setStatus(status);

        if (releaseNotes != null) release.setReleaseNotes(releaseNotes);
        return release;
    }

    /** @return The path to the given file, relative to the build workspace. */
    static String getRelativeFileName(FilePath workspace, FilePath file) {
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

    @VisibleForTesting
    static void setJenkinsUtil(JenkinsUtil util) {
        sJenkins = util;
    }

    @VisibleForTesting
    static void setAndroidUtil(AndroidUtil util) {
        sAndroid = util;
    }
}
