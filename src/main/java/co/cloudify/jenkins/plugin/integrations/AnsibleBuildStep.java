package co.cloudify.jenkins.plugin.integrations;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import co.cloudify.jenkins.plugin.CloudifyPluginUtilities;
import co.cloudify.jenkins.plugin.Messages;
import co.cloudify.rest.client.CloudifyClient;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

/**
 * A build step for applying an Azure ARM template.
 * 
 * @author Isaac Shabtay
 */
public class AnsibleBuildStep extends IntegrationBuildStep {
    private String sourcePath;
    private String playbookPath;
    private Boolean savePlaybook;
    private Boolean remergeSources;
    private String sources;
    private String runData;
    private String sensitiveKeys;
    private String optionsConfig;
    private String ansibleEnvVars;
    private Integer debugLevel;
    private String additionalArgs;
    private String startAtTask;
    private String scpExtraArgs;
    private String sftpExtraArgs;
    private String sshCommonArgs;
    private String sshExtraArgs;
    private Integer timeout;

    @DataBoundConstructor
    public AnsibleBuildStep() {
        super();
    }

    public String getSourcePath() {
        return sourcePath;
    }

    @DataBoundSetter
    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getPlaybookPath() {
        return playbookPath;
    }

    @DataBoundSetter
    public void setPlaybookPath(String playbookPath) {
        this.playbookPath = playbookPath;
    }

    public Boolean getSavePlaybook() {
        return savePlaybook;
    }

    @DataBoundSetter
    public void setSavePlaybook(Boolean savePlaybook) {
        this.savePlaybook = savePlaybook;
    }

    public Boolean getRemergeSources() {
        return remergeSources;
    }

    @DataBoundSetter
    public void setRemergeSources(Boolean remergeSources) {
        this.remergeSources = remergeSources;
    }

    public String getSources() {
        return sources;
    }

    @DataBoundSetter
    public void setSources(String sources) {
        this.sources = sources;
    }

    public String getRunData() {
        return runData;
    }

    @DataBoundSetter
    public void setRunData(String runData) {
        this.runData = runData;
    }

    public String getSensitiveKeys() {
        return sensitiveKeys;
    }

    @DataBoundSetter
    public void setSensitiveKeys(String sensitiveKeys) {
        this.sensitiveKeys = sensitiveKeys;
    }

    public String getOptionsConfig() {
        return optionsConfig;
    }

    @DataBoundSetter
    public void setOptionsConfig(String optionsConfig) {
        this.optionsConfig = optionsConfig;
    }

    public String getAnsibleEnvVars() {
        return ansibleEnvVars;
    }

    @DataBoundSetter
    public void setAnsibleEnvVars(String ansibleEnvVars) {
        this.ansibleEnvVars = ansibleEnvVars;
    }

    public Integer getDebugLevel() {
        return debugLevel;
    }

    @DataBoundSetter
    public void setDebugLevel(Integer debugLevel) {
        this.debugLevel = debugLevel;
    }

    public String getAdditionalArgs() {
        return additionalArgs;
    }

    @DataBoundSetter
    public void setAdditionalArgs(String additionalArgs) {
        this.additionalArgs = additionalArgs;
    }

    public String getStartAtTask() {
        return startAtTask;
    }

    @DataBoundSetter
    public void setStartAtTask(String startAtTask) {
        this.startAtTask = startAtTask;
    }

    public String getScpExtraArgs() {
        return scpExtraArgs;
    }

    @DataBoundSetter
    public void setScpExtraArgs(String scpExtraArgs) {
        this.scpExtraArgs = scpExtraArgs;
    }

    public String getSftpExtraArgs() {
        return sftpExtraArgs;
    }

    @DataBoundSetter
    public void setSftpExtraArgs(String sftpExtraArgs) {
        this.sftpExtraArgs = sftpExtraArgs;
    }

    public String getSshCommonArgs() {
        return sshCommonArgs;
    }

    @DataBoundSetter
    public void setSshCommonArgs(String sshCommonArgs) {
        this.sshCommonArgs = sshCommonArgs;
    }

    public String getSshExtraArgs() {
        return sshExtraArgs;
    }

