package co.cloudify.jenkins.plugin.integrations;

import java.io.File;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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
import hudson.util.Secret;

/**
 * A build step for applying an Azure ARM template.
 * 
 * @author Isaac Shabtay
 */
public class ARMBuildStep extends IntegrationBuildStep {
    private String subscriptionId;
    private String tenantId;
    private String clientId;
    private Secret clientSecret;
    private String clientSecretParameter;
    private String location;
    private String resourceGroupName;
    private String parameters;
    private String templateFile;

    private transient BlueprintUploadSpec uploadSpec;

    @DataBoundConstructor
    public ARMBuildStep() {
        super();
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    @DataBoundSetter
    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    @DataBoundSetter
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getClientId() {
        return clientId;
    }

    @DataBoundSetter
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Secret getClientSecret() {
        return clientSecret;
    }

    @DataBoundSetter
    public void setClientSecret(Secret clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientSecretParameter() {
        return clientSecretParameter;
    }

    @DataBoundSetter
    public void setClientSecretParameter(String clientSecretParameter) {
        this.clientSecretParameter = clientSecretParameter;
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

    public String getParameters() {
        return parameters;
    }

    @DataBoundSetter
    public void setParameters(String parameters) {
        this.parameters = parameters;
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
        PrintStream logger = listener.getLogger();

        String subscriptionId = CloudifyPluginUtilities.expandString(envVars, this.subscriptionId);
        String tenantId = CloudifyPluginUtilities.expandString(envVars, this.tenantId);
        String clientId = CloudifyPluginUtilities.expandString(envVars, this.clientId);
        String clientSecret = CloudifyPluginUtilities.expandString(envVars, this.clientSecret);
        String clientSecretParameter = CloudifyPluginUtilities.expandString(envVars, this.clientSecretParameter);
        String location = CloudifyPluginUtilities.expandString(envVars, this.location);
        String resourceGroupName = CloudifyPluginUtilities.expandString(envVars, this.resourceGroupName);
        String parameters = CloudifyPluginUtilities.expandString(envVars, this.parameters);
        String templateFile = CloudifyPluginUtilities.expandString(envVars, this.templateFile);

        Map<String, Object> variablesMap = new LinkedHashMap<>();
        variablesMap.putAll(CloudifyPluginUtilities.readYamlOrJson(parameters));

        String effectiveClientSecret = CloudifyPluginUtilities.getValueWithProxy(envVars, clientSecretParameter,
                clientSecret);

        putIfNonNullValue(operationInputs, "azure_subscription_id", subscriptionId);
        putIfNonNullValue(operationInputs, "azure_tenant_id", tenantId);
        putIfNonNullValue(operationInputs, "azure_client_id", clientId);
        putIfNonNullValue(operationInputs, "azure_client_secret", effectiveClientSecret);
        putIfNonNullValue(operationInputs, "location", location);
        putIfNonNullValue(operationInputs, "resource_group_name", resourceGroupName);
        operationInputs.put("parameters", variablesMap);
        operationInputs.put("template_file", templateFile);

        File blueprintPath = prepareBlueprintDirectory("/blueprints/arm/blueprint.yaml");

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
        return "azure-arm";
    }

    @Override
    protected BlueprintUploadSpec getBlueprintUploadSpec() {
        return uploadSpec;
    }

    @Symbol("cfyAzureArm")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation checkClientSecret(final Secret clientSecret, final String clientSecretParameter) {
            if (!(StringUtils.isBlank(clientSecret.getPlainText()) ^ StringUtils.isBlank(clientSecretParameter))) {
                return FormValidation
                        .error("Either a Client Secret or a Client Secret Parameter must be specified, not both");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckClientSecret(final @QueryParameter Secret value,
                final @QueryParameter String clientSecretParameter) {
            return checkClientSecret(value, clientSecretParameter);
        }

        public FormValidation doCheckClientSecretParameter(final @QueryParameter String value,
                final @QueryParameter Secret clientSecret) {
            return checkClientSecret(clientSecret, value);
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
                .append("subscriptionId", subscriptionId)
                .append("tenantId", tenantId)
                .append("clientId", clientId)
                .append("clientSecretParameter", clientSecretParameter)
                // Skip Client Secret
                .append("location", location)
                .append("resourceGroupName", resourceGroupName)
                .append("parameters", parameters)
                .append("templateFile", templateFile)
                .toString();
    }
}
