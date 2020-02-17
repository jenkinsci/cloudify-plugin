package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import co.cloudify.rest.client.CloudifyClient;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.RunAction2;

public class CreateEnvironmentBuildStep extends Builder {
//	private String blueprintId;

	@DataBoundConstructor
	public CreateEnvironmentBuildStep(String blueprintId) {
		super();
//		this.blueprintId = blueprintId;
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
		CloudifyClient client = CloudifyConfiguration.getCloudifyClient();
		logger.println("client=" + client);
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
