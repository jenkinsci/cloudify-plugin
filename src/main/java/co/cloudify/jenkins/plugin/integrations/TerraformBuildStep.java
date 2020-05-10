package co.cloudify.jenkins.plugin.integrations;

import java.io.File;
import java.io.PrintStream;
import java.util.Map;
import java.util.stream.Collectors;

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
    private String variablesAsString;
    private Map<String, Object> variables;
    private String environmentVariablesAsString;
    private Map<String, String> environmentVariables;

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

    public String getVariablesAsString() {
        return variablesAsString;
    }

    @DataBoundSetter
    public void setVariablesAsString(String parameters) {
        this.variablesAsString = parameters;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    @DataBoundSetter
    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public String getEnvironmentVariablesAsString() {
        return environmentVariablesAsString;
    }

    @DataBoundSetter
    public void setEnvironmentVariablesAsString(String environmentVariablesAsString) {
        this.environmentVariablesAsString = environmentVariablesAsString;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    @DataBoundSetter
    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        PrintStream logger = listener.getLogger();

        String templateUrl = CloudifyPluginUtilities.expandString(envVars, this.templateUrl);
        String variablesAsString = CloudifyPluginUtilities.expandString(envVars, this.variablesAsString);
        String environmentVariablesAsString = CloudifyPluginUtilities.expandString(envVars, this.environmentVariablesAsString);

        Map<String, Object> variablesMap = CloudifyPluginUtilities.getMapFromMapOrString(variablesAsString,
                this.variables);
        Map<String, String> envVariablesMap = CloudifyPluginUtilities
                .getMapFromMapOrString(environmentVariablesAsString, this.environmentVariables)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> (entry.getValue() != null ? entry.getValue().toString() : null)));

        operationInputs.put("module_source", templateUrl);
        operationInputs.put("variables", variablesMap);
        operationInputs.put("environment_variables", envVariablesMap);

        File blueprintPath = prepareBlueprintDirectory("/blueprints/terraform/blueprint.yaml");

        try {
            uploadSpec = new BlueprintUploadSpec(blueprintPath);
            super.performImpl(run, launcher, listener, workspace, envVars, cloudifyClient);
        } finally {
            if (!blueprintPath.delete()) {
                logger.println("Failed deleting blueprint file");
            }
            if (!blueprintPath.getParentFile().delete()) {
                logger.println("Failed deleting temporary directory");
            }
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
                .append("variablesAsString", variablesAsString)
                .append("variables", variables)
                .append("environmentVariablesAsString", environmentVariablesAsString)
                .append("environmentVariables", environmentVariables)
                .toString();
    }
}
