package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.VariableResolver;
import net.sf.json.JSONObject;

public class ExecuteWorkflowBuildStep extends Builder {
	private static final Logger logger = LoggerFactory.getLogger(ExecuteWorkflowBuildStep.class);
	
	private	String deploymentId;
	private String workflowId;
	private String executionParameters;
	
	@DataBoundConstructor
	public ExecuteWorkflowBuildStep(final String deploymentId, final String workflowId, final String executionParameters) {
		super();
		this.deploymentId = deploymentId;
		this.workflowId = workflowId;
		this.executionParameters = executionParameters;
	}
	
	public String getDeploymentId() {
		return deploymentId;
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
		CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
		ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(cloudifyClient, jenkinsLog);
		ExecutionsClient executionsClient = cloudifyClient.getExecutionsClient();
		VariableResolver<String> buildVariableResolver = build.getBuildVariableResolver();
		String effectiveDeploymentId = Util.replaceMacro(deploymentId, buildVariableResolver);
		String effectiveWorkflowId = Util.replaceMacro(workflowId, buildVariableResolver);
		String effectiveExecutionParameters = Util.replaceMacro(executionParameters, buildVariableResolver);;
		
		String strippedParameters = StringUtils.trimToNull(effectiveExecutionParameters);
		JSONObject executionParametersAsMap = strippedParameters != null ?
				JSONObject.fromObject(strippedParameters) :
					null;
				
		try {
			Execution execution = executionsClient.start(effectiveDeploymentId, effectiveWorkflowId, executionParametersAsMap);
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
