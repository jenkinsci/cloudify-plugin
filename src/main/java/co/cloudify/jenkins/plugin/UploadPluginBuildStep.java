package co.cloudify.jenkins.plugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.cloudify.rest.client.PluginsClient;
import co.cloudify.rest.model.Plugin;
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

public class UploadPluginBuildStep extends Builder {
	private static final Logger logger = LoggerFactory.getLogger(UploadPluginBuildStep.class);

	private String wagonLocation;
	private String yamlLocation;
	private String outputLocation;

	@DataBoundConstructor
	public UploadPluginBuildStep() {
		super();
	}

	@DataBoundSetter
	public void setWagonLocation(String wagonLocation) {
		this.wagonLocation = wagonLocation;
	}

	public String getWagonLocation() {
		return wagonLocation;
	}

	@DataBoundSetter
	public void setYamlLocation(String yamlLocation) {
		this.yamlLocation = yamlLocation;
	}

	public String getYamlLocation() {
		return yamlLocation;
	}

	@DataBoundSetter
	public void setOutputLocation(String outputLocation) {
		this.outputLocation = outputLocation;
	}
	
	public String getOutputLocation() {
		return outputLocation;
	}
	
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
	        throws InterruptedException, IOException {
		PrintStream jenkinsLog = listener.getLogger();
		listener.started(Arrays.asList(new Cause.UserIdCause()));
		VariableResolver<String> buildVariableResolver = build.getBuildVariableResolver();

		String effectiveWagonLocation = Util.replaceMacro(wagonLocation, buildVariableResolver);
		String effectiveYamlLocation = Util.replaceMacro(yamlLocation, buildVariableResolver);
		String effectiveOutputLocation = Util.replaceMacro(outputLocation, buildVariableResolver);
		PluginsClient client = CloudifyConfiguration.getCloudifyClient().getPluginsClient();

		try {
			Plugin plugin = client.upload(effectiveWagonLocation, effectiveYamlLocation);
			listener.finished(Result.SUCCESS);
			
			if (StringUtils.isNotBlank(effectiveOutputLocation)) {
				File outputFile = new File(build.getWorkspace().child(effectiveOutputLocation).getRemote());
				jenkinsLog.println(String.format("Saving plugin information to %s", outputFile));
				CloudifyPluginUtilities.writeJson(plugin, outputFile);
			}
		} catch (Exception ex) {
			// Jenkins doesn't like Exception causes (doesn't print them).
			logger.error("Exception encountered during plugin upload", ex);
			listener.finished(Result.FAILURE);
			throw new AbortException(String.format("Exception encountered during plugin upload: %s", ex));
		}
		jenkinsLog.println("Plugin uploaded successfully");
		return true;
	}

	@Symbol("uploadCloudifyBlueprint")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public FormValidation doCheckWagonLocation(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}

		public FormValidation doCheckYamlLocation(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}

		@Override
		public String getDisplayName() {
			return "Upload Cloudify plugin";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
		        .appendSuper(super.toString())
		        .append("wagonLocation", wagonLocation)
		        .append("yamlLocation", yamlLocation)
		        .toString();
	}
}
