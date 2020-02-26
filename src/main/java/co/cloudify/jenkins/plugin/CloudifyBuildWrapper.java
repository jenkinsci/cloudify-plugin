package co.cloudify.jenkins.plugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.model.Blueprint;
import co.cloudify.rest.model.Deployment;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildWrapper;

public class CloudifyBuildWrapper extends SimpleBuildWrapper {
	private String blueprintId;
	private String blueprintRootDirectory;
	private String blueprintMainFile;
	private String deploymentId;
	private String inputs;
	private String inputsLocation;
	private	String outputsLocation;
	private boolean ignoreFailureOnTeardown;

	@DataBoundConstructor
	public CloudifyBuildWrapper() {
		super();
	}

	public String getBlueprintId() {
		return blueprintId;
	}
	
	@DataBoundSetter
	public void setBlueprintId(String blueprintId) {
		this.blueprintId = blueprintId;
	}

	public String getBlueprintRootDirectory() {
		return blueprintRootDirectory;
	}
	
	@DataBoundSetter
	public void setBlueprintRootDirectory(String blueprintRootDirectory) {
		this.blueprintRootDirectory = blueprintRootDirectory;
	}
	
	public String getBlueprintMainFile() {
		return blueprintMainFile;
	}
	
	@DataBoundSetter
	public void setBlueprintMainFile(String blueprintMainFile) {
		this.blueprintMainFile = blueprintMainFile;
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
	
	public String getInputsLocation() {
		return inputsLocation;
	}
	
	@DataBoundSetter
	public void setInputsLocation(String inputsLocation) {
		this.inputsLocation = inputsLocation;
	}
	
	public String getOutputsLocation() {
		return outputsLocation;
	}
	
	@DataBoundSetter
	public void setOutputsLocation(String outputsLocation) {
		this.outputsLocation = outputsLocation;
	}
	
	public boolean isIgnoreFailureOnTeardown() {
		return ignoreFailureOnTeardown;
	}
	
	@DataBoundSetter
	public void setIgnoreFailureOnTeardown(boolean ignoreFailureOnTeardown) {
		this.ignoreFailureOnTeardown = ignoreFailureOnTeardown;
	}
	
	@Override
	public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener,
	        EnvVars initialEnvironment) throws IOException, InterruptedException {
		String blueprintId = Util.replaceMacro(this.blueprintId, initialEnvironment);
		String blueprintRootDirectory = Util.replaceMacro(this.blueprintRootDirectory, initialEnvironment);
		String blueprintMainFile = Util.replaceMacro(this.blueprintMainFile, initialEnvironment);
		String deploymentId = Util.replaceMacro(this.deploymentId, initialEnvironment);
		String inputs = Util.replaceMacro(this.inputs, initialEnvironment);
		String inputsLocation = Util.replaceMacro(this.inputsLocation, initialEnvironment);
		String outputsLocation = Util.replaceMacro(this.outputsLocation, initialEnvironment);
		
		CloudifyDisposer disposer = new CloudifyDisposer();
		context.setDisposer(disposer);
		
		CloudifyClient client = CloudifyConfiguration.getCloudifyClient();
		BlueprintsClient blueprintsClient = client.getBlueprintsClient();
		PrintStream logger = listener.getLogger();

		Blueprint blueprint;
		if (StringUtils.isBlank(blueprintRootDirectory)) {
			logger.println(String.format("Retrieving blueprint: %s", blueprintId));
			blueprint = blueprintsClient.get(blueprintId);
		} else {
			blueprint = blueprintsClient.upload(blueprintId, new File(blueprintRootDirectory), blueprintMainFile);
			//	This blueprint will need to be disposed.
			disposer.setBlueprint(blueprint);
		}
		
		CloudifyEnvironmentData envData = CloudifyPluginUtilities.createEnvironment(
				listener, workspace, client, blueprint.getId(),
				deploymentId, inputs, inputsLocation, outputsLocation);
		disposer.setDeployment(envData.getDeployment());
		disposer.setIgnoreFailure(ignoreFailureOnTeardown);
	}

	public static class CloudifyDisposer extends Disposer {
		/**	Serialization UID. */
		private static final long serialVersionUID = 1L;

		private Blueprint blueprint;
		private Deployment deployment;
		private Boolean	ignoreFailure;
		
		public CloudifyDisposer() {
			super();
		}
		
		public void setBlueprint(Blueprint blueprint) {
			this.blueprint = blueprint;
		}
		
		public void setDeployment(Deployment deployment) {
			this.deployment = deployment;
		}
		
		public void setIgnoreFailure(Boolean ignoreFailure) {
			this.ignoreFailure = ignoreFailure;
		}
		
		@Override
		public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
		        throws IOException, InterruptedException {
			CloudifyClient client = CloudifyConfiguration.getCloudifyClient();
			PrintStream logger = listener.getLogger();
			
			if (deployment != null) {
				CloudifyPluginUtilities.deleteEnvironment(listener, client, deployment.getId(), ignoreFailure);
			}

			if (blueprint != null) {
				String blueprintId = blueprint.getId();
				logger.println(String.format("Deleting blueprint: %s", blueprintId));
				client.getBlueprintsClient().delete(blueprintId);
			}
		}
	}
	
	@Extension
	public static class Descriptor extends BuildWrapperDescriptor {
		@Override
		public boolean isApplicable(AbstractProject<?, ?> item) {
			return true;
		}

		public FormValidation doCheckBlueprintId(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
		private FormValidation checkBlueprintParams(final String blueprintRootDirectory, final String blueprintMainFile) {
			if (StringUtils.isBlank(blueprintMainFile) ^ StringUtils.isBlank(blueprintRootDirectory)) {
				return FormValidation.error("Both blueprint root directory and main file must either be populated, or remain empty");
			}
			return FormValidation.ok();
		}
		
		public FormValidation doCheckBlueprintMainFile(@QueryParameter String value, @QueryParameter String blueprintRootDirectory) {
			return checkBlueprintParams(blueprintRootDirectory, value);
		}

		public FormValidation doCheckBlueprintRootDirectory(@QueryParameter String value, @QueryParameter String blueprintMainFile) {
			return checkBlueprintParams(value, blueprintMainFile);
		}

		public FormValidation doCheckDeploymentId(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
		public FormValidation doCheckInputs(@QueryParameter String value) {
			return CloudifyPluginUtilities.validateInputs(value);
		}
		
		@Override
		public String getDisplayName() {
			return "Cloudify Environment";
		}
	}
}
