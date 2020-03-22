package co.cloudify.jenkins.plugin;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

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
import hudson.util.VariableResolver;

/**
 * A build step for deleting a Cloudify environment.
 * 
 * @author Isaac Shabtay
 */
public class DeleteEnvironmentBuildStep extends CloudifyBuildStep {
    private String deploymentId;
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
    protected void performImpl(Run<?, ?> run, Launcher launcher, TaskListener listener, FilePath workspace,
            CloudifyClient cloudifyClient) throws Exception {
        EnvVars env = run.getEnvironment(listener);
        VariableResolver<String> resolver = new VariableResolver.ByMap<String>(env);
        String deploymentId = CloudifyPluginUtilities.parseInput(this.deploymentId, resolver);
        CloudifyPluginUtilities.deleteEnvironment(listener, cloudifyClient, deploymentId, ignoreFailure, debugOutput);
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
                .append("ignoreFailure", ignoreFailure)
                .append("debugOutput", debugOutput)
                .toString();
    }
}
