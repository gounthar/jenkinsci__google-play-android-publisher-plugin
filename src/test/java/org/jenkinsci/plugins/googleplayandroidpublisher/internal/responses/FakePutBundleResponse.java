package org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.services.androidpublisher.model.Bundle;

/**
 * This is the second half of the uploadBundles request. The request is initiated automatically from
 * {@link MediaHttpUploader} by handling the {@code Location} redirect of that request.
 *
 * @see com.google.api.services.androidpublisher.AndroidPublisher.Edits.Bundles#upload Bundles.upload() - Original request method
 * @see Bundle Bundle - Response type
 * @see FakeUploadResponse FakeUploadResponse (first half of this request)
 */
public class FakePutBundleResponse extends FakeHttpResponse<FakePutBundleResponse> {
    public FakePutBundleResponse success(int versionCode, String sha1) {
        Bundle bundle = new Bundle()
                .setVersionCode(versionCode)
                .setSha1(sha1);

        return setSuccessData(bundle);
    }
}
