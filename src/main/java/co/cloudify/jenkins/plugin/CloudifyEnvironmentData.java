package co.cloudify.jenkins.plugin;

import java.io.Serializable;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.commons.lang3.builder.ToStringBuilder;

import co.cloudify.rest.model.Deployment;

/**
 * Encapsulates data pertaining to a Cloudify environment created by various
 * build steps.
 * 
 * @author Isaac Shabtay
 */
public class CloudifyEnvironmentData implements Serializable {
    /** Serialization UID. */
    private static final long serialVersionUID = 1L;

    private Deployment deployment;
    private Map<String, Object> outputs;
    private Map<String, Object> capabilities;

    public CloudifyEnvironmentData(final Deployment deployment,
            final Map<String, Object> outputs,
            final Map<String, Object> capabilities) {
        this.deployment = deployment;
        this.outputs = outputs;
        this.capabilities = capabilities;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    // TODO: this requires the JSON object to be in memory. Perhaps a better
    // way would be to make this class a JAXB-bound type and marshal it
    // using JAXB?
    public JsonObject toJson() {
        JsonObject contents = Json.createObjectBuilder()
                .add("deployment",
                        Json.createObjectBuilder().add("id", deployment.getId()))
                .add("outputs", CloudifyPluginUtilities.jsonFromMap(outputs))
                .add("capabilities", CloudifyPluginUtilities.jsonFromMap(capabilities))
                .build();
        return contents;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("deployment", deployment)
                .append("outputs", outputs)
                .append("capabilities", capabilities)
                .toString();
    }
}
