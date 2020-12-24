package org.jenkinsci.plugins.googleplayandroidpublisher.internal;

import java.io.Serializable;

public abstract class AppFileMetadata implements Serializable {

    private final String applicationId;
    private final long versionCode;
    private final String versionName;
    private final String minSdkVersion;

    AppFileMetadata(String applicationId, long versionCode, String versionName, String minSdkVersion) {
        this.applicationId = applicationId;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.minSdkVersion = minSdkVersion;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public long getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getMinSdkVersion() {
        return minSdkVersion;
    }

}
