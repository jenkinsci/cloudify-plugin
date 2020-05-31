package co.cloudify.jenkins.plugin.integrations;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.MapUtils;
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
 * A build step for applying a CloudFormation stack.
 * 
 * @author Isaac Shabtay
 */
public class CloudFormationBuildStep extends IntegrationBuildStep {
    private Secret accessKeyId;
    private String accessKeyIdAsString;
    private Secret secretAccessKey;
    private String secretAccessKeyAsString;
    private String regionName;
    private String stackName;
    private Map<String, Object> parameters;
    private String parametersAsString;
    private String parametersFile;
    private String templateUrl;

    @DataBoundConstructor
    public CloudFormationBuildStep() {
        super();
    }

    public Secret getAccessKeyId() {
        return accessKeyId;
    }

    @DataBoundSetter
    public void setAccessKeyId(Secret accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeyIdAsString() {
        return accessKeyIdAsString;
    }

    @DataBoundSetter
    public void setAccessKeyIdAsString(String accessKeyIdAsString) {
        this.accessKeyIdAsString = accessKeyIdAsString;
    }

    public Secret getSecretAccessKey() {
        return secretAccessKey;
    }

    @DataBoundSetter
    public void setSecretAccessKey(Secret secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getSecretAccessKeyAsString() {
        return secretAccessKeyAsString;
    }

    @DataBoundSetter
    public void setSecretAccessKeyAsString(String secretAccessKeyAsString) {
        this.secretAccessKeyAsString = secretAccessKeyAsString;
    }

    public String getRegionName() {
        return regionName;
    }

    @DataBoundSetter
    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getStackName() {
        return stackName;
    }

    @DataBoundSetter
    public void setStackName(String stackName) {
        this.stackName = stackName;
    }

    public String getParametersAsString() {
        return parametersAsString;
    }

    @DataBoundSetter
    public void setParametersAsString(String parameters) {
        this.parametersAsString = parameters;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    @DataBoundSetter
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getParametersFile() {
        return parametersFile;
    }

    @DataBoundSetter
    public void setParametersFile(String parametersFile) {
        this.parametersFile = parametersFile;
    }

    public String getTemplateUrl() {
        return templateUrl;
    }

    @DataBoundSetter
    public void setTemplateUrl(String templateUrl) {
        this.templateUrl = templateUrl;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        String accessKeyIdAsString = CloudifyPluginUtilities.expandString(envVars, this.accessKeyIdAsString);
        String secretAccessKeyAsString = CloudifyPluginUtilities.expandString(envVars, this.secretAccessKeyAsString);
        String regionName = CloudifyPluginUtilities.expandString(envVars, this.regionName);
        String stackName = CloudifyPluginUtilities.expandString(envVars, this.stackName);
        String parametersAsString = CloudifyPluginUtilities.expandString(envVars, this.parametersAsString);
        String parametersFile = CloudifyPluginUtilities.expandString(envVars, this.parametersFile);
        String templateUrl = CloudifyPluginUtilities.expandString(envVars, this.templateUrl);

        String effectiveAccessKeyId = CloudifyPluginUtilities.getPassword(this.accessKeyId, accessKeyIdAsString);
        String effectiveSecretAccessKey = CloudifyPluginUtilities.getPassword(this.secretAccessKey,
                secretAccessKeyAsString);

        Map<String, Object> parametersMap = CloudifyPluginUtilities.getCombinedMap(workspace, parametersFile,
                parametersAsString,
                this.parameters);

        // As of AWS plugin 2.3.2, we need to convert the parameters to a list.
        // There's probably a more elegant way to do this without using commons-collections,
        // but instead use Java 8 streams.
        List parametersAsList = parametersMap.entrySet().stream()
                .map(entry -> MapUtils.putAll(new HashMap<>(), new Object[][] {
                        { "ParameterKey", entry.getKey() },
                        { "ParameterValue", entry.getValue() }
                })).collect(Collectors.toList());

        putIfNonNullValue(operationInputs, "aws_access_key_id", effectiveAccessKeyId);
        putIfNonNullValue(operationInputs, "aws_secret_access_key", effectiveSecretAccessKey);
        putIfNonNullValue(operationInputs, "aws_region_name", regionName);
        operationInputs.put("stack_name", stackName);
        operationInputs.put("parameters", parametersAsList);
        operationInputs.put("template_url", templateUrl);
        super.performImpl(run, launcher, listener, workspace, envVars, cloudifyClient);
    }

    @Override
    protected String getIntegrationName() {
        return "cloudformation";
    }

    @Override
    protected String getIntegrationVersion() {
        return "1.0";
    }

    @Override
    protected BlueprintUploadSpec getBlueprintUploadSpec() throws IOException {
        return new BlueprintUploadSpec("/blueprints/cfn/blueprint.yaml");
    }

    @Symbol("cfyCloudFormation")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheckTemplateUrl(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckStackName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        @Override
        public String getDisplayName() {
            return Messages.CloudFormationBuildStep_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                // Omit confidential info (access key, etc)
                .append("regionName", regionName)
                .append("stackName", stackName)
                .append("templateUrl", templateUrl)
                .append("parametersAsString", parametersAsString)
                .append("parameters", parameters)
                .append("parametersFile", parametersFile)
                .toString();
    }
}
