package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.cloudify.jenkins.plugin.parameters.EnvironmentParameterValue;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.client.ExecutionsClient;
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

public class ExecuteWorkflowBuildStep extends Builder {
	private static final Logger logger = LoggerFactory.getLogger(ExecuteWorkflowBuildStep.class);
	
	private	String envId;
	private String workflowId;
	private String executionParameters;
	
	@DataBoundConstructor
	public ExecuteWorkflowBuildStep(final String envId, final String workflowId, final String executionParameters) {
		super();
		this.envId = envId;
		this.workflowId = workflowId;
		this.executionParameters = executionParameters;
	}
	
	public String getEnvId() {
		return envId;
	}
	
	public String getWorkflowId() {
		return workflowId;
	}
	
	public String getExecutionParameters() {
		return executionParameters;
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
		String strippedParameters = StringUtils.trimToNull(executionParameters);
		JSONObject executionParametersAsMap = strippedParameters != null ?
				JSONObject.fromObject(strippedParameters) :
					null;
		String deploymentId = EnvironmentParameterValue.getDeploymentId(envObj);
		CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
		ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(cloudifyClient, jenkinsLog);
		ExecutionsClient executionsClient = cloudifyClient.getExecutionsClient();
		
		try {
			Execution execution = executionsClient.start(deploymentId, workflowId, executionParametersAsMap);
			execution = ExecutionsHelper.followExecution(cloudifyClient, execution, follower);
			if (execution.getStatus() != ExecutionStatus.terminated) {
				throw new Exception(String.format("Execution did not end successfully; execution=", execution));
			}
			listener.finished(Result.SUCCESS);
		} catch (Exception ex) {
			//	Jenkins doesn't like Exception causes (doesn't print them).
			logger.error("Exception encountered running execution", ex);
			listener.finished(Result.FAILURE);
			throw new AbortException("Exception encountered running execution");
		}
		return true;
	}

	@Symbol("executeCloudifyWorkflow")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Execute Cloudify workflow";
		}
	}
}
