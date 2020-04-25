package co.cloudify.jenkins.plugin.integrations;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private Secret secretAccessKey;
    private String regionName;
    private String stackName;
    private String parameters;
    private String parametersFile;
    private String templateUrl;

    private transient BlueprintUploadSpec uploadSpec;

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

    public Secret getSecretAccessKey() {
        return secretAccessKey;
    }

    @DataBoundSetter
    public void setSecretAccessKey(Secret secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
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
        PrintStream logger = listener.getLogger();

        String accessKeyId = expandString(envVars, this.accessKeyId.getPlainText());
        String secretAccessKey = expandString(envVars, this.secretAccessKey.getPlainText());
        String regionName = expandString(envVars, this.regionName);
        String stackName = expandString(envVars, this.stackName);
        String parameters = expandString(envVars, this.parameters);
        String parametersFile = expandString(envVars, this.parametersFile);
        String templateUrl = expandString(envVars, this.templateUrl);

        Map<String, Object> parametersMap = new LinkedHashMap<>();
        parametersMap.putAll(CloudifyPluginUtilities.readYamlOrJson(parameters));

        if (parametersFile != null) {
            FilePath parametersFilePath = workspace.child(parametersFile);
            if (parametersFilePath.exists()) {
                logger.println(String.format("Reading template parameters from %s", parametersFilePath));
                parametersMap.putAll(CloudifyPluginUtilities.readYamlOrJson(parametersFilePath));
            } else {
                logger.println(String.format("Parameters file (%s) doesn't exist; skipping", parametersFilePath));
            }
        }

        // As of AWS plugin 2.3.2, we need to convert the parameters to a list.
        // There's probably a more elegant way to do this without using commons-collections,
        // but instead use Java 8 streams.
        List parametersAsList = parametersMap.entrySet().stream()
                .map(entry -> MapUtils.putAll(new HashMap<>(), new Object[][] {
                        { "ParameterKey", entry.getKey() },
                        { "ParameterValue", entry.getValue() }
                })).collect(Collectors.toList());

        inputs = new LinkedHashMap<>();
        putIfNonNullValue(inputs, "aws_access_key_id", accessKeyId);
        putIfNonNullValue(inputs, "aws_secret_access_key", secretAccessKey);
        putIfNonNullValue(inputs, "aws_region_name", regionName);
        inputs.put("stack_name", stackName);
        inputs.put("parameters", parametersAsList);
        inputs.put("template_url", templateUrl);

        File blueprintPath = prepareBlueprintDirectory("/blueprints/cfn/blueprint.yaml");

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
        return "cloud-formation";
    }

    @Override
    protected BlueprintUploadSpec getBlueprintUploadSpec() {
        return uploadSpec;
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
                .append("parameters", parameters)
                .append("parametersFile", parametersFile)
                .toString();
    }
}
