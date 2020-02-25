package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.helpers.DeploymentsHelper;
import co.cloudify.rest.helpers.ExecutionFollowCallback;
import co.cloudify.rest.helpers.ExecutionsHelper;
import co.cloudify.rest.helpers.PrintStreamLogEmitterExecutionFollower;
import co.cloudify.rest.model.Execution;
import co.cloudify.rest.model.ExecutionStatus;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;

/**
 * A build step for deleting a Cloudify environment.
 * 
 * @author	Isaac Shabtay
 */
public class DeleteEnvironmentBuildStep extends CloudifyBuildStep {
	private String deploymentId;
	private boolean ignoreFailure;

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

	@Override
	protected void performImpl(Run<?, ?> run, Launcher launcher, TaskListener listener, FilePath workspace,
	        CloudifyClient cloudifyClient) throws Exception {
		PrintStream jenkinsLog = listener.getLogger();
		ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(cloudifyClient, jenkinsLog);
		
		Map<String, Object> executionParams = new HashMap<String, Object>();
		executionParams.put("ignore_failure", ignoreFailure);
		
		jenkinsLog.println(String.format("Executing the 'uninstall' workflow on deployment: %s", deploymentId));
		Execution execution = ExecutionsHelper.startAndFollow(
				cloudifyClient, deploymentId, "uninstall", executionParams, follower);
		ExecutionStatus status = execution.getStatus();
		if (status != ExecutionStatus.terminated) {
			throw new Exception(String.format("Execution didn't end well; status=%s", status));
		}
		jenkinsLog.println("Execution finished successfully; deleting deployment");
		DeploymentsHelper.deleteDeploymentAndWait(cloudifyClient, deploymentId);
	}

	@Override
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		VariableResolver<String> buildVariableResolver = build.getBuildVariableResolver();
		deploymentId = Util.replaceMacro(deploymentId, buildVariableResolver);
		return super.perform(build, launcher, listener);
	}
	
	@Symbol("deleteCloudifyEnv")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public FormValidation doCheckDeploymentId(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
		@Override
		public String getDisplayName() {
			return "Delete Cloudify Environment";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("deploymentId", deploymentId)
				.append("ignoreFailure", ignoreFailure)
				.toString();
	}
}
