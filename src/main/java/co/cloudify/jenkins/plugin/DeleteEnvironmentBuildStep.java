package co.cloudify.jenkins.plugin;

import java.io.PrintStream;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.client.DeploymentsClient;
import co.cloudify.rest.helpers.DeploymentsHelper;
import co.cloudify.rest.model.Deployment;
import co.cloudify.rest.model.ListResponse;
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
 * A build step for deleting a Cloudify environment.
 * 
 * @author Isaac Shabtay
 */
public class DeleteEnvironmentBuildStep extends CloudifyBuildStep {
    private String deploymentId;
    private boolean skipUninstall;
    private boolean deleteBlueprintIfLast;
    private boolean ignoreFailure;
    private boolean debugOutput;

    @DataBoundConstructor
    public DeleteEnvironmentBuildStep() {
        super();
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    @DataBoundSetter
    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public boolean isSkipUninstall() {
        return skipUninstall;
    }

    @DataBoundSetter
    public void setSkipUninstall(boolean skipUninstall) {
        this.skipUninstall = skipUninstall;
    }

    public boolean isDeleteBlueprintIfLast() {
        return deleteBlueprintIfLast;
    }

    @DataBoundSetter
    public void setDeleteBlueprintIfLast(boolean deleteBlueprintIfLast) {
        this.deleteBlueprintIfLast = deleteBlueprintIfLast;
    }

    public boolean isIgnoreFailure() {
        return ignoreFailure;
    }

    @DataBoundSetter
    public void setIgnoreFailure(boolean ignoreFailure) {
        this.ignoreFailure = ignoreFailure;
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
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        PrintStream logger = listener.getLogger();
        String deploymentId = CloudifyPluginUtilities.expandString(envVars, this.deploymentId);
        DeploymentsClient deploymentsClient = cloudifyClient.getDeploymentsClient();
        Deployment deployment = deploymentsClient.get(deploymentId);
        CloudifyPluginUtilities.deleteEnvironment(listener, cloudifyClient, deploymentId,
                DeploymentsHelper.DEFAULT_POLLING_INTERVAL, skipUninstall, ignoreFailure, debugOutput);

        if (deleteBlueprintIfLast) {
            String blueprintId = deployment.getBlueprintId();
            logger.println(
                    String.format("Checking to see if additional deployments exist for blueprint '%s'", blueprintId));
            ListResponse<Deployment> deployments = deploymentsClient.list(blueprintId, null, null, false);
            if (deployments.getMetadata().getPagination().getTotal() == 0) {
                logger.println(String.format("No additional deployments found; deleting blueprint '%s'", blueprintId));
                cloudifyClient.getBlueprintsClient().delete(blueprintId);
                logger.println("Blueprint deleted");
            }
        }
    }

    @Symbol("deleteCloudifyEnv")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheckDeploymentId(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        @Override
        public String getDisplayName() {
            return Messages.DeleteEnvironmentBuildStep_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("deploymentId", deploymentId)
                .append("skipUninstall", skipUninstall)
                .append("deleteBlueprintIfLast", deleteBlueprintIfLast)
                .append("ignoreFailure", ignoreFailure)
                .append("debugOutput", debugOutput)
                .toString();
    }
}
