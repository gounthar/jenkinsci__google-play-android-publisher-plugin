package org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses;

import com.google.api.services.androidpublisher.model.InternalAppSharingArtifact;

public class FakeInternalAppSharingArtifactResponse extends FakeHttpResponse<FakeInternalAppSharingArtifactResponse> {
    public FakeInternalAppSharingArtifactResponse success() {
        InternalAppSharingArtifact artifact = new InternalAppSharingArtifact()
                .setDownloadUrl("https://play.google.com/test/download.apk")
                .setCertificateFingerprint("apk-fingerprint")
                .setSha256("apk-sha256");
        return setSuccessData(artifact);
    }
}
