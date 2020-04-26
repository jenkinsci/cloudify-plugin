package co.cloudify.jenkins.plugin.integrations;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.jenkins.plugin.BlueprintUploadSpec;
import co.cloudify.jenkins.plugin.CloudifyPluginUtilities;
import co.cloudify.jenkins.plugin.Messages;
import co.cloudify.rest.client.CloudifyClient;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

/**
 * A build step for applying a Terraform template.
 * 
 * @author Isaac Shabtay
 */
public class TerraformBuildStep extends IntegrationBuildStep {
    private String templateUrl;
    private String variables;

    private transient BlueprintUploadSpec uploadSpec;

    @DataBoundConstructor
    public TerraformBuildStep() {
        super();
    }

    public String getTemplateUrl() {
        return templateUrl;
    }

    @DataBoundSetter
    public void setTemplateUrl(String templateUrl) {
        this.templateUrl = templateUrl;
    }

    public String getVariables() {
        return variables;
    }

    @DataBoundSetter
    public void setVariables(String parameters) {
        this.variables = parameters;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        String templateUrl = expandString(envVars, this.templateUrl);
        String variables = expandString(envVars, this.variables);

        Map<String, Object> variablesMap = new LinkedHashMap<>();
        variablesMap.putAll(CloudifyPluginUtilities.readYamlOrJson(variables));

        inputs = new LinkedHashMap<>();
        inputs.put("module_source", templateUrl);
        inputs.put("variables", variablesMap);

        File blueprintPath = prepareBlueprintDirectory("/blueprints/terraform/blueprint.yaml");

        try {
            uploadSpec = new BlueprintUploadSpec(blueprintPath);
            super.performImpl(run, launcher, listener, workspace, envVars, cloudifyClient);
        } finally {
            blueprintPath.delete();
            blueprintPath.getParentFile().delete();
        }
    }

    @Override
    protected String getIntegrationName() {
        return "terraform";
    }

    @Override
    protected BlueprintUploadSpec getBlueprintUploadSpec() {
        return uploadSpec;
    }

    @Symbol("cfyTerraform")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheckTemplateUrl(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        @Override
        public String getDisplayName() {
            return Messages.TerraformBuildStep_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("templateUrl", templateUrl)
                .append("variables", variables)
                .toString();
    }
}
