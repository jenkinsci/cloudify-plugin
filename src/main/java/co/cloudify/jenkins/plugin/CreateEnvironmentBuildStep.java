package co.cloudify.jenkins.plugin;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.jenkins.plugin.actions.EnvironmentBuildAction;
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
 * A Build Step for creating an environment.
 * 
 * @author Isaac Shabtay
 */
public class CreateEnvironmentBuildStep extends CloudifyBuildStep {
    private String blueprintId;
    private String deploymentId;
    private String inputs;
    private String inputsFile;
    private String mapping;
    private String mappingFile;
    private String outputFile;
    private boolean skipInstall;
    private boolean echoInputs;
    private boolean echoOutputs;
    private boolean debugOutput;

    @DataBoundConstructor
    public CreateEnvironmentBuildStep() {
        super();
    }

    public String getBlueprintId() {
        return blueprintId;
    }

    @DataBoundSetter
    public void setBlueprintId(String blueprintId) {
        this.blueprintId = blueprintId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    @DataBoundSetter
    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getInputs() {
        return inputs;
    }

    @DataBoundSetter
    public void setInputs(String inputs) {
        this.inputs = inputs;
    }

    public String getInputsFile() {
        return inputsFile;
    }

    @DataBoundSetter
    public void setInputsFile(String inputsFile) {
        this.inputsFile = inputsFile;
    }

    public String getMapping() {
        return mapping;
    }

    @DataBoundSetter
    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    public String getMappingFile() {
        return mappingFile;
    }

    @DataBoundSetter
    public void setMappingFile(String mappingFile) {
        this.mappingFile = mappingFile;
    }

    public String getOutputFile() {
        return outputFile;
    }

    @DataBoundSetter
    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public boolean isSkipInstall() {
        return skipInstall;
    }

    @DataBoundSetter
    public void setSkipInstall(boolean skipInstall) {
        this.skipInstall = skipInstall;
    }

    public boolean isEchoInputs() {
        return echoInputs;
    }

    @DataBoundSetter
    public void setEchoInputs(boolean echoInputs) {
        this.echoInputs = echoInputs;
    }

    public boolean isEchoOutputs() {
        return echoOutputs;
    }

    @DataBoundSetter
    public void setEchoOutputs(boolean echoOutputs) {
        this.echoOutputs = echoOutputs;
    }

    public boolean isDebugOutput() {
        return debugOutput;
    }

    @DataBoundSetter
    public void setDebugOutput(boolean debugOutput) {
        this.debugOutput = debugOutput;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace, final EnvVars envVars, final CloudifyClient cloudifyClient) throws Exception {
        String blueprintId = CloudifyPluginUtilities.expandString(envVars, this.blueprintId);
        String deploymentId = CloudifyPluginUtilities.expandString(envVars, this.deploymentId);
        String inputs = CloudifyPluginUtilities.expandString(envVars, this.inputs);
        String inputsFile = CloudifyPluginUtilities.expandString(envVars, this.inputsFile);
        String mapping = CloudifyPluginUtilities.expandString(envVars, this.mapping);
        String mappingFile = CloudifyPluginUtilities.expandString(envVars, this.mappingFile);
        String outputFile = CloudifyPluginUtilities.expandString(envVars, this.outputFile);

        EnvironmentBuildAction action = new EnvironmentBuildAction();
        action.setBlueprintId(blueprintId);
        action.setDeploymentId(deploymentId);
        run.addOrReplaceAction(action);

        CloudifyEnvironmentData envData = CloudifyPluginUtilities.createEnvironment(listener, workspace, cloudifyClient,
                blueprintId, deploymentId, inputs, inputsFile, mapping, mappingFile, outputFile, skipInstall,
                echoInputs, echoOutputs, debugOutput, x -> true);
        action.applyEnvironmentData(envData);
    }

    @Symbol("createCloudifyEnv")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheckBlueprintId(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckDeploymentId(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckInputs(@QueryParameter String value) {
            // This may consist of expansion parameters (such as "${inputs}")
            // so we can't really validate anything at this stage.
//			return CloudifyPluginUtilities.validateStringIsYamlOrJson(value);
            return FormValidation.ok();
        }

        private FormValidation checkMappingParams(final String mapping, final String mappingFile) {
            if (StringUtils.isNotBlank(mapping) && StringUtils.isNotBlank(mappingFile)) {
                return FormValidation.error("Either mapping or mapping file may be specified, not both");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMapping(@QueryParameter String value, @QueryParameter String mappingFile) {
            return checkMappingParams(value, mappingFile);
        }

        public FormValidation doCheckMappingFile(@QueryParameter String value, @QueryParameter String mapping) {
            return checkMappingParams(mapping, value);
        }

        @Override
        public String getDisplayName() {
            return Messages.CreateEnvironmentBuildStep_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).appendSuper(super.toString()).append("blueprintId", blueprintId)
                .append("deploymentId", deploymentId).append("inputs", inputs).append("inputsFile", inputsFile)
                .append("mapping", mapping).append("mappingFile", mappingFile).append("outputFile", outputFile)
                .append("skipInstall", skipInstall).append("echoInputs", echoInputs).append("echoOutputs", echoOutputs)
                .append("debugOutput", debugOutput).toString();
    }
}
