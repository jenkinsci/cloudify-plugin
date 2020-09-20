package co.cloudify.jenkins.plugin.integrations;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

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
    private String executable;
    private String pluginsDirectory;
    private String storageDirectory;
    private String templateUrl;
    private String variablesAsString;
    private Map<String, Object> variables;
    private String environmentVariablesAsString;
    private String variablesFile;
    private Map<String, String> environmentVariables;
    private String environmentVariablesFile;

    @DataBoundConstructor
    public TerraformBuildStep() {
        super();
    }

    public String getExecutable() {
        return executable;
    }

    @DataBoundSetter
    public void setExecutable(String executable) {
        this.executable = executable;
    }

    public String getPluginsDirectory() {
        return pluginsDirectory;
    }

    @DataBoundSetter
    public void setPluginsDirectory(String pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory;
    }

    public String getStorageDirectory() {
        return storageDirectory;
    }

    @DataBoundSetter
    public void setStorageDirectory(String storageDirectory) {
        this.storageDirectory = storageDirectory;
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

    public String getVariablesFile() {
        return variablesFile;
    }

    @DataBoundSetter
    public void setVariablesFile(String variablesFile) {
        this.variablesFile = variablesFile;
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

    public String getEnvironmentVariablesFile() {
        return environmentVariablesFile;
    }

    @DataBoundSetter
    public void setEnvironmentVariablesFile(String environmentVariablesFile) {
        this.environmentVariablesFile = environmentVariablesFile;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        String executable = CloudifyPluginUtilities.expandString(envVars, this.executable);
        String pluginsDirectory = CloudifyPluginUtilities.expandString(envVars, this.pluginsDirectory);
        String storageDirectory = CloudifyPluginUtilities.expandString(envVars, this.storageDirectory);
        String templateUrl = CloudifyPluginUtilities.expandString(envVars, this.templateUrl);
        String variablesAsString = CloudifyPluginUtilities.expandString(envVars, this.variablesAsString);
        String variablesFile = CloudifyPluginUtilities.expandString(envVars, this.variablesFile);
        String environmentVariablesAsString = CloudifyPluginUtilities.expandString(envVars,
                this.environmentVariablesAsString);
        String environmentVariablesFile = CloudifyPluginUtilities.expandString(envVars, this.environmentVariablesFile);

        Map<String, Object> variablesMap = CloudifyPluginUtilities.getCombinedMap(workspace, variablesFile,
                variablesAsString,
                this.variables);
        Map<String, String> envVariablesMap = CloudifyPluginUtilities
                .getCombinedMap(workspace, environmentVariablesFile, environmentVariablesAsString,
                        this.environmentVariables)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> (entry.getValue() != null ? entry.getValue().toString() : null)));

        putIfNonNullValue(operationInputs, "terraform_executable", executable);
        putIfNonNullValue(operationInputs, "terraform_plugins_dir", pluginsDirectory);
        putIfNonNullValue(operationInputs, "terraform_storage_dir", storageDirectory);
        operationInputs.put("module_source", templateUrl);
        operationInputs.put("variables", variablesMap);
        operationInputs.put("environment_variables", envVariablesMap);
        super.performImpl(run, launcher, listener, workspace, envVars, cloudifyClient);
    }

    @Override
    protected String getIntegrationName() {
        return "terraform";
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
                .append("executable", executable)
                .append("pluginsDirectory", pluginsDirectory)
                .append("storageDirectory", storageDirectory)
                .append("templateUrl", templateUrl)
                .append("variablesAsString", variablesAsString)
                .append("variablesFile", variablesFile)
                .append("variables", variables)
                .append("environmentVariablesAsString", environmentVariablesAsString)
                .append("environmentVariables", environmentVariables)
                .append("environmentVariablesFile", environmentVariablesFile)
                .toString();
    }
}
