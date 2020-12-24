package org.jenkinsci.plugins.googleplayandroidpublisher.internal;

class ApkFileMetadata extends AppFileMetadata {
    ApkFileMetadata(String applicationId, long versionCode, String versionName, String minSdkVersion) {
        super(applicationId, versionCode, versionName, minSdkVersion);
    }
}
