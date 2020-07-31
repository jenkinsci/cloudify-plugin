package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import co.cloudify.jenkins.plugin.actions.EnvironmentBuildAction;
import co.cloudify.jenkins.plugin.callables.BlueprintUploadDirFileCallable;
import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.helpers.DeploymentsHelper;
import co.cloudify.rest.model.Blueprint;
import co.cloudify.rest.model.Deployment;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
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
    private String credentialsId;
    private String tenant;
    private String blueprintId;
    private String blueprintRootDirectory;
    private String blueprintArchiveUrl;
    private String blueprintMainFile;
    private String deploymentId;
    private String inputs;
    private String inputsLocation;
    private String outputsLocation;
    private boolean ignoreFailureOnTeardown;
    private boolean echoInputs;
    private boolean echoOutputs;
    private boolean debugOutput;

    @DataBoundConstructor
    public CloudifyBuildWrapper() {
        super();
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getTenant() {
        return tenant;
    }

    @DataBoundSetter
    public void setTenant(String tenant) {
        this.tenant = tenant;
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

    public String getBlueprintArchiveUrl() {
        return blueprintArchiveUrl;
    }

    @DataBoundSetter
    public void setBlueprintArchiveUrl(String blueprintArchiveUrl) {
        this.blueprintArchiveUrl = blueprintArchiveUrl;
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

    protected String expand(final EnvVars environment, final String value) {
        return StringUtils.trimToNull(environment.expand(value));
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener,
            EnvVars initialEnvironment) throws IOException, InterruptedException {
        String tenant = expand(initialEnvironment, this.tenant);
        String blueprintId = expand(initialEnvironment, this.blueprintId);
        String blueprintRootDirectory = expand(initialEnvironment, this.blueprintRootDirectory);
        String blueprintArchiveUrl = expand(initialEnvironment, this.blueprintArchiveUrl);
        String blueprintMainFile = expand(initialEnvironment, this.blueprintMainFile);
        String deploymentId = expand(initialEnvironment, this.deploymentId);
        String inputs = expand(initialEnvironment, this.inputs);
        String inputsLocation = expand(initialEnvironment, this.inputsLocation);
        String outputsLocation = expand(initialEnvironment, this.outputsLocation);

        EnvironmentBuildAction action = new EnvironmentBuildAction();
        action.setBlueprintId(blueprintId);
        action.setDeploymentId(deploymentId);
        build.addOrReplaceAction(action);

        CloudifyDisposer disposer = new CloudifyDisposer(credentialsId, tenant, debugOutput);
        context.setDisposer(disposer);

        StandardUsernamePasswordCredentials creds = CloudifyPluginUtilities
                .getUsernamePasswordCredentials(credentialsId, build);
        CloudifyClient client = CloudifyConfiguration.getCloudifyClient(creds, tenant);
        BlueprintsClient blueprintsClient = client.getBlueprintsClient();
        PrintStream logger = listener.getLogger();

        Blueprint blueprint;
        if (blueprintMainFile == null) {
            logger.println(String.format("Retrieving blueprint: %s", blueprintId));
            blueprint = blueprintsClient.get(blueprintId);
        } else {
            if (blueprintArchiveUrl != null) {
                logger.println(String.format("Uploading blueprint '%s' from %s (main filename: %s)", blueprintId,
                        blueprintArchiveUrl, blueprintMainFile));
                blueprint = blueprintsClient.upload(blueprintId, new URL(blueprintArchiveUrl), blueprintMainFile);
            } else {
                FilePath rootFilePath = workspace.child(blueprintRootDirectory);
                logger.println(String.format("Uploading blueprint '%s' from %s (main filename: %s)", blueprintId,
                        rootFilePath, blueprintMainFile));

                blueprint = rootFilePath
                        .act(new BlueprintUploadDirFileCallable(blueprintsClient, blueprintId, blueprintMainFile));
            }
            // This blueprint will need to be disposed of.
            disposer.setBlueprint(blueprint);
        }

        CloudifyEnvironmentData envData = CloudifyPluginUtilities.createEnvironment(listener, workspace, client,
                blueprint.getId(), deploymentId, inputs, inputsLocation, null, null, outputsLocation, false, echoInputs,
                echoOutputs, debugOutput, x -> true);

        disposer.setDeployment(envData.getDeployment(), ignoreFailureOnTeardown);
        action.applyEnvironmentData(envData);
    }

    public static class CloudifyDisposer extends Disposer {
        /** Serialization UID. */
        private static final long serialVersionUID = 1L;

        private String credentialsId;
        private String tenant;
        private Blueprint blueprint;
        private Deployment deployment;
        private Boolean ignoreFailure;
        private boolean debugOutput;

        public CloudifyDisposer(String credentialsId, String tenant, boolean debugOutput) {
            super();
            this.credentialsId = credentialsId;
            this.tenant = tenant;
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
            StandardUsernamePasswordCredentials creds = CloudifyPluginUtilities
                    .getUsernamePasswordCredentials(credentialsId, build);
            CloudifyClient client = CloudifyConfiguration.getCloudifyClient(creds, tenant);
            PrintStream logger = listener.getLogger();

            if (deployment != null) {
                CloudifyPluginUtilities.deleteEnvironment(listener, client, deployment.getId(),
                        DeploymentsHelper.DEFAULT_POLLING_INTERVAL, false, ignoreFailure, debugOutput);
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
                final String blueprintArchiveUrl, final String blueprintMainFile) {
            if (StringUtils.isNotBlank(blueprintMainFile) && StringUtils.isBlank(blueprintRootDirectory)
                    && StringUtils.isBlank(blueprintArchiveUrl)) {
                return FormValidation.error(
                        "If blueprint's main file is populated, then either blueprint's root directory or archive URL must be specified at runtime");
            }
            return FormValidation.ok();
        }

        public FormValidation checkBlueprintArchiveUrl(@QueryParameter String value,
                @QueryParameter String blueprintRootDirectory, @QueryParameter String blueprintMainFile) {
            return checkBlueprintParams(blueprintRootDirectory, value, blueprintMainFile);
        }

        public FormValidation doCheckBlueprintMainFile(@QueryParameter String value,
                @QueryParameter String blueprintArchiveUrl, @QueryParameter String blueprintRootDirectory) {
            return checkBlueprintParams(blueprintRootDirectory, blueprintArchiveUrl, value);
        }

        public FormValidation doCheckBlueprintRootDirectory(@QueryParameter String value,
                @QueryParameter String blueprintArchiveUrl, @QueryParameter String blueprintMainFile) {
            return checkBlueprintParams(value, blueprintArchiveUrl, blueprintMainFile);
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
        return new ToStringBuilder(this).appendSuper(super.toString()).append("credentialsId", credentialsId)
                .append("tenant", tenant).append("blueprintId", blueprintId)
                .append("blueprintMainFile", blueprintMainFile).append("blueprintRootDirectory", blueprintRootDirectory)
                .append("blueprintArchiveUrl", blueprintArchiveUrl).append("deploymentId", deploymentId)
                .append("inputs", inputs).append("inputsLocation", inputsLocation)
                .append("outputsLocation", outputsLocation).append("ignoreFailureOnTeardown", ignoreFailureOnTeardown)
                .append("echoInputs", echoInputs).append("echoOutputs", echoOutputs).append("debugOutput", debugOutput)
                .toString();
    }
}
