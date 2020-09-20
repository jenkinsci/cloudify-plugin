package co.cloudify.jenkins.plugin.integrations;

import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.microsoft.azure.util.AzureCredentials;

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

/**
 * A build step for applying an Azure ARM template.
 * 
 * @author Isaac Shabtay
 */
public class ARMBuildStep extends IntegrationBuildStep {
    private String azureCredentialsId;
    private String location;
    private String resourceGroupName;
    private Map<String, Object> parameters;
    private String parametersAsString;
    private String parametersFile;
    private String templateFile;

    @DataBoundConstructor
    public ARMBuildStep() {
        super();
    }

    public String getAzureCredentialsId() {
        return azureCredentialsId;
    }

    @DataBoundSetter
    public void setAzureCredentialsId(String azureCredentialsId) {
        this.azureCredentialsId = azureCredentialsId;
    }

    public String getLocation() {
        return location;
    }

    @DataBoundSetter
    public void setLocation(String location) {
        this.location = location;
    }

    public String getResourceGroupName() {
        return resourceGroupName;
    }

    @DataBoundSetter
    public void setResourceGroupName(String resourceGroupName) {
        this.resourceGroupName = resourceGroupName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    @DataBoundSetter
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getParametersAsString() {
        return parametersAsString;
    }

    @DataBoundSetter
    public void setParametersAsString(String parametersAsString) {
        this.parametersAsString = parametersAsString;
    }

    public String getParametersFile() {
        return parametersFile;
    }

    @DataBoundSetter
    public void setParametersFile(String parametersFile) {
        this.parametersFile = parametersFile;
    }

    public String getTemplateFile() {
        return templateFile;
    }

    @DataBoundSetter
    public void setTemplateFile(String templateFile) {
        this.templateFile = templateFile;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        String azureCredentialsId = CloudifyPluginUtilities.expandString(envVars, this.azureCredentialsId);
        String location = CloudifyPluginUtilities.expandString(envVars, this.location);
        String resourceGroupName = CloudifyPluginUtilities.expandString(envVars, this.resourceGroupName);
        String parametersAsString = CloudifyPluginUtilities.expandString(envVars, this.parametersAsString);
        String parametersFile = CloudifyPluginUtilities.expandString(envVars, this.parametersFile);
        String templateFile = CloudifyPluginUtilities.expandString(envVars, this.templateFile);

        Map<String, Object> variablesMap = CloudifyPluginUtilities.getCombinedMap(workspace, parametersFile,
                parametersAsString,
                this.parameters);

        AzureCredentials azureCreds = CloudifyPluginUtilities.getCredentials(azureCredentialsId, AzureCredentials.class,
                run);

        putIfNonNullValue(operationInputs, "azure_subscription_id", azureCreds.getSubscriptionId());
        putIfNonNullValue(operationInputs, "azure_tenant_id", azureCreds.getTenant());
        putIfNonNullValue(operationInputs, "azure_client_id", azureCreds.getClientId());
        putIfNonNullValue(operationInputs, "azure_client_secret", azureCreds.getPlainClientSecret());
        putIfNonNullValue(operationInputs, "location", location);
        putIfNonNullValue(operationInputs, "resource_group_name", resourceGroupName);
        operationInputs.put("parameters", variablesMap);
        operationInputs.put("template_file", templateFile);
        super.performImpl(run, launcher, listener, workspace, envVars, cloudifyClient);
    }

    @Override
    protected String getIntegrationName() {
        return "arm";
    }

    @Symbol("cfyAzureArm")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.ARMBuildStep_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("azureCredentialsId", azureCredentialsId)
                .append("location", location)
                .append("resourceGroupName", resourceGroupName)
                .append("parameters", parameters)
                .append("parametersAsString", parametersAsString)
                .append("parametersFile", parametersFile)
                .append("templateFile", templateFile)
                .toString();
    }
}
