package co.cloudify.jenkins.plugin;

import java.io.PrintStream;
import java.net.URL;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.jenkins.plugin.callables.BlueprintUploadArchiveFileCallable;
import co.cloudify.jenkins.plugin.callables.BlueprintUploadDirFileCallable;
import co.cloudify.jenkins.plugin.callables.BlueprintUploadFileCallable;
import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.client.CloudifyClient;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;

/**
 * A build step for uploading a blueprint.
 * 
 * @author Isaac Shabtay
 */
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
    protected void performImpl(Run<?, ?> run, Launcher launcher, TaskListener listener, FilePath workspace,
            CloudifyClient cloudifyClient) throws Exception {
        EnvVars env = run.getEnvironment(listener);
        VariableResolver<String> resolver = new VariableResolver.ByMap<String>(env);
        String blueprintId = Util.replaceMacro(this.blueprintId, resolver);
        String archiveUrl = Util.replaceMacro(this.archiveUrl, resolver);
        String archivePath = Util.replaceMacro(this.archivePath, resolver);
        String rootDirectory = Util.replaceMacro(this.rootDirectory, resolver);
        String mainFileName = Util.replaceMacro(this.mainFileName, resolver);

        PrintStream jenkinsLog = listener.getLogger();
        BlueprintsClient blueprintsClient = cloudifyClient.getBlueprintsClient();

        if (StringUtils.isNotBlank(archiveUrl)) {
            jenkinsLog.println(String.format("Uploading blueprint from %s", archiveUrl));
            blueprintsClient.upload(blueprintId, new URL(archiveUrl), mainFileName);
        } else {
            FilePath opFile;
            BlueprintUploadFileCallable callable;
            if (StringUtils.isNotBlank(archivePath)) {
                opFile = workspace.child(archivePath);
                callable = new BlueprintUploadArchiveFileCallable(blueprintsClient, blueprintId, mainFileName);
            } else {
                opFile = workspace.child(rootDirectory);
                callable = new BlueprintUploadDirFileCallable(blueprintsClient, blueprintId, mainFileName);
            }
            jenkinsLog.println(String.format("Uploading blueprint from %s", opFile));
            opFile.act(callable);
        }
        jenkinsLog.println("Blueprint uploaded successfully");
    }

    @Symbol("uploadCloudifyBlueprint")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
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
            return "Upload Cloudify Blueprint";
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
