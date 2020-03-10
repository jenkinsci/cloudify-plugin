package co.cloudify.jenkins.plugin;

import java.io.Serializable;
import java.util.Map;

import co.cloudify.rest.model.Deployment;

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
}
