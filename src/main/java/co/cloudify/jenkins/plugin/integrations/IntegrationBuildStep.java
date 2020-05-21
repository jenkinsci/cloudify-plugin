package co.cloudify.jenkins.plugin.integrations;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundSetter;

import co.cloudify.jenkins.plugin.BlueprintUploadSpec;
import co.cloudify.jenkins.plugin.CloudifyBuildStep;
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

    protected abstract BlueprintUploadSpec getBlueprintUploadSpec() throws Exception;

    protected File prepareBlueprintDirectory(final String blueprintResourceName) throws IOException {
        Path tempBlueprintDir = Files.createTempDirectory("cfy");
        Path blueprintPath = tempBlueprintDir.resolve("blueprint.yaml");
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream resourceAsStream = classLoader.getResourceAsStream(blueprintResourceName)) {
            Files.copy(resourceAsStream, blueprintPath);
        }

        return blueprintPath.toFile();
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
            logger.println(String.format("Blueprint '%s' doesn't exist; uploading it...", blueprintId));
            BlueprintUploadSpec uploadSpec = getBlueprintUploadSpec();
            blueprint = uploadSpec.upload(blueprintsClient, blueprintId);
        }

        String envDataLocation = CloudifyPluginUtilities.expandString(envVars, this.envDataLocation);
        CloudifyPluginUtilities.createEnvironment(
                listener, workspace, cloudifyClient,
                blueprint.getId(), deploymentId, operationInputs, envDataLocation,
                echoInputs, echoEnvData, debugOutput);
    }

    protected void putIfNonNullValue(final Map<String, Object> map, final String key, final String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    protected abstract String getIntegrationName();

    protected abstract String getIntegrationVersion();

    /**
     * @return A generated blueprint ID. May be overridden by subclasses for specialized implementations.
     */
    protected String generateBlueprintId() {
        return String.format("cfy-jenkins-%s-%s", getIntegrationName(), getIntegrationVersion());
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
