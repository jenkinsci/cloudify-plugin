package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

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
//		logger.println(String.format("Starting Cloudify Environment build: blueprintId=%s", blueprintId));
		return true;
	}
	
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return FreeStyleProject.class.isAssignableFrom(jobType);
		}
		
//		public ListBoxModel doFillBlueprintIdItems() {
//			ListBoxModel model = new ListBoxModel();
//			CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
//			ListResponse<Blueprint> blueprints = cloudifyClient.getBlueprintsClient().list();
//			blueprints.forEach(item -> model.add(item.getId()));
//			return model;
//		}
		
		@Override
		public String getDisplayName() {
			return "Build Cloudify environment";
		}
	}
}
