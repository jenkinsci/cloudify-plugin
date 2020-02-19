package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.cloudify.rest.client.BlueprintsClient;
import hudson.AbortException;
import hudson.Extension;
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

public class DeleteBlueprintBuildStep extends Builder {
	private static final Logger logger = LoggerFactory.getLogger(DeleteBlueprintBuildStep.class);

	private String              blueprintId;

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
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
	        throws InterruptedException, IOException {
		PrintStream jenkinsLog = listener.getLogger();
		listener.started(Arrays.asList(new Cause.UserIdCause()));
		VariableResolver<String> buildVariableResolver = build.getBuildVariableResolver();
		String effectiveBlueprintId = Util.replaceMacro(blueprintId, buildVariableResolver);

		BlueprintsClient client = CloudifyConfiguration.getCloudifyClient().getBlueprintsClient();

		try {
			client.delete(effectiveBlueprintId);
		} catch (Exception ex) {
			// Jenkins doesn't like Exception causes (doesn't print them).
			logger.error("Exception encountered during blueprint deletion", ex);
			listener.finished(Result.FAILURE);
			throw new AbortException(String.format("Exception encountered during blueprint deletion: %s", ex));
		}
		jenkinsLog.println("Blueprint deleted successfully");
		listener.finished(Result.SUCCESS);
		return true;
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
			return "Delete Cloudify blueprint";
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
