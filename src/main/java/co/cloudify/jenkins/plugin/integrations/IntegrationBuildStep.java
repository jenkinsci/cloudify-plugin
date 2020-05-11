package co.cloudify.jenkins.plugin.integrations;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundSetter;

import co.cloudify.jenkins.plugin.BlueprintUploadSpec;
import co.cloudify.jenkins.plugin.CloudifyBuildStep;
import co.cloudify.jenkins.plugin.CloudifyEnvironmentData;
import co.cloudify.jenkins.plugin.CloudifyPluginUtilities;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.model.Blueprint;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

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
        if (StringUtils.isBlank(deploymentId)) {
            deploymentId = generateDeploymentId(blueprintId);
        }
        String envDataLocation = CloudifyPluginUtilities.expandString(envVars, this.envDataLocation);

        BlueprintUploadSpec uploadSpec = getBlueprintUploadSpec();
        Blueprint blueprint = uploadSpec.upload(cloudifyClient.getBlueprintsClient(), blueprintId);
        CloudifyEnvironmentData envData = CloudifyPluginUtilities.createEnvironment(
                listener, workspace, cloudifyClient,
                blueprint.getId(), deploymentId, operationInputs, envDataLocation,
                echoInputs, echoEnvData, debugOutput);
        JsonObject dataJsonObject = envData.toJson();
    }

    protected void putIfNonNullValue(final Map<String, Object> map, final String key, final String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    protected abstract String getIntegrationName();

    protected abstract BlueprintUploadSpec getBlueprintUploadSpec();

    /**
     * @return A generated blueprint ID. May be overridden by subclasses for specialized implementations.
     */
    protected String generateBlueprintId() {
        return String.format("%s-%s", getIntegrationName(), Long.toHexString(new Date().getTime()));
    }

    /**
     * @return A generated deployment ID. May be overridden by subclasses for specialized implementations.
     */
    protected String generateDeploymentId(final String blueprintId) {
        return blueprintId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("deploymentId", deploymentId)
                .append("debugOutput", debugOutput)
                .append("echoInputs", echoInputs)
                .append("echoEnvData", echoEnvData)
                .append("inputs", operationInputs)
                .append("outputsLocation", envDataLocation)
                .toString();
    }
}
