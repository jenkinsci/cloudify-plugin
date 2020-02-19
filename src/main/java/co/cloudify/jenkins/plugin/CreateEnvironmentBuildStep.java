package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import net.sf.json.JSONObject;

public class CreateEnvironmentBuildStep extends Builder {
	private static final Logger logger = LoggerFactory.getLogger(CreateEnvironmentBuildStep.class);
	
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
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		PrintStream jenkinsLog = listener.getLogger();
		listener.started(Arrays.asList(new Cause.UserIdCause()));
		CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
		VariableResolver<String> buildVariableResolver = build.getBuildVariableResolver();
		String effectiveBlueprintId = Util.replaceMacro(blueprintId, buildVariableResolver);
		String effectiveDeploymentId = Util.replaceMacro(deploymentId, buildVariableResolver);
		String effectiveInputs = Util.replaceMacro(inputs, buildVariableResolver);
		String effectiveInputsFile = Util.replaceMacro(inputsFile, buildVariableResolver);
		String effectiveOutputFile = Util.replaceMacro(outputFile, buildVariableResolver);
		
		String inputsAsString = effectiveInputs;
		
		if (StringUtils.isBlank(inputsAsString) && StringUtils.isNotBlank(effectiveInputsFile)) {
			try (InputStream is = build.getWorkspace().child(effectiveInputsFile).read()) {
				inputsAsString = IOUtils.toString(is, StandardCharsets.UTF_8);
			}
		}
		
		inputsAsString = StringUtils.trimToNull(inputsAsString);
		Map<String, Object> inputsMap = inputsAsString != null ? JSONObject.fromObject(inputsAsString) : null;
		ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(cloudifyClient, jenkinsLog);
		
		try {
			jenkinsLog.println(String.format("Creating deployment %s from blueprint %s", effectiveDeploymentId, effectiveBlueprintId));
			Deployment deployment = DeploymentsHelper.createDeploymentAndWait(cloudifyClient, effectiveDeploymentId, effectiveBlueprintId, inputsMap, follower);
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
			FilePath outputFilePath = build.getWorkspace().child(effectiveOutputFile);
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

		protected FormValidation inputsValidation(final String inputs, final String inputsFile) {
			if (StringUtils.isNotBlank(inputs) && StringUtils.isNotBlank(inputsFile)) {
				return FormValidation.error("Either inputs or inputs file may be provided, not both");
			}
			return FormValidation.ok();
		}
		
        public FormValidation doCheckInputs(@QueryParameter String value, @QueryParameter String inputsFile)
                throws IOException, ServletException {
        	return inputsValidation(value, inputsFile);
        }

        public FormValidation doCheckInputsFile(@QueryParameter String value, @QueryParameter String inputs)
                throws IOException, ServletException {
        	return inputsValidation(inputs, value);
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
				.append("blueprintId", blueprintId)
				.append("deploymentId", deploymentId)
				.append("outputFile", outputFile)
				.toString();
	}
}
