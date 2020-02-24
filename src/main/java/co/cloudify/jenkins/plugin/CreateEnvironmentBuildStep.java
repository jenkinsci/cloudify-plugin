package co.cloudify.jenkins.plugin;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.jenkins.plugin.actions.EnvironmentBuildAction;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.client.DeploymentsClient;
import co.cloudify.rest.helpers.DeploymentsHelper;
import co.cloudify.rest.helpers.ExecutionFollowCallback;
import co.cloudify.rest.helpers.ExecutionsHelper;
import co.cloudify.rest.helpers.PrintStreamLogEmitterExecutionFollower;
import co.cloudify.rest.model.Deployment;
import co.cloudify.rest.model.Execution;
import co.cloudify.rest.model.ExecutionStatus;
import hudson.Extension;
import hudson.FilePath;
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

/**
 * A Build Step for creating an environment.
 * 
 * @author	Isaac Shabtay
 */
public class CreateEnvironmentBuildStep extends CloudifyBuildStep {
	private String blueprintId;
	private String deploymentId;
	private String inputs;
	private String inputsFile;
	private	String outputFile;

	@DataBoundConstructor
	public CreateEnvironmentBuildStep() {
		super();
	}

	public String getBlueprintId() {
		return blueprintId;
	}
	
	@DataBoundSetter
	public void setBlueprintId(String blueprintId) {
		this.blueprintId = blueprintId;
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

	public String getInputsFile() {
		return inputsFile;
	}
	
	@DataBoundSetter
	public void setInputsFile(String inputsFile) {
		this.inputsFile = inputsFile;
	}
	
	public String getOutputFile() {
		return outputFile;
	}

	@DataBoundSetter
	public void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
	}
	
	@Override
	protected void perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener,
	        CloudifyClient cloudifyClient) throws Exception {
		PrintStream jenkinsLog = listener.getLogger();
		VariableResolver<String> buildVariableResolver = build.getBuildVariableResolver();
		String effectiveBlueprintId = Util.replaceMacro(blueprintId, buildVariableResolver);
		String effectiveDeploymentId = Util.replaceMacro(deploymentId, buildVariableResolver);
		String effectiveInputs = Util.replaceMacro(inputs, buildVariableResolver);
		String effectiveInputsFile = Util.replaceMacro(inputsFile, buildVariableResolver);
		String effectiveOutputFile = Util.replaceMacro(outputFile, buildVariableResolver);

		EnvironmentBuildAction action = new EnvironmentBuildAction();
		action.setBlueprintId(effectiveBlueprintId);
		action.setDeploymentId(effectiveDeploymentId);
		build.addOrReplaceAction(action);

		Map<String, Object> inputsMap = new HashMap<String, Object>();
		if (StringUtils.isNotBlank(effectiveInputs)) {
			inputsMap.putAll(
					JSONObject.fromObject(effectiveInputs));
		}
		if (StringUtils.isNotBlank(effectiveInputsFile)) {
			FilePath expectedLocation = build.getWorkspace().child(effectiveInputsFile);
			if (expectedLocation.exists()) {
				jenkinsLog.println(String.format("Reading inputs from %s", expectedLocation));
				inputsMap.putAll(CloudifyPluginUtilities.readJson(expectedLocation));
			} else {
				jenkinsLog.println(String.format("Deployment inputs file not found, skipping: %s", effectiveInputsFile));
			}
		}
		ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(cloudifyClient, jenkinsLog);
		
		jenkinsLog.println(
				String.format("Creating deployment %s from blueprint %s",
						effectiveDeploymentId, effectiveBlueprintId));
		Deployment deployment = DeploymentsHelper.createDeploymentAndWait(
				cloudifyClient, effectiveDeploymentId, effectiveBlueprintId, inputsMap, follower);
		action.setInputs(deployment.getInputs());
		
		jenkinsLog.println("Executing the 'install' workflow'");
		Execution execution = ExecutionsHelper.startAndFollow(
				cloudifyClient, deployment.getId(), "install", null, follower);
		ExecutionStatus status = execution.getStatus();
		if (status != ExecutionStatus.terminated) {
			throw new Exception(String.format(
					"Execution didn't end well; status=%s", status));
		}
		jenkinsLog.println("Execution finished successfully");
		
		DeploymentsClient deploymentsClient = cloudifyClient.getDeploymentsClient();
		Map<String, Object> outputs = deploymentsClient.getOutputs(deployment);
		Map<String, Object> capabilities = deploymentsClient.getCapabilities(deployment);
		
		action.setOutputs(outputs);
		action.setCapabilities(capabilities);
		
		if (StringUtils.isNotBlank(effectiveOutputFile)) {
			jenkinsLog.println("Retrieving outputs and capabilities");
			JSONObject output = new JSONObject();
			output.put("outputs", outputs);
			output.put("capabilities", capabilities);
			FilePath outputFilePath = build.getWorkspace().child(effectiveOutputFile);
			jenkinsLog.println(String.format(
					"Writing outputs and capabilities to %s", outputFilePath));
			CloudifyPluginUtilities.writeJson(output, outputFilePath);
		}
	}

	@Symbol("createCloudifyEnv")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public FormValidation doCheckBlueprintId(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
		public FormValidation doCheckDeploymentId(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
        @Override
		public String getDisplayName() {
			return "Build Cloudify Environment";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("blueprintId", blueprintId)
				.append("deploymentId", deploymentId)
				.append("inputs", inputs)
				.append("inputsFile", inputsFile)
				.append("outputFile", outputFile)
				.toString();
	}
}
