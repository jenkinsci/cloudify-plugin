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
import hudson.AbortException;
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
    private String parametersFile;
    private String templatePathUrl;
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

    public String getParametersFile() {
        return parametersFile;
    }

    @DataBoundSetter
    public void setParametersFile(String parametersFile) {
        this.parametersFile = parametersFile;
    }

    public String getTemplatePathUrl() {
        return templatePathUrl;
    }

    @DataBoundSetter
    public void setTemplatePathUrl(String templatePathUrl) {
        this.templatePathUrl = templatePathUrl;
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

        String subscriptionId = expandString(envVars, this.subscriptionId);
        String tenantId = expandString(envVars, this.tenantId);
        String clientId = expandString(envVars, this.clientId);
        String clientSecret = expandString(envVars, this.clientSecret.getPlainText());
        String clientSecretParameter = expandString(envVars, this.clientSecretParameter);
        String location = expandString(envVars, this.location);
        String resourceGroupName = expandString(envVars, this.resourceGroupName);
        String parameters = expandString(envVars, this.parameters);
        String parametersFile = expandString(envVars, this.parametersFile);
        String templatePathUrl = expandString(envVars, this.templatePathUrl);
        String templateFile = expandString(envVars, this.templateFile);

        // TODO: Move this validation to the descriptor, once we know how to
        // only invoke validation during job execution and not in the config.

        if (!(templatePathUrl != null ^ templateFile != null)) {
            throw new AbortException(String.format(
                    "Both template path/URL (%s) and file (%s) were specified; please specify exactly one",
                    templatePathUrl, templateFile));
        }
        Map<String, Object> variablesMap = new LinkedHashMap<>();
        variablesMap.putAll(CloudifyPluginUtilities.readYamlOrJson(parameters));

        if (parametersFile != null) {
            FilePath parametersFilePath = workspace.child(parametersFile);
            if (parametersFilePath.exists()) {
                logger.println(String.format("Reading template parameters from %s", parametersFilePath));
                variablesMap.putAll(CloudifyPluginUtilities.readYamlOrJson(parametersFilePath));
            } else {
                logger.println(String.format("Parameters file (%s) doesn't exist; skipping", parametersFilePath));
            }
        }

        String effectiveClientSecret;
        if (clientSecretParameter != null) {
            effectiveClientSecret = envVars.expand(String.format("${%s}", clientSecretParameter));
        } else {
            effectiveClientSecret = clientSecret;
        }

        inputs = new LinkedHashMap<>();

        putIfNonNullValue(inputs, "azure_subscription_id", subscriptionId);
        putIfNonNullValue(inputs, "azure_tenant_id", tenantId);
        putIfNonNullValue(inputs, "azure_client_id", clientId);
        putIfNonNullValue(inputs, "azure_client_secret", effectiveClientSecret);
        putIfNonNullValue(inputs, "location", location);
        putIfNonNullValue(inputs, "resource_group_name", resourceGroupName);
        putIfNonNullValue(inputs, "parameters", parameters);

        if (templateFile != null) {
            // Read the template as a JSON.
            FilePath templateFilePath = workspace.child(templateFile);
            logger.println(String.format("Reading template from %s", templateFilePath));
            Map<String, Object> templateContents = CloudifyPluginUtilities.readYamlOrJson(templateFilePath);
            inputs.put("template", templateContents);
        } else {
            inputs.put("template_file", templatePathUrl);
        }

        File blueprintPath = prepareBlueprintDirectory("/blueprints/arm/blueprint.yaml");

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

        // TODO: comment out because we only need this validation on parameterized builds,
        // not in the config screen.
//        public FormValidation checkTemplate(final String templatePathUrl, final String templateFile) {
//            if (!(StringUtils.isBlank(templateFile) ^ StringUtils.isBlank(templatePathUrl))) {
//                return FormValidation.error("Either template path/URL or file must be provided, not both");
//            }
//            return FormValidation.ok();
//        }
//
//        public FormValidation doCheckTemplateFile(final @QueryParameter String value,
//                final @QueryParameter String templatePathUrl) {
//            return checkTemplate(templatePathUrl, value);
//        }
//
//        public FormValidation doCheckTemplatePathUrl(final @QueryParameter String value,
//                final @QueryParameter String templateFile) {
//            return checkTemplate(value, templateFile);
//        }
//

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
                .append("parametersFile", parametersFile)
                .append("templateFile", templateFile)
                .toString();
    }
}
