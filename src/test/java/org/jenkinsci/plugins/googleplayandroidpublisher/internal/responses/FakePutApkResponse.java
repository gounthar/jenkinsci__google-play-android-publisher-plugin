package org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.services.androidpublisher.model.Apk;
import com.google.api.services.androidpublisher.model.ApkBinary;

/**
 * This is the second half of the uploadApk request. The request is initiated automatically from
 * {@link MediaHttpUploader} by handling the {@code Location} redirect of that request.
 *
 * @see com.google.api.services.androidpublisher.AndroidPublisher.Edits.Apks#upload Apks.upload() - Original request method
 * @see com.google.api.services.androidpublisher.model.Apk Apk - Response type
 * @see com.google.api.services.androidpublisher.model.ApkBinary ApkBinary - Response inner type
 * @see FakeUploadResponse FakeUploadResponse (first half of this request)
 */
public class FakePutApkResponse extends FakeHttpResponse<FakePutApkResponse> {
    public FakePutApkResponse success(int versionCode, String sha1) {
        Apk apk = new Apk()
                .setVersionCode(versionCode)
                .setBinary(new ApkBinary().setSha1(sha1));

        return setSuccessData(apk);
    }
}
