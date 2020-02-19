package co.cloudify.jenkins.plugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
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

public class UploadBlueprintBuildStep extends Builder {
	private static final Logger logger = LoggerFactory.getLogger(UploadBlueprintBuildStep.class);

	private String blueprintId;
	private String archiveUrl;
	private String archivePath;
	private	String rootDirectory;
	private	String mainFileName;

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
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		PrintStream jenkinsLog = listener.getLogger();
		listener.started(Arrays.asList(new Cause.UserIdCause()));
		VariableResolver<String> buildVariableResolver = build.getBuildVariableResolver();
		String effectiveBlueprintId = Util.replaceMacro(blueprintId, buildVariableResolver);
		String effectiveArchiveUrl = Util.replaceMacro(archiveUrl, buildVariableResolver);
		String effectiveArchivePath = Util.replaceMacro(archivePath, buildVariableResolver);
		String effectiveRootDirectory = Util.replaceMacro(rootDirectory, buildVariableResolver);
		String effectiveMainFileName = Util.replaceMacro(mainFileName, buildVariableResolver);

		BlueprintsClient client = CloudifyConfiguration.getCloudifyClient().getBlueprintsClient();
		
		try {
			if (StringUtils.isNotBlank(effectiveArchiveUrl)) {
				jenkinsLog.println(String.format("Uploading blueprint from %s", effectiveArchiveUrl));
				client.upload(effectiveBlueprintId, new URL(effectiveArchiveUrl), effectiveMainFileName);
			} else if (StringUtils.isNotBlank(effectiveArchivePath)) {
				jenkinsLog.println(String.format("Uploading blueprint from %s", effectiveArchivePath));
				client.uploadArchive(effectiveBlueprintId, new File(effectiveArchivePath), effectiveMainFileName);
			} else {
				jenkinsLog.println(String.format("Uploading blueprint from %s", effectiveRootDirectory));
				client.upload(effectiveBlueprintId, new File(effectiveRootDirectory), effectiveMainFileName);
			}
		} catch (Exception ex) {
			// Jenkins doesn't like Exception causes (doesn't print them).
			logger.error("Exception encountered during blueprint upload", ex);
			listener.finished(Result.FAILURE);
			throw new AbortException(String.format("Exception encountered during blueprint upload: %s", ex));
		}
		jenkinsLog.println("Blueprint uploaded successfully");
		listener.finished(Result.SUCCESS);
		return true;
	}

	@Symbol("uploadCloudifyBlueprint")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
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
				return FormValidation.error("Please provide exactly one of 'archive URL', 'archive path' or 'root directory'");
			}
			
			return FormValidation.ok();
		}

		public FormValidation doCheckArchiveUrl(@QueryParameter String value, @QueryParameter String archivePath, @QueryParameter String rootDirectory) {
			return blueprintLocationValidation(value, archivePath, rootDirectory);
		}

		public FormValidation doCheckArchivePath(@QueryParameter String value, @QueryParameter String archiveUrl, @QueryParameter String rootDirectory) {
			return blueprintLocationValidation(archiveUrl, value, rootDirectory);
		}

		public FormValidation doCheckRootDirectory(@QueryParameter String value, @QueryParameter String archivePath, @QueryParameter String archiveUrl) {
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
