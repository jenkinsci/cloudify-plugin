package co.cloudify.jenkins.plugin.integrations;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Predicate;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundSetter;

import co.cloudify.jenkins.plugin.BlueprintUploadSpec;
import co.cloudify.jenkins.plugin.CloudifyBuildStep;
import co.cloudify.jenkins.plugin.CloudifyConfiguration;
import co.cloudify.jenkins.plugin.CloudifyPluginUtilities;
import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.client.exceptions.BlueprintNotFoundException;
import co.cloudify.rest.model.Blueprint;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * This is a base class for integration points into the Cloudify Jenkins plugin.
 * 
 * @author Isaac Shabtay
 */
public abstract class IntegrationBuildStep extends CloudifyBuildStep {
    private String deploymentId;
    private boolean echoInputs;
    private boolean echoEnvData;
    private boolean debugOutput;
    protected String envDataLocation;
    protected Map<String, Object> operationInputs = new LinkedHashMap<String, Object>();
    /** Predicate to determine whether an input should be printed out, or be masked. */
    protected Predicate<String> inputPrintPredicate;

    protected static final ResourceBundle INTEGRATION_BUNDLE = ResourceBundle.getBundle("integration");

    public boolean isEchoInputs() {
        return echoInputs;
    }

    @DataBoundSetter
    public void setEchoInputs(boolean echoInputs) {
        this.echoInputs = echoInputs;
    }

    public boolean isEchoEnvData() {
        return echoEnvData;
    }

    @DataBoundSetter
    public void setEchoEnvData(boolean echoOutputs) {
        this.echoEnvData = echoOutputs;
    }

    public boolean isDebugOutput() {
        return debugOutput;
    }

    @DataBoundSetter
    public void setDebugOutput(boolean debugOutput) {
        this.debugOutput = debugOutput;
    }

    public String getEnvDataLocation() {
        return envDataLocation;
    }

    @DataBoundSetter
    public void setEnvDataLocation(String outputsLocation) {
        this.envDataLocation = outputsLocation;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    @DataBoundSetter
    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    protected BlueprintUploadSpec getBlueprintUploadSpec() throws Exception {
        return new BlueprintUploadSpec(
                CloudifyConfiguration.get().getIntegrationBlueprintsArchiveUrl(),
                INTEGRATION_BUNDLE.getString(String.format("integration.%s.blueprint-file", getIntegrationName())));
    }

    @Override
    protected void performImpl(Run<?, ?> run, Launcher launcher, TaskListener listener, FilePath workspace,
            EnvVars envVars, CloudifyClient cloudifyClient) throws Exception {
        PrintStream logger = listener.getLogger();

        String blueprintId = generateBlueprintId();
        String deploymentId = CloudifyPluginUtilities.expandString(envVars, this.deploymentId);

        BlueprintsClient blueprintsClient = cloudifyClient.getBlueprintsClient();
        Blueprint blueprint;
        try {
            logger.println(String.format("Loading blueprint: %s", blueprintId));
            blueprint = blueprintsClient.get(blueprintId);
        } catch (BlueprintNotFoundException ex) {
            logger.println(String.format("Blueprint '%s' doesn't exist; will try to upload it", blueprintId));
            try (BlueprintUploadSpec uploadSpec = getBlueprintUploadSpec()) {
                blueprint = uploadSpec.upload(blueprintsClient, blueprintId);
            }
            logger.println(String.format("Blueprint '%s' uploaded", blueprintId));
        }

        String envDataLocation = CloudifyPluginUtilities.expandString(envVars, this.envDataLocation);
        CloudifyPluginUtilities.createEnvironment(listener, workspace, cloudifyClient, blueprint.getId(), deploymentId,
                operationInputs, envDataLocation, false, echoInputs, echoEnvData, debugOutput,
                inputPrintPredicate != null ? inputPrintPredicate : x -> true);
    }

    protected void putIfNonNullValue(final Map<String, Object> map, final String key, final Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    protected abstract String getIntegrationName();

    /**
     * @return A generated blueprint ID. May be overridden by subclasses for
     *         specialized implementations.
     */
    protected String generateBlueprintId() {
        return INTEGRATION_BUNDLE.getString(String.format("integration.%s.blueprint-id", getIntegrationName()));
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("deploymentId", deploymentId)
                .append("debugOutput", debugOutput)
                .append("echoInputs", echoInputs)
                .append("echoEnvData", echoEnvData)
                .append("operationInputs", operationInputs)
                .append("envDataLocation", envDataLocation)
                .toString();
    }
}
