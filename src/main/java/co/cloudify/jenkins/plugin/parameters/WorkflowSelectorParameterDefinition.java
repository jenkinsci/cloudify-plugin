package co.cloudify.jenkins.plugin.parameters;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import co.cloudify.jenkins.plugin.CloudifyConfiguration;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.model.Deployment;
import co.cloudify.rest.model.Workflow;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;

/**
 * A parameter definition that renders a list of all available
 * workflows for a given deployment.
 * 
 * @author Isaac Shabtay
 */
public class WorkflowSelectorParameterDefinition extends ParameterDefinition {
    private static final long serialVersionUID = 1L;

    private String deploymentId;
    private String selectedValue;

    @DataBoundConstructor
    public WorkflowSelectorParameterDefinition(String name, String description) {
        super(name, description);
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    @DataBoundSetter
    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getSelectedValue() {
        return selectedValue;
    }

    @DataBoundSetter
    public void setSelectedValue(String selectedValue) {
        this.selectedValue = selectedValue;
    }

    @Exported
    public List<String> getChoices() {
        CloudifyClient client = CloudifyConfiguration.getCloudifyClient(null, null, null);
        Deployment deployment = client.getDeploymentsClient().get(deploymentId);
        List<Workflow> workflows = deployment.getWorkflows();
        return workflows
                .stream()
                .map(Workflow::getName)
                .collect(Collectors.toList());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        return null;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        String name = jo.getString("name");
        String value = jo.getString("selectedValue");
        return new StringParameterValue(name, value);
    }

    @Extension
    @Symbol("cloudifyWorkflowSelector")
    public static class WorkflowSelectorParameterDescriptor extends ParameterDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return Messages.WorkflowSelectorParameterDefinition_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("deploymentId", deploymentId)
                .append("selectedValue", selectedValue)
                .toString();
    }
}
