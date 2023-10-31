package org.jenkinsci.plugins.googleplayandroidpublisher;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Item;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static hudson.Util.fixEmptyAndTrim;
import static hudson.Util.tryParseNumber;
import static hudson.model.Item.EXTENDED_READ;
import static org.jenkinsci.plugins.googleplayandroidpublisher.Util.REGEX_VARIABLE;

public abstract class GooglePlayBuildStepDescriptor<T extends BuildStep & Describable<T>>
        extends BuildStepDescriptor<T> {

    public GooglePlayBuildStepDescriptor() {
        load();
    }

    public ListBoxModel doFillGoogleCredentialsIdItems(@AncestorInPath Item item) {
        // Only allow enumerating credentials if we have the appropriate permission
        if (item == null || !item.hasPermission(EXTENDED_READ)) {
            return new ListBoxModel();
        }

        ListBoxModel credentials = getCredentialsListBox(item);
        if (credentials.isEmpty()) {
            credentials.add("(No Google Play account credentials have been added to Jenkins)", null);
        }
        return credentials;
    }

    public FormValidation doCheckGoogleCredentialsId(@AncestorInPath Item item, @QueryParameter String value) {
        // Only allow validating the existence of credentials if we have the appropriate permission
        if (item == null || !item.hasPermission(EXTENDED_READ)) {
            return FormValidation.ok();
        }

        // Complain if no credentials have been set up
        ListBoxModel credentials = getCredentialsListBox(item);
        if (credentials.isEmpty()) {
            return FormValidation.error("You must add at least one Google Service Account via the Credentials page");
        }

        // Don't validate value if it hasn't been set, or looks like an expression
        if (value == null || value.isEmpty() || value.matches("\\$\\{[A-Za-z0-9_]+}")) {
            return FormValidation.ok();
        }

        // Otherwise, attempt to load the given credential to see whether it has been set up correctly
        try {
            new CredentialsHandler(value).getServiceAccountCredentials(item);
        } catch (EphemeralCredentialsException e) {
            // Loading the credential (apparently) goes online, so we may get ephemeral connectivity problems
            return FormValidation.warning(e.getMessage());
        } catch (UploadException e) {
            return FormValidation.error(e.getMessage());
        }

        // Everything is fine
        return FormValidation.ok();
    }

    @NonNull
    private static ListBoxModel getCredentialsListBox(Item item) {
        ListBoxModel listBox = new ListBoxModel();
        CredentialsHandler.getCredentials(item).forEach(credential -> {
            String name = CredentialsNameProvider.name(credential);
            listBox.add(name, credential.getId());
        });
        return listBox;
    }

    public ComboBoxModel doFillTrackNameItems() {
        // Auto-complete the default track names, though users can also enter custom track names
        List<String> trackNames = new ArrayList<>(Arrays.asList("internal", "alpha", "beta", "production"));
        if (clazz.isAssignableFrom(ApkPublisher.class)) {
            trackNames.add("internal-app-sharing");
        }
        return new ComboBoxModel(trackNames.toArray(new String[0]));
    }

    public FormValidation doCheckTrackName(@QueryParameter String value) {
        if (fixEmptyAndTrim(value) == null) {
            return FormValidation.error("A release track is required");
        }
        return FormValidation.ok();
    }

    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public FormValidation doCheckRolloutPercentage(@QueryParameter String value) {
        value = fixEmptyAndTrim(value);
        if (value == null) {
            return FormValidation.error("A rollout percentage is required");
        }
        if (value.matches(REGEX_VARIABLE)) {
            return FormValidation.ok();
        }

        double pct = tryParseNumber(value.replace("%", "").trim(), Double.NaN).doubleValue();
        if (Double.isNaN(pct) || Double.compare(pct, 0) < 0 || Double.compare(pct, 100) > 0) {
            return FormValidation.error("Percentage value must be between 0 and 100%");
        }
        return FormValidation.ok();
    }

    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public FormValidation doCheckInAppUpdatePriority(@QueryParameter String value) {
        value = fixEmptyAndTrim(value);
        if (value == null || value.matches(REGEX_VARIABLE)) {
            return FormValidation.ok();
        }

        int priority = tryParseNumber(value.trim(), -1).intValue();
        if (priority < 0 || priority > 5) {
            return FormValidation.error("Priority value must be between 0 and 5");
        }
        return FormValidation.ok();
    }

    public boolean isApplicable(Class<? extends AbstractProject> c) {
        return true;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        save();
        return super.configure(req, formData);
    }

}
