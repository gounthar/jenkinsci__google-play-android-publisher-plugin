package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Thrown when a call to the Google Play API throws an exception. */
public class PublisherApiException extends UploadException {

    private List<String> errorMessages;

    public PublisherApiException(IOException cause) {
        super(cause);
        // We need to extract the meaningful part of the exception here because GJRE isn't Serializable,
        // and so cannot be passed back from the build slave to Jenkins master without losing information
        List<String> errors = new ArrayList<>();
        if (cause instanceof GoogleJsonResponseException) {
            GoogleJsonError details = ((GoogleJsonResponseException) cause).getDetails();
            if (details == null) {
                // The details don't seem to be populated in the case of 401 errors:
                // https://code.google.com/p/google-api-java-client/issues/detail?id=898
                if (((GoogleJsonResponseException) cause).getStatusCode() == 401) {
                    errors.add("The API credentials provided do not have permission to apply these changes");
                }
            } else {
                if (details.getErrors() != null) {
                    for (GoogleJsonError.ErrorInfo error : details.getErrors()) {
                        errors.add(error.getMessage());
                    }
                }
            }
            this.errorMessages = Collections.unmodifiableList(errors);
        }
    }

    @Nullable
    public List<String> getErrorMessages() {
        return errorMessages;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessages.isEmpty() ? null : errorMessages.get(0);
    }

}
