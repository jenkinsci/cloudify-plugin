package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.jenkins.plugin.actions.EnvironmentBuildAction;
import co.cloudify.jenkins.plugin.callables.BlueprintUploadDirFileCallable;
import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.model.Blueprint;
import co.cloudify.rest.model.Deployment;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildWrapper;

/**
 * A {@link BuildWrapper} that creates a Cloudify environment during setup time,
 * and deletes at during disposal time.
 * 
 * @author Isaac Shabtay
 */
public class CloudifyBuildWrapper extends SimpleBuildWrapper {
    private String blueprintId;
    private String blueprintRootDirectory;
    private String blueprintMainFile;
    private String deploymentId;
    private String inputs;
    private String inputsLocation;
    private String outputsLocation;
    private boolean ignoreFailureOnTeardown;
    private boolean echoOutputs;
    private boolean debugOutput;

    @DataBoundConstructor
    public CloudifyBuildWrapper() {
        super();
    }

    public String getBlueprintId() {
        return blueprintId;
    }

    @DataBoundSetter
    public void setBlueprintId(String blueprintId) {
        this.blueprintId = blueprintId;
    }

    public String getBlueprintRootDirectory() {
        return blueprintRootDirectory;
    }

    @DataBoundSetter
    public void setBlueprintRootDirectory(String blueprintRootDirectory) {
        this.blueprintRootDirectory = blueprintRootDirectory;
    }

    public String getBlueprintMainFile() {
        return blueprintMainFile;
    }

    @DataBoundSetter
    public void setBlueprintMainFile(String blueprintMainFile) {
        this.blueprintMainFile = blueprintMainFile;
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

    public String getInputsLocation() {
        return inputsLocation;
    }

    @DataBoundSetter
    public void setInputsLocation(String inputsLocation) {
        this.inputsLocation = inputsLocation;
    }

    public String getOutputsLocation() {
        return outputsLocation;
    }

    @DataBoundSetter
    public void setOutputsLocation(String outputsLocation) {
        this.outputsLocation = outputsLocation;
    }

    public boolean isIgnoreFailureOnTeardown() {
        return ignoreFailureOnTeardown;
    }

    @DataBoundSetter
    public void setIgnoreFailureOnTeardown(boolean ignoreFailureOnTeardown) {
        this.ignoreFailureOnTeardown = ignoreFailureOnTeardown;
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
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener,
            EnvVars initialEnvironment) throws IOException, InterruptedException {
        if (build instanceof AbstractBuild) {
            initialEnvironment.overrideAll(((AbstractBuild) build).getBuildVariables());
        }

        String blueprintId = initialEnvironment.expand(this.blueprintId);
        String blueprintRootDirectory = initialEnvironment.expand(this.blueprintRootDirectory);
        String blueprintMainFile = initialEnvironment.expand(this.blueprintMainFile);
        String deploymentId = initialEnvironment.expand(this.deploymentId);
        String inputs = initialEnvironment.expand(this.inputs);
        String inputsLocation = initialEnvironment.expand(this.inputsLocation);
        String outputsLocation = initialEnvironment.expand(this.outputsLocation);

        EnvironmentBuildAction action = new EnvironmentBuildAction();
        action.setBlueprintId(blueprintId);
        action.setDeploymentId(deploymentId);
        build.addOrReplaceAction(action);

        CloudifyDisposer disposer = new CloudifyDisposer(debugOutput);
        context.setDisposer(disposer);

        CloudifyClient client = CloudifyConfiguration.getCloudifyClient();
        BlueprintsClient blueprintsClient = client.getBlueprintsClient();
        PrintStream logger = listener.getLogger();

        Blueprint blueprint;
        if (blueprintRootDirectory == null) {
            logger.println(String.format("Retrieving blueprint: %s", blueprintId));
            blueprint = blueprintsClient.get(blueprintId);
        } else {
            FilePath rootFilePath = workspace.child(blueprintRootDirectory);
            logger.println(String.format(
                    "Uploading blueprint '%s' from %s (main filename: %s)",
                    blueprintId,
                    rootFilePath, blueprintMainFile));

            blueprint = rootFilePath.act(
                    new BlueprintUploadDirFileCallable(
                            blueprintsClient, blueprintId, blueprintMainFile));
            // This blueprint will need to be disposed of.
            disposer.setBlueprint(blueprint);
        }

        CloudifyEnvironmentData envData = CloudifyPluginUtilities.createEnvironment(
                listener, workspace, client, blueprint.getId(),
                deploymentId, inputs, inputsLocation, null, null, outputsLocation, echoOutputs, debugOutput);

        disposer.setDeployment(envData.getDeployment(), ignoreFailureOnTeardown);
        action.applyEnvironmentData(envData);
    }

    public static class CloudifyDisposer extends Disposer {
        /** Serialization UID. */
        private static final long serialVersionUID = 1L;

        private Blueprint blueprint;
        private Deployment deployment;
        private Boolean ignoreFailure;
        private boolean debugOutput;

        public CloudifyDisposer(boolean debugOutput) {
            super();
            this.debugOutput = debugOutput;
        }

        public void setBlueprint(Blueprint blueprint) {
            this.blueprint = blueprint;
        }

        public void setDeployment(Deployment deployment, Boolean ignoreFailure) {
            this.deployment = deployment;
            this.ignoreFailure = ignoreFailure;
        }

        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
                throws IOException, InterruptedException {
            CloudifyClient client = CloudifyConfiguration.getCloudifyClient();
            PrintStream logger = listener.getLogger();

            if (deployment != null) {
                CloudifyPluginUtilities.deleteEnvironment(listener, client, deployment.getId(), ignoreFailure,
                        debugOutput);
            }

            if (blueprint != null) {
                String blueprintId = blueprint.getId();
                logger.println(String.format("Deleting blueprint: %s", blueprintId));
                client.getBlueprintsClient().delete(blueprintId);
            }
        }
    }

    @Extension
    public static class Descriptor extends BuildWrapperDescriptor {
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        public FormValidation doCheckBlueprintId(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        private FormValidation checkBlueprintParams(final String blueprintRootDirectory,
                final String blueprintMainFile) {
            if (StringUtils.isBlank(blueprintMainFile) ^ StringUtils.isBlank(blueprintRootDirectory)) {
                return FormValidation
                        .error("Both blueprint root directory and main file must either be populated, or remain empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckBlueprintMainFile(@QueryParameter String value,
                @QueryParameter String blueprintRootDirectory) {
            return checkBlueprintParams(blueprintRootDirectory, value);
        }

        public FormValidation doCheckBlueprintRootDirectory(@QueryParameter String value,
                @QueryParameter String blueprintMainFile) {
            return checkBlueprintParams(value, blueprintMainFile);
        }

        public FormValidation doCheckDeploymentId(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckInputs(@QueryParameter String value) {
            return CloudifyPluginUtilities.validateStringIsYamlOrJson(value);
        }

        @Override
        public String getDisplayName() {
            return Messages.CloudifyBuildWrapper_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("blueprintId", blueprintId)
                .append("blueprintMainFile", blueprintMainFile)
                .append("blueprintRootDirectory", blueprintRootDirectory)
                .append("deploymentId", deploymentId)
                .append("inputs", inputs)
                .append("inputsLocation", inputsLocation)
                .append("outputsLocation", outputsLocation)
                .append("ignoreFailureOnTeardown", ignoreFailureOnTeardown)
                .append("echoOutputs", echoOutputs)
                .append("debugOutput", debugOutput)
                .toString();
    }
}
