package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

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
 * A build step for deleting a Cloudify blueprint.
 * 
 * @author	Isaac Shabtay
 */
public class DeleteBlueprintBuildStep extends CloudifyBuildStep {
	private String blueprintId;

	@DataBoundConstructor
	public DeleteBlueprintBuildStep() {
		super();
	}

	public String getBlueprintId() {
		return blueprintId;
	}

	@DataBoundSetter
	public void setBlueprintId(String blueprintId) {
		this.blueprintId = blueprintId;
	}

	@Override
	protected void performImpl(Run<?,?> run, Launcher launcher, TaskListener listener, FilePath workspace, CloudifyClient cloudifyClient) throws Exception {
		PrintStream jenkinsLog = listener.getLogger();
		jenkinsLog.println(String.format("Deleting blueprint: %s", blueprintId));
		cloudifyClient.getBlueprintsClient().delete(blueprintId);
	}

	@Override
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException ,IOException {
		VariableResolver<String> buildVariableResolver = build.getBuildVariableResolver();
		blueprintId = Util.replaceMacro(blueprintId, buildVariableResolver);
		return super.perform(build, launcher, listener);
	}
	
	@Symbol("deleteCloudifyBlueprint")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public FormValidation doCheckBlueprintId(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}

		@Override
		public String getDisplayName() {
			return "Delete Cloudify Blueprint";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
		        .appendSuper(super.toString())
		        .append("blueprintId", blueprintId)
		        .toString();
	}
}
