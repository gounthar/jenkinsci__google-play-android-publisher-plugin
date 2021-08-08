package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.model.TaskListener;
import jenkins.security.MasterToSlaveCallable;

import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;

public abstract class AbstractPublisherTask<V> extends MasterToSlaveCallable<V, UploadException> {

    private final TaskListener listener;
    private final GoogleRobotCredentials credentials;
    private final String pluginVersion;
    protected AndroidPublisher.Edits editService;
    protected final String applicationId;
    protected String editId;
    protected PrintStream logger;

    AbstractPublisherTask(TaskListener listener, GoogleRobotCredentials credentials, String applicationId) {
        this.listener = listener;
        this.credentials = credentials;
        this.pluginVersion = Util.getPluginVersion();
        this.applicationId = applicationId;
    }

    public final V call() throws UploadException {
        editService = getEditService();
        logger = listener.getLogger();
        try {
            return execute();
        } catch (IOException e) {
            // All the remote API calls can throw IOException, so we catch and wrap them here for convenience
            throw new PublisherApiException(e);
        } catch (InterruptedException e) {
            // There's no special handling we want to do if the build is interrupted, so just wrap and rethrow
            throw new UploadException(e);
        } finally {
            logger.flush();
        }
    }

    protected abstract V execute() throws IOException, InterruptedException, UploadException;

    protected final AndroidPublisher.Edits getEditService() throws UploadException {
        try {
            return Util.getPublisherClient(credentials, pluginVersion).edits();
        } catch (GeneralSecurityException e) {
            throw new UploadException(e);
        }
    }

    /** Creates a new edit, assigning the {@link #editId}. Any previous edit ID will be lost. */
    protected final void createEdit(String applicationId) throws IOException {
        editId = editService.insert(applicationId, null).execute().getId();
    }

    protected void commit() throws IOException {
        logger.println("Applying changes to Google Play...");
        boolean cannotBeSentForReview = false;
        try {
            // Try committing and sending the changes for review
            editService.commit(applicationId, editId).setChangesNotSentForReview(false).execute();
        } catch (GoogleJsonResponseException e) {
            // Check whether the commit was rejected because it can't be automatically submitted for review
            GoogleJsonError details = e.getDetails();
            if (details != null) {
                String msg = details.getMessage();
                cannotBeSentForReview = msg != null && msg.contains("changesNotSentForReview");
            }

            if (cannotBeSentForReview) {
                // If so, we can retry without sending the changes for review
                editService.commit(applicationId, editId).setChangesNotSentForReview(true).execute();
            } else {
                // The commit failed for another reason, so just rethrow
                throw e;
            }
        }

        // If committing didn't throw an exception, everything worked fine
        logger.println("Changes were successfully applied to Google Play");
        if (cannotBeSentForReview) {
            logger.println("- However, it has indicated that these changes need to be manually submitted for review via the Google Play Console");
        }
    }

    /** @return The name of the credential being used. */
    protected String getCredentialName() {
        return credentials.getId();
    }

}