    @DataBoundSetter
    public void setSshExtraArgs(String sshExtraArgs) {
        this.sshExtraArgs = sshExtraArgs;
    }

    public Integer getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        String sourcePath = CloudifyPluginUtilities.expandString(envVars, this.sourcePath);
        String playbookPath = CloudifyPluginUtilities.expandString(envVars, this.playbookPath);
        List<String> sources = Arrays
                .asList(StringUtils.defaultString(CloudifyPluginUtilities.expandString(envVars, this.sources)));
        Map<String, Object> runData = CloudifyPluginUtilities.readYamlOrJson(
                CloudifyPluginUtilities.expandString(envVars, this.runData));
        List<String> sensitiveKeys = Arrays
                .asList(StringUtils.defaultString(CloudifyPluginUtilities.expandString(envVars, this.sensitiveKeys))
                        .split("\n"));
        Map<String, Object> optionsConfig = CloudifyPluginUtilities.readYamlOrJson(
                CloudifyPluginUtilities.expandString(envVars, this.optionsConfig));
        Map<String, Object> ansibleEnvVars = CloudifyPluginUtilities.readYamlOrJson(
                CloudifyPluginUtilities.expandString(envVars, this.ansibleEnvVars));
        String additionalArgs = CloudifyPluginUtilities.expandString(envVars, this.additionalArgs);
        String startAtTask = CloudifyPluginUtilities.expandString(envVars, this.startAtTask);
        String scpExtraArgs = CloudifyPluginUtilities.expandString(envVars, this.scpExtraArgs);
        String sftpExtraArgs = CloudifyPluginUtilities.expandString(envVars, this.sftpExtraArgs);
        String sshCommonArgs = CloudifyPluginUtilities.expandString(envVars, this.sshCommonArgs);
        String sshExtraArgs = CloudifyPluginUtilities.expandString(envVars, this.sshExtraArgs);

        Map<String, Object> ansibleOpInputs = new LinkedHashMap<>();
        ansibleOpInputs.put("playbook_source_path", sourcePath);
        ansibleOpInputs.put("playbook_path", playbookPath);
        ansibleOpInputs.put("save_playbook", savePlaybook);
        ansibleOpInputs.put("remerge_sources", remergeSources);
        ansibleOpInputs.put("sources", sources);
        ansibleOpInputs.put("run_data", runData);
        ansibleOpInputs.put("sensitive_keys", sensitiveKeys);
        ansibleOpInputs.put("options_config", optionsConfig);
        ansibleOpInputs.put("ansible_env_vars", ansibleEnvVars);
        ansibleOpInputs.put("debug_level", debugLevel);
        ansibleOpInputs.put("additional_args", additionalArgs);
        ansibleOpInputs.put("start_at_task", startAtTask);
        ansibleOpInputs.put("scp_extra_args", scpExtraArgs);
        ansibleOpInputs.put("sftp_extra_args", sftpExtraArgs);
        ansibleOpInputs.put("ssh_common_args", sshCommonArgs);
        ansibleOpInputs.put("ssh_extra_args", sshExtraArgs);
        ansibleOpInputs.put("timeout", timeout);
        operationInputs.put("operation_inputs", ansibleOpInputs);

        super.performImpl(run, launcher, listener, workspace, envVars, cloudifyClient);
    }

    @Override
    protected String getIntegrationName() {
        return "ansible";
    }

    @Symbol("cfyAnsible")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.AnsibleBuildStep_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("additionalArgs", additionalArgs)
                .append("ansibleEnvVars", ansibleEnvVars)
                .append("debugLevel", debugLevel)
                .append("optionsConfig", optionsConfig)
                .append("playbookPath", playbookPath)
                .append("remergeSources", remergeSources)
                .append("runData", runData)
                .append("savePlaybook", savePlaybook)
                .append("scpExtraArgs", scpExtraArgs)
                .append("sensitiveKeys", sensitiveKeys)
                .append("sftpExtraArgs", sftpExtraArgs)
                .append("sourcePath", sourcePath)
                .append("sources", sources)
                .append("sshCommonArgs", sshCommonArgs)
                .append("sshExtraArgs", sshExtraArgs)
                .append("startAtTask", startAtTask)
                .append("timeout", timeout)
                .toString();
    }
}
