package co.cloudify.jenkins.plugin.integrations;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundSetter;

import co.cloudify.jenkins.plugin.BlueprintUploadSpec;
import co.cloudify.jenkins.plugin.CloudifyBuildStep;
import co.cloudify.jenkins.plugin.CloudifyPluginUtilities;
import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.client.exceptions.BlueprintNotFoundException;
import co.cloudify.rest.model.Blueprint;
import co.cloudify.rest.model.ListResponse;
import co.cloudify.rest.model.Plugin;
import hudson.AbortException;
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

    protected abstract Set<String> getRequiredPluginNames();

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
            Set<String> requiredPlugins = getRequiredPluginNames();
            if (CollectionUtils.isNotEmpty(requiredPlugins)) {
                ListResponse<Plugin> pluginsList = cloudifyClient.getPluginsClient().list();
                Set<String> pluginsSet = pluginsList.stream().map(x -> x.getPackageName()).collect(Collectors.toSet());
                requiredPlugins.removeAll(pluginsSet);
                if (!requiredPlugins.isEmpty()) {
                    logger.println("The following plugins are required in order to use this feature:");
                    requiredPlugins.forEach(x -> logger.println(x));
                    logger.println("Please ensure that the required plugin(s) are installed on Cloudify Manager and try again");
                    throw new AbortException(String.format("Missing required plugins: %s", StringUtils.join(requiredPlugins, ", ")));
                }
            }
            try (BlueprintUploadSpec uploadSpec = getBlueprintUploadSpec()) {
                blueprint = uploadSpec.upload(blueprintsClient, blueprintId);
            }
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

    protected abstract String getIntegrationVersion();

    /**
     * @return A generated blueprint ID. May be overridden by subclasses for
     *         specialized implementations.
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
