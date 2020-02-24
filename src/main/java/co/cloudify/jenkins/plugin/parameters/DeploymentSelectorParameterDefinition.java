package co.cloudify.jenkins.plugin.parameters;

import java.util.LinkedList;
import java.util.List;

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
import co.cloudify.rest.model.ListResponse;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;

public class DeploymentSelectorParameterDefinition extends ParameterDefinition {
	private static final long serialVersionUID = 1L;
	
	private	String deploymentId;
	
	@DataBoundConstructor
	public DeploymentSelectorParameterDefinition(String name, String description) {
		super(name, description);
	}
	
	public String getDeploymentId() {
		return deploymentId;
	}

	@DataBoundSetter
	public void setDeploymentId(String deploymentId) {
		this.deploymentId = deploymentId;
	}
	
	@Exported
	public List<String> getChoices() {
		List<String> choices = new LinkedList<>();
		CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
		ListResponse<Deployment> deployments = cloudifyClient.getDeploymentsClient().list();
		deployments.forEach(item -> choices.add(item.getId()));
		return choices;
	}
	
	@Override
	public ParameterValue createValue(StaplerRequest req) {
		return null;
	}
	
	@Override
	public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
		String name = jo.getString("name");
		String deploymentId = jo.getString("deploymentId");
		return new StringParameterValue(name, deploymentId);
	}

	@Extension @Symbol({"cloudify","cloudifyDeploymentParam"})
	public static class DeploymentSelectorParameterDescriptor extends ParameterDescriptor {
		@Override
		@Nonnull
		public String getDisplayName() {
			return "Cloudify Deployment Selector";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("deploymentId", deploymentId)
				.toString();
	}
}
