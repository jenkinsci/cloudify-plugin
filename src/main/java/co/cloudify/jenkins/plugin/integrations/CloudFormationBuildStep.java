package co.cloudify.jenkins.plugin.integrations;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.auth.AWSCredentials;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

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

/**
 * A build step for applying a CloudFormation stack.
 * 
 * @author Isaac Shabtay
 */
public class CloudFormationBuildStep extends IntegrationBuildStep {
    private String awsCredentialsId;
    private String regionName;
    private String stackName;
    private Map<String, Object> parameters;
    private String parametersAsString;
    private String parametersFile;
    private String templateUrl;
    private String templateFile;
    private String templateBucketName;
    private String templateResourceName;
    private String templateBody;

    @DataBoundConstructor
    public CloudFormationBuildStep() {
        super();
    }

    public String getAwsCredentialsId() {
        return awsCredentialsId;
    }

    @DataBoundSetter
    public void setAwsCredentialsId(String awsCredentialsId) {
        this.awsCredentialsId = awsCredentialsId;
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

    public String getTemplateFile() {
        return templateFile;
    }

    @DataBoundSetter
    public void setTemplateFile(String templateFile) {
        this.templateFile = templateFile;
    }

    public String getTemplateBucketName() {
        return templateBucketName;
    }

    @DataBoundSetter
    public void setTemplateBucketName(String templateBucketName) {
        this.templateBucketName = templateBucketName;
    }

    public String getTemplateResourceName() {
        return templateResourceName;
    }

    @DataBoundSetter
    public void setTemplateResourceName(String templateResourceName) {
        this.templateResourceName = templateResourceName;
    }

    public String getTemplateBody() {
        return templateBody;
    }

    @DataBoundSetter
    public void setTemplateBody(String templateBody) {
        this.templateBody = templateBody;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        String awsCredentialsId = CloudifyPluginUtilities.expandString(envVars, this.awsCredentialsId);
        String regionName = CloudifyPluginUtilities.expandString(envVars, this.regionName);
        String stackName = CloudifyPluginUtilities.expandString(envVars, this.stackName);
        String parametersAsString = CloudifyPluginUtilities.expandString(envVars, this.parametersAsString);
        String parametersFile = CloudifyPluginUtilities.expandString(envVars, this.parametersFile);
        String templateUrl = CloudifyPluginUtilities.expandString(envVars, this.templateUrl);
        String templateFile = CloudifyPluginUtilities.expandString(envVars, this.templateFile);
        String templateBucketName = CloudifyPluginUtilities.expandString(envVars, this.templateBucketName);
        String templateResourceName = CloudifyPluginUtilities.expandString(envVars, this.templateResourceName);
        String templateBody = CloudifyPluginUtilities.expandString(envVars, this.templateBody);

        if (Arrays.asList(
                templateUrl != null,
                templateFile != null,
                templateBucketName != null && templateResourceName != null,
                templateBody != null).stream().filter(p -> p).count() != 1) {
            throw new AbortException(
                    String.format(
                            "Template must be specified in exactly one of the following ways: by URL, by file name, "
                                    + "by template body, or by a combination of bucket name and resource name. Provided values: "
                                    + "url=%s, file=%s, bucket name=%s, resource name=%s, body=%s",
                            templateUrl, templateFile, templateBucketName, templateResourceName, templateBody));
        }

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

        AmazonWebServicesCredentials awsCredentials = CloudifyPluginUtilities.getCredentials(awsCredentialsId,
                AmazonWebServicesCredentials.class, run);
        AWSCredentials awsCreds = awsCredentials.getCredentials();

        Map<String, Object> resourceConfigKwargs = new HashMap<String, Object>();
        Map<String, Object> resourceConfig = Collections.singletonMap("kwargs", resourceConfigKwargs);

        putIfNonNullValue(operationInputs, "aws_access_key_id", awsCreds.getAWSAccessKeyId());
        putIfNonNullValue(operationInputs, "aws_secret_access_key", awsCreds.getAWSSecretKey());
        putIfNonNullValue(operationInputs, "aws_region_name", regionName);
        putIfNonNullValue(operationInputs, "resource_config", resourceConfig);
        putIfNonNullValue(resourceConfigKwargs, "StackName", stackName);
        putIfNonNullValue(resourceConfigKwargs, "Parameters", parametersAsList);

        if (templateUrl != null) {
            resourceConfigKwargs.put("TemplateURL", templateUrl);
        } else if (templateBucketName != null && templateResourceName != null) {
            resourceConfigKwargs.put("TemplateURL", String.format(
                    "https://%s.s3.amazonaws.com/%s", templateBucketName, templateResourceName));
        } else {
            String finalBody;
            if (templateFile != null) {
                try (InputStream is = workspace.child(templateFile).read()) {
                    finalBody = IOUtils.toString(is, StandardCharsets.UTF_8);
                }
            } else if (templateBody != null) {
                finalBody = templateBody;
            } else {
                throw new AbortException("Could not conclude CloudFormation template body");
            }
            resourceConfigKwargs.put("TemplateBody", finalBody);
        }
        super.performImpl(run, launcher, listener, workspace, envVars, cloudifyClient);
    }

    @Override
    protected String getIntegrationName() {
        return "cloudformation";
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
                .append("awsCredentialsId", awsCredentialsId)
                .append("regionName", regionName)
                .append("stackName", stackName)
                .append("templateUrl", templateUrl)
                .append("templateFile", templateFile)
                .append("templateBucketName", templateBucketName)
                .append("templateResourceName", templateResourceName)
                .append("templateBody", templateBody)
                .append("parametersAsString", parametersAsString)
                .append("parameters", parameters)
                .append("parametersFile", parametersFile)
                .toString();
    }
}
