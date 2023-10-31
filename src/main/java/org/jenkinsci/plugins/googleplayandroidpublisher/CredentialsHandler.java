package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.credentials.domains.DomainRequirementProvider;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials.AccountIdNotSetException;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials.PrivateKeyNotSetException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.exception.ExceptionUtils;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import static hudson.Util.fixEmptyAndTrim;

public class CredentialsHandler {

    private final String googleCredentialsId;

    public CredentialsHandler(String googleCredentialsId) throws CredentialsException {
        String id = fixEmptyAndTrim(googleCredentialsId);
        if (id == null) {
            throw new CredentialsException("No credentials have been specified: You must add a Google Account via the "
                    + "Jenkins Credentials page, then configure this job to use those credentials");
        }
        this.googleCredentialsId = id;
    }

    /** @return The Google API credentials configured for this job. */
    @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
    public final GoogleRobotCredentials getServiceAccountCredentials(Item item) throws UploadException {
        try {
            GoogleOAuth2ScopeRequirement req = new AndroidPublisherScopeRequirement();
            GoogleRobotCredentials credentials = getById(googleCredentialsId, item);
            if (credentials == null) {
                throw new CredentialsException(String.format("The Google Service Account credential '%s' "
                        + "could not be found.%n\tIf you renamed the credential since configuring this job, you must "
                        + "re-configure this job, choosing the new credential name", googleCredentialsId));
            }
            return credentials.forRemote(req);
        } catch (AccountIdNotSetException | PrivateKeyNotSetException e) {
            throw new CredentialsException(String.format("The Google Service Account credential '%s' "
                    + "has not been configured correctly.%n\tUpdate the credential, ensuring that the required data "
                    + "have been entered, then try again", googleCredentialsId));
        } catch (IllegalStateException e) {
            if (ExceptionUtils.getRootCause(e) instanceof FileNotFoundException) {
                throw new UploadException("Failed to get Google service account info. Ensure that the JSON file and " +
                        "P12 private key for the '"+ googleCredentialsId +"' credential have both been uploaded.", e);
            }
            throw new UploadException(e);
        } catch (GeneralSecurityException e) {
            if (ExceptionUtils.getRootCause(e) instanceof IOException) {
                throw new EphemeralCredentialsException("Failed to validate Google Service Account credential " +
                        "against the Google API servers. Check internet connectivity on the Jenkins controller and " +
                        "try again.", e);
            }
            throw new UploadException(e);
        }
    }

    /** @return A list of Google Play-compatible credentials visible to the given item. */
    @NonNull
    static List<GoogleRobotCredentials> getCredentials(Item item) {
        GoogleOAuth2ScopeRequirement requirement = DomainRequirementProvider.of(
            GooglePlayPublisher.class, GoogleOAuth2ScopeRequirement.class
        );
        if (requirement == null) {
            return Collections.emptyList();
        }

        // As per the Credentials Plugin docs: https://github.com/jenkinsci/credentials-plugin/blob/2f9bf725/docs/consumer.adoc#listing-available-credentials-matching-some-specific-set-of-criteria
        Authentication auth = item instanceof Queue.Task ? Tasks.getAuthenticationOf((Queue.Task) item) : ACL.SYSTEM;
        return CredentialsProvider.lookupCredentials(
            GoogleRobotCredentials.class, item, auth, ImmutableList.of(requirement)
        );
    }

    /** @return The Google Play-compatible credentials matching the given ID, if any, visible to the given item. */
    @Nullable
    private static GoogleRobotCredentials getById(String id, Item item) {
        return getCredentials(item).stream()
            .filter(it -> it.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

}
