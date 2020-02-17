package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.helpers.DeploymentsHelper;
import co.cloudify.rest.helpers.ExecutionFollowCallback;
import co.cloudify.rest.helpers.ExecutionsHelper;
import co.cloudify.rest.helpers.PrintStreamLogEmitterExecutionFollower;
import co.cloudify.rest.model.Deployment;
import co.cloudify.rest.model.Execution;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;

public class CreateEnvironmentBuildStep extends Builder {
	private String envId;

	@DataBoundConstructor
	public CreateEnvironmentBuildStep(String envId) {
		super();
		this.envId = envId;
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("envId", envId)
				.toString();
	}
	
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		PrintStream logger = listener.getLogger();
		List<Cause> buildStepCause = new ArrayList();
		buildStepCause.add(new Cause() {
			public String getShortDescription() {
				return "Build Step started by Cloudify Environment Builder";
			}
		});
		listener.started(buildStepCause);
		Map<String, String> buildVariables = build.getBuildVariables();
		logger.println("buildVariables=" + buildVariables);
		JSONObject envObj = JSONObject.fromObject(buildVariables.get(envId));
		String blueprintId = envObj.getString("blueprintId");
		JSONObject inputs = JSONObject.fromObject(envObj.getString("inputs"));
		CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
		ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(cloudifyClient, logger);
		Deployment deployment = DeploymentsHelper.createDeploymentAndWait(cloudifyClient, envId, blueprintId, inputs, follower);
		Execution execution = cloudifyClient.getExecutionsClient().start(deployment, "install", null);
		execution = ExecutionsHelper.followExecution(cloudifyClient, execution, follower);
		Map<String, Object> capabilities = cloudifyClient.getDeploymentsClient().getCapabilities(deployment);
		logger.println("capabilities=" + capabilities);
		listener.finished(Result.SUCCESS);
		return true;
	}

	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return FreeStyleProject.class.isAssignableFrom(jobType);
		}

		@Override
		public String getDisplayName() {
			return "Build Cloudify environment";
		}
	}
}
