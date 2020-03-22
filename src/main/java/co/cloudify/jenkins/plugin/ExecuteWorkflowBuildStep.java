package co.cloudify.jenkins.plugin;

import java.io.PrintStream;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.helpers.ExecutionFollowCallback;
import co.cloudify.rest.helpers.ExecutionsHelper;
import co.cloudify.rest.model.Execution;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

/**
 * A build step for executing a Cloudify workflow.
 * 
 * @author Isaac Shabtay
 */
public class ExecuteWorkflowBuildStep extends CloudifyBuildStep {
    private String deploymentId;
    private String workflowId;
    private String executionParameters;
    private boolean waitForCompletion = true;
    private boolean printLogs = true;
    private boolean debugOutput;

    @DataBoundConstructor
    public ExecuteWorkflowBuildStep() {
        super();
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    @DataBoundSetter
    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    @DataBoundSetter
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getExecutionParameters() {
        return executionParameters;
    }

    @DataBoundSetter
    public void setExecutionParameters(String executionParameters) {
        this.executionParameters = executionParameters;
    }

    public boolean isWaitForCompletion() {
        return waitForCompletion;
    }

    @DataBoundSetter
    public void setWaitForCompletion(boolean waitForCompletion) {
        this.waitForCompletion = waitForCompletion;
    }

    public boolean isPrintLogs() {
        return printLogs;
    }

    @DataBoundSetter
    public void setPrintLogs(boolean printLogs) {
        this.printLogs = printLogs;
    }

    public boolean isDebugOutput() {
        return debugOutput;
    }

    @DataBoundSetter
    public void setDebugOutput(boolean debugOutput) {
        this.debugOutput = debugOutput;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        String deploymentId = expandString(envVars, this.deploymentId);
        String workflowId = expandString(envVars, this.workflowId);
        String executionParameters = expandString(envVars, this.executionParameters);

        PrintStream jenkinsLog = listener.getLogger();

        Map<String, Object> executionParametersAsMap = null;
        if (executionParameters != null) {
            executionParametersAsMap = CloudifyPluginUtilities.readYamlOrJson(executionParameters);
        }

        Execution execution = cloudifyClient.getExecutionsClient().start(deploymentId, workflowId,
                executionParametersAsMap);
        jenkinsLog.println(String.format("Execution started; id=%s", execution.getId()));

        if (waitForCompletion || printLogs) {
            jenkinsLog.println("Waiting for execution to end...");
            ExecutionFollowCallback callback = CloudifyPluginUtilities.getExecutionFollowCallback(printLogs,
                    debugOutput, cloudifyClient, jenkinsLog);
            execution = ExecutionsHelper.followExecution(cloudifyClient, execution, callback);
            ExecutionsHelper.validate(execution, "Execution did not end successfully");
            jenkinsLog.println("Execution ended successfully");
        }
    }

    @Symbol("executeCloudifyWorkflow")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheckDeploymentId(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckWorkflowId(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        @Override
        public String getDisplayName() {
            return Messages.ExecuteWorkflowBuildStep_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("deploymentId", deploymentId)
                .append("workflowId", workflowId)
                .append("executionParametrs", executionParameters)
                .append("waitForCompletion", waitForCompletion)
                .append("printLogs", printLogs)
                .append("debugOutput", debugOutput)
                .toString();
    }
}
