package co.cloudify.jenkins.plugin;

import java.io.File;
import java.io.PrintStream;
import java.net.URL;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.client.CloudifyClient;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;

public class UploadBlueprintBuildStep extends CloudifyBuildStep {
	private String blueprintId;
	private String archiveUrl;
	private String archivePath;
	private String rootDirectory;
	private String mainFileName;

	@DataBoundConstructor
	public UploadBlueprintBuildStep() {
		super();
	}

	public String getBlueprintId() {
		return blueprintId;
	}

	@DataBoundSetter
	public void setBlueprintId(String blueprintId) {
		this.blueprintId = blueprintId;
	}

	public String getArchiveUrl() {
		return archiveUrl;
	}

	@DataBoundSetter
	public void setArchiveUrl(String archiveUrl) {
		this.archiveUrl = archiveUrl;
	}

	public String getArchivePath() {
		return archivePath;
	}

	@DataBoundSetter
	public void setArchivePath(String archivePath) {
		this.archivePath = archivePath;
	}

	public String getRootDirectory() {
		return rootDirectory;
	}

	@DataBoundSetter
	public void setRootDirectory(String rootDirectory) {
		this.rootDirectory = rootDirectory;
	}

	public String getMainFileName() {
		return mainFileName;
	}

	public void setMainFileName(String mainFileName) {
		this.mainFileName = mainFileName;
	}

	@Override
	protected void perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener,
	        CloudifyClient cloudifyClient) throws Exception {
		PrintStream jenkinsLog = listener.getLogger();
		VariableResolver<String> buildVariableResolver = build.getBuildVariableResolver();
		String effectiveBlueprintId = Util.replaceMacro(blueprintId, buildVariableResolver);
		String effectiveArchiveUrl = Util.replaceMacro(archiveUrl, buildVariableResolver);
		String effectiveArchivePath = Util.replaceMacro(archivePath, buildVariableResolver);
		String effectiveRootDirectory = Util.replaceMacro(rootDirectory, buildVariableResolver);
		String effectiveMainFileName = Util.replaceMacro(mainFileName, buildVariableResolver);

		BlueprintsClient blueprintsClient = cloudifyClient.getBlueprintsClient();
		
		if (StringUtils.isNotBlank(effectiveArchiveUrl)) {
			jenkinsLog.println(String.format("Uploading blueprint from %s", effectiveArchiveUrl));
			blueprintsClient.upload(effectiveBlueprintId, new URL(effectiveArchiveUrl), effectiveMainFileName);
		} else if (StringUtils.isNotBlank(effectiveArchivePath)) {
			File absoluteArchivePath = new File(build.getWorkspace().child(effectiveArchivePath).getRemote());
			jenkinsLog.println(String.format("Uploading blueprint from %s", absoluteArchivePath));
			blueprintsClient.uploadArchive(effectiveBlueprintId, absoluteArchivePath, effectiveMainFileName);
		} else {
			File absoluteRootDir = new File(build.getWorkspace().child(effectiveRootDirectory).getRemote());
			jenkinsLog.println(String.format("Uploading blueprint from %s", absoluteRootDir));
			blueprintsClient.upload(effectiveBlueprintId, absoluteRootDir, effectiveMainFileName);
		}
		jenkinsLog.println("Blueprint uploaded successfully");
	}

	@Symbol("uploadCloudifyBlueprint")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public FormValidation doCheckBlueprintId(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}

		protected FormValidation blueprintLocationValidation(
		        final String archiveUrl,
		        final String archivePath,
		        final String rootDirectory) {
			long argsProvided = Arrays.asList(archiveUrl, archivePath, rootDirectory)
			        .stream()
			        .filter(StringUtils::isNotBlank)
			        .count();

			if (argsProvided != 1) {
				return FormValidation
				        .error("Please provide exactly one of 'archive URL', 'archive path' or 'root directory'");
			}

			return FormValidation.ok();
		}

		public FormValidation doCheckArchiveUrl(@QueryParameter String value, @QueryParameter String archivePath,
		        @QueryParameter String rootDirectory) {
			return blueprintLocationValidation(value, archivePath, rootDirectory);
		}

		public FormValidation doCheckArchivePath(@QueryParameter String value, @QueryParameter String archiveUrl,
		        @QueryParameter String rootDirectory) {
			return blueprintLocationValidation(archiveUrl, value, rootDirectory);
		}

		public FormValidation doCheckRootDirectory(@QueryParameter String value, @QueryParameter String archivePath,
		        @QueryParameter String archiveUrl) {
			return blueprintLocationValidation(archiveUrl, archivePath, value);
		}

		@Override
		public String getDisplayName() {
			return "Upload Cloudify blueprint";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
		        .appendSuper(super.toString())
		        .append("blueprintId", blueprintId)
		        .append("archiveUrl", archiveUrl)
		        .append("archivePath", archivePath)
		        .append("rootDirectory", rootDirectory)
		        .append("mainFileName", mainFileName)
		        .toString();
	}
}
