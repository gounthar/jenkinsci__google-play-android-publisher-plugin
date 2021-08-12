package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.api.client.http.FileContent;
import com.google.api.services.androidpublisher.model.InternalAppSharingArtifact;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.AppFileFormat;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.UploadFile;

import java.io.File;
import java.io.IOException;

import static hudson.Functions.humanReadableByteSize;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Util.getRelativeFileName;

public class InternalAppSharingUploadTask extends AbstractPublisherTask<Boolean> {

    private final String applicationId;
    private final FilePath workspace;
    private final UploadFile appFile;

    public InternalAppSharingUploadTask(
        TaskListener listener, GoogleRobotCredentials credentials,
        String applicationId, FilePath workspace, UploadFile appFile
    ) {
        super(listener, credentials);
        this.applicationId = applicationId;
        this.workspace = workspace;
        this.appFile = appFile;
    }

    @Override
    protected Boolean execute() throws IOException, InterruptedException, UploadException {
        logger.printf("Uploading file to internal app sharing on Google Play...%n" +
                "- Credential:     %s%n" +
                "- Application ID: %s%n%n", getCredentialName(), applicationId);

        // Log some useful information about the file that will be uploaded
        final AppFileFormat fileFormat = appFile.getFileFormat();
        final String fileType = (fileFormat == AppFileFormat.BUNDLE) ? "AAB" : "APK";
        logger.printf("        %s file: %s%n", fileType, getRelativeFileName(workspace, appFile.getFilePath()));
        logger.printf("       File size: %s%n", humanReadableByteSize(appFile.getFilePath().length()));
        logger.printf("      SHA-1 hash: %s%n", appFile.getSha1Hash());
        logger.printf("     versionCode: %d%n", appFile.getVersionCode());
        logger.printf("     versionName: %s%n", appFile.getVersionName());
        logger.printf("   minSdkVersion: %s%n", appFile.getMinSdkVersion());
        logger.printf(" %n");

        // Upload the file
        File fileToUpload = new File(appFile.getFilePath().getRemote());
        FileContent fileContent = new FileContent("application/octet-stream", fileToUpload);
        InternalAppSharingArtifact artifact;
        if (fileFormat == AppFileFormat.APK) {
            artifact = getInternalAppSharing().uploadapk(applicationId, fileContent).execute();
        } else {
            artifact = getInternalAppSharing().uploadbundle(applicationId, fileContent).execute();
        }

        // Output URL
        logger.println("Internal app sharing file was successfully uploaded to Google Play:");
        logger.println(artifact.getDownloadUrl());

        // TODO: Store action

        return true;
    }
}
