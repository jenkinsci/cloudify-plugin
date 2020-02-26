package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.jenkins.plugin.actions.EnvironmentBuildAction;
import co.cloudify.rest.client.CloudifyClient;
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
	protected void performImpl(Run<?,?> run, Launcher launcher, TaskListener listener, FilePath workspace, CloudifyClient cloudifyClient) throws Exception {
		EnvironmentBuildAction action = new EnvironmentBuildAction();
		action.setBlueprintId(blueprintId);
		action.setDeploymentId(deploymentId);
		run.addOrReplaceAction(action);

		CloudifyEnvironmentData envData = CloudifyPluginUtilities.createEnvironment(
				listener, workspace, cloudifyClient, blueprintId, deploymentId, inputsFile, inputsFile, outputFile);

		action.setInputs(envData.getDeployment().getInputs());
		action.setOutputs(envData.getOutputs());
		action.setCapabilities(envData.getCapabilities());
	}
	
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		VariableResolver<String> buildVariableResolver = build.getBuildVariableResolver();
		blueprintId = Util.replaceMacro(blueprintId, buildVariableResolver);
		deploymentId = Util.replaceMacro(deploymentId, buildVariableResolver);
		inputs = Util.replaceMacro(inputs, buildVariableResolver);
		inputsFile = Util.replaceMacro(inputsFile, buildVariableResolver);
		outputFile = Util.replaceMacro(outputFile, buildVariableResolver);

		return super.perform(build, launcher, listener);
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
