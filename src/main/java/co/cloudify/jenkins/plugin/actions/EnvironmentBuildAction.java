package co.cloudify.jenkins.plugin.actions;

import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;

import co.cloudify.jenkins.plugin.CloudifyEnvironmentData;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class EnvironmentBuildAction implements RunAction2 {
    private transient Run<?, ?> run;

    private String blueprintId;
    private String deploymentId;
    private Map<String, Object> inputs;
    private Map<String, Object> outputs;
    private Map<String, Object> capabilities;

    public EnvironmentBuildAction() {
        super();
    }

    public String getBlueprintId() {
        return blueprintId;
    }

    public void setBlueprintId(String blueprintId) {
        this.blueprintId = blueprintId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, Object> outputs) {
        this.outputs = outputs;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, Object> capabilities) {
        this.capabilities = capabilities;
    }

    public void applyEnvironmentData(final CloudifyEnvironmentData envData) {
        setInputs(envData.getDeployment().getInputs());
        setOutputs(envData.getOutputs());
        setCapabilities(envData.getCapabilities());
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
    }

    @SuppressWarnings("rawtypes")
    public Run getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        return "document.png";
    }

    @Override
    public String getDisplayName() {
        return "Cloudify";
    }

    @Override
    public String getUrlName() {
        return "cloudify";
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("blueprintId", blueprintId)
                .append("deploymentId", deploymentId)
                .append("inputs", inputs)
                .append("outputs", outputs)
                .append("capabilities", capabilities)
                .toString();
    }
}
