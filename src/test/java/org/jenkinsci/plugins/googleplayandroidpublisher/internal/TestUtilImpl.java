package org.jenkinsci.plugins.googleplayandroidpublisher.internal;

import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import java.io.File;

import org.mockito.stubbing.Answer;

import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestConstants.DEFAULT_APK_APP_ID;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestConstants.DEFAULT_APK_MIN_SDK_VERSION;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestConstants.DEFAULT_APK_VERSION_CODE;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestConstants.DEFAULT_BUNDLE_APP_ID;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestConstants.DEFAULT_BUNDLE_MIN_SDK_VERSION;
import static org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestConstants.DEFAULT_BUNDLE_VERSION_CODE;
import static org.mockito.Mockito.mock;

public class TestUtilImpl implements JenkinsUtil, AndroidUtil {
    public static final boolean DEBUG = true;

    private String apkAppId = DEFAULT_APK_APP_ID;
    private String bundleAppId = DEFAULT_BUNDLE_APP_ID;

    @Override
    public String getPluginVersion() {
        return "0.0.0-TEST";
    }

    @Override
    public AndroidPublisher createPublisherClient(GoogleRobotCredentials credentials, String pluginVersion) {
        // Example:
        //     AndroidPublisher androidClient = TestsHelper.createAndroidPublisher(transport);
        //     when(jenkinsUtil.createPublisherClient(any(), anyString())).thenReturn(androidClient);
        return mock(AndroidPublisher.class, (Answer) invocationOnMock -> {
            throw new UnsupportedOperationException(
                    "This should be implemented using `TestHelper.createAndroidPublisher()` for now.");
        });
    }

    @Override
    public AppFileMetadata getAppFileMetadata(File file) {
        if (file.getName().endsWith(".aab")) {
            return new BundleFileMetadata(
                bundleAppId, DEFAULT_BUNDLE_VERSION_CODE, DEFAULT_BUNDLE_VERSION_NAME, DEFAULT_BUNDLE_MIN_SDK_VERSION
            );
        }
        return new ApkFileMetadata(
            apkAppId, DEFAULT_APK_VERSION_CODE, DEFAULT_APK_VERSION_NAME, DEFAULT_APK_MIN_SDK_VERSION
        );
    }

    public void setApkAppId(String apkAppId) {
        this.apkAppId = apkAppId;
    }

    public void setBundleAppId(String bundleAppId) {
        this.bundleAppId = bundleAppId;
    }

}
