package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.Arrays;
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
import co.cloudify.rest.model.Deployment;
import co.cloudify.rest.model.Execution;
import co.cloudify.rest.model.ExecutionStatus;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
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

public class CreateEnvironmentBuildStep extends Builder {
	private static final Logger logger = LoggerFactory.getLogger(CreateEnvironmentBuildStep.class);
	
	private String envId;
	private	String outputFile;

	@DataBoundConstructor
	public CreateEnvironmentBuildStep(String envId, String outputFile) {
		super();
		this.envId = envId;
		this.outputFile = outputFile;
	}

	public String getEnvId() {
		return envId;
	}
	
	public String getOutputFile() {
		return outputFile;
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
		
		String blueprintId = EnvironmentParameterValue.getBlueprintId(envObj);
		String deploymentId = EnvironmentParameterValue.getDeploymentId(envObj);
		Map<String, Object> inputs = EnvironmentParameterValue.getInputs(envObj);
		CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
		ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(cloudifyClient, jenkinsLog);
		
		try {
			jenkinsLog.println(String.format("Creating deployment %s from blueprint %s", deploymentId, blueprintId));
			Deployment deployment = DeploymentsHelper.createDeploymentAndWait(cloudifyClient, deploymentId, blueprintId, inputs, follower);
			jenkinsLog.println("Executing the 'install' workflow'");
			Execution execution = cloudifyClient.getExecutionsClient().start(deployment, "install", null);
			execution = ExecutionsHelper.followExecution(cloudifyClient, execution, follower);
			ExecutionStatus status = execution.getStatus();
			if (status == ExecutionStatus.terminated) {
				jenkinsLog.println("Execution finished successfully");
			} else {
				throw new Exception(String.format("Execution didn't end well; status=%s", status));
			}
			jenkinsLog.println("Retrieving outputs and capabilities");
			Map<String, Object> outputs = cloudifyClient.getDeploymentsClient().getOutputs(deployment);
			Map<String, Object> capabilities = cloudifyClient.getDeploymentsClient().getCapabilities(deployment);
			JSONObject output = new JSONObject();
			output.put("outputs", outputs);
			output.put("capabilities", capabilities);
			FilePath outputFilePath = build.getWorkspace().child(outputFile);
			jenkinsLog.println(String.format("Writing outputs and capabilities to %s", outputFilePath));
			try (OutputStreamWriter osw = new OutputStreamWriter(outputFilePath.write())) {
				osw.write(output.toString(4));
			}
		} catch (Exception ex) {
			//	Jenkins doesn't like Exception causes (doesn't print them).
			logger.error("Exception encountered during environment creation", ex);
			listener.finished(Result.FAILURE);
			throw new AbortException(String.format("Exception encountered during environment creation: %s", ex));
		}
		listener.finished(Result.SUCCESS);
		return true;
	}

	@Symbol("createCloudifyEnv")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Build Cloudify environment";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("envId", envId)
				.append("outputFile", outputFile)
				.toString();
	}
}
