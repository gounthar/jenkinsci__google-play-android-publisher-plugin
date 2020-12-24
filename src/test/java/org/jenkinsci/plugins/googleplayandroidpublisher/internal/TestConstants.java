package org.jenkinsci.plugins.googleplayandroidpublisher.internal;

import com.google.api.services.androidpublisher.model.Apk;
import com.google.api.services.androidpublisher.model.ApkBinary;
import com.google.api.services.androidpublisher.model.Bundle;

public class TestConstants {

    // The SHA-1 of an empty file
    private static String DEFAULT_FILE_SHA1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709";

    static String DEFAULT_APK_APP_ID = "org.jenkins.appId";
    static String DEFAULT_APK_MIN_SDK_VERSION = "16";
    static int DEFAULT_APK_VERSION_CODE = 42;
    static final String DEFAULT_APK_VERSION_NAME = "1.42";
    public static final Apk DEFAULT_APK = new Apk()
            .setVersionCode(DEFAULT_APK_VERSION_CODE)
            .setBinary(new ApkBinary().setSha1(DEFAULT_FILE_SHA1));

    static String DEFAULT_BUNDLE_APP_ID = "org.jenkins.bundleAppId";
    static String DEFAULT_BUNDLE_MIN_SDK_VERSION = "29";
    static int DEFAULT_BUNDLE_VERSION_CODE = 43;
    static final String DEFAULT_BUNDLE_VERSION_NAME = "1.43";
    public static final Bundle DEFAULT_BUNDLE = new Bundle()
            .setVersionCode(DEFAULT_BUNDLE_VERSION_CODE)
            .setSha1(DEFAULT_FILE_SHA1);
}
