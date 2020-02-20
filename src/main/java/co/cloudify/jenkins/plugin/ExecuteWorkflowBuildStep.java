package co.cloudify.jenkins.plugin;

import java.io.PrintStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.client.ExecutionsClient;
import co.cloudify.rest.helpers.ExecutionFollowCallback;
import co.cloudify.rest.helpers.ExecutionsHelper;
import co.cloudify.rest.helpers.PrintStreamLogEmitterExecutionFollower;
import co.cloudify.rest.model.Execution;
import co.cloudify.rest.model.ExecutionStatus;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import net.sf.json.JSONObject;

public class ExecuteWorkflowBuildStep extends CloudifyBuildStep {
	private	String deploymentId;
	private String workflowId;
	private String executionParameters;

	@DataBoundConstructor
	public ExecuteWorkflowBuildStep() {
		super();
	}
	
	public String getDeploymentId() {
		return deploymentId;
	}

	@DataBoundSetter
	public void setDeploymentId(String deploymentId) {
		this.deploymentId = deploymentId;
	}
	
	public String getWorkflowId() {
		return workflowId;
	}

	@DataBoundSetter
	public void setWorkflowId(String workflowId) {
		this.workflowId = workflowId;
	}
	
	public String getExecutionParameters() {
		return executionParameters;
	}
	
	@DataBoundSetter
	public void setExecutionParameters(String executionParameters) {
		this.executionParameters = executionParameters;
	}
	
	@Override
	protected void perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener,
	        CloudifyClient cloudifyClient) throws Exception {
		PrintStream jenkinsLog = listener.getLogger();
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
				
		Execution execution = executionsClient.start(effectiveDeploymentId, effectiveWorkflowId, executionParametersAsMap);
		execution = ExecutionsHelper.followExecution(cloudifyClient, execution, follower);
		if (execution.getStatus() != ExecutionStatus.terminated) {
			throw new Exception(String.format("Execution did not end successfully; execution=", execution));
		}
	}

	@Symbol("executeCloudifyWorkflow")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public FormValidation doCheckDeploymentId(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
		public FormValidation doCheckWorkflowId(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
		@Override
		public String getDisplayName() {
			return "Execute Cloudify workflow";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("deploymentId", deploymentId)
				.append("workflowId", workflowId)
				.append("executionParametrs", executionParameters)
				.toString();
	}
}
