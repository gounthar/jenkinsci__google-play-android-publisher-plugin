package org.jenkinsci.plugins.googleplayandroidpublisher.internal;

class BundleFileMetadata extends AppFileMetadata {
    BundleFileMetadata(String applicationId, long versionCode, String versionName, String minSdkVersion) {
        super(applicationId, versionCode, versionName, minSdkVersion);
    }
}
