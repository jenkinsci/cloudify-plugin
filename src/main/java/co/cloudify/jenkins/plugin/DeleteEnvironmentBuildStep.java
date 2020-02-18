package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.cloudify.jenkins.plugin.parameters.EnvironmentParameterValue;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.helpers.DeploymentsHelper;
import co.cloudify.rest.helpers.ExecutionFollowCallback;
import co.cloudify.rest.helpers.ExecutionsHelper;
import co.cloudify.rest.helpers.PrintStreamLogEmitterExecutionFollower;
import co.cloudify.rest.model.Execution;
import co.cloudify.rest.model.ExecutionStatus;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

public class DeleteEnvironmentBuildStep extends Builder {
	private static final Logger logger = LoggerFactory.getLogger(DeleteEnvironmentBuildStep.class);
	
	private String envId;
	private boolean ignoreFailure;

	@DataBoundConstructor
	public DeleteEnvironmentBuildStep(final String envId, final boolean ignoreFailure) {
		super();
		this.envId = envId;
		this.ignoreFailure = ignoreFailure;
	}

	public String getEnvId() {
		return envId;
	}
	
	public boolean isIgnoreFailure() {
		return ignoreFailure;
	}
	
	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("envId", envId)
				.append("ignoreFailure", ignoreFailure)
				.toString();
	}
	
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		PrintStream jenkinsLog = listener.getLogger();
		listener.started(Arrays.asList(new Cause.UserIdCause()));
		Map<String, String> buildVariables = build.getBuildVariables();
		String envInfoStr = buildVariables.get(envId);
		Validate.notEmpty(envInfoStr, "Couldn't find environment description in build variables; environment id=%s, build variables=%s", envId, buildVariables);
		JSONObject envObj;
		try {
			envObj = JSONObject.fromObject(envInfoStr);
		} catch (JSONException ex) {
			throw new IllegalArgumentException(String.format("Failed parsing environment info to JSON; string=%s", envInfoStr), ex);
		}
		
		String deploymentId = EnvironmentParameterValue.getDeploymentId(envObj);
		CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
		ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(cloudifyClient, jenkinsLog);
		try {
			Map<String, Object> executionParams = new HashMap<String, Object>();
			executionParams.put("ignore_failure", ignoreFailure);
			jenkinsLog.println("Executing the 'uninstall' workflow'");
			Execution execution = cloudifyClient.getExecutionsClient().start(deploymentId, "uninstall", executionParams);
			execution = ExecutionsHelper.followExecution(cloudifyClient, execution, follower);
			ExecutionStatus status = execution.getStatus();
			if (status == ExecutionStatus.terminated) {
				jenkinsLog.println("Execution finished successfully");
			} else {
				throw new Exception(String.format("Execution didn't end well; status=%s", status));
			}
			jenkinsLog.println("Deleting deployment");
			DeploymentsHelper.deleteDeploymentAndCheck(cloudifyClient, deploymentId);
			listener.finished(Result.SUCCESS);
		} catch (Exception ex) {
			//	Jenkins doesn't like Exception causes (doesn't print them).
			logger.error("Exception encountered during environment deletion", ex);
			listener.finished(Result.FAILURE);
			throw new AbortException("Exception encountered during environment deletion");
		}
		listener.finished(Result.SUCCESS);
		return true;
	}

	@Symbol("deleteCloudifyEnv")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Delete Cloudify environment";
		}
	}
}
