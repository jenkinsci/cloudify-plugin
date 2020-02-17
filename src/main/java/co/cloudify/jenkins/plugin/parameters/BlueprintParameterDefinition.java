package co.cloudify.jenkins.plugin.parameters;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.cloudify.jenkins.plugin.CloudifyConfiguration;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.model.Blueprint;
import co.cloudify.rest.model.ListResponse;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;

public class BlueprintParameterDefinition extends ParameterDefinition {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(BlueprintParameterDefinition.class);
	
	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.append("name", getName())
				.append("description", getDescription())
				.toString();
	}

	@DataBoundConstructor
	public BlueprintParameterDefinition(String name, String description) {
		super(name, description);
		logger.info("In DataBoundCtor; this={}", this);
	}
	
	@Exported
	public List<String> getChoices() {
		List<String> choices = new LinkedList<>();
		CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
		ListResponse<Blueprint> blueprints = cloudifyClient.getBlueprintsClient().list();
		blueprints.forEach(item -> choices.add(item.getId()));
		return choices;
	}
	
	@Override
	public ParameterValue createValue(StaplerRequest req) {
		logger.info("createValue; req={}", req);
		return null;
	}
	
	@Override
	public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
		return new StringParameterValue(jo.getString("name"), jo.getString("blueprintId"));
	}

	@Extension @Symbol({"cloudify","cloudifyBlueprintParam"})
	public static class BlueprintParameterDescriptor extends ParameterDescriptor {
		@Override
		@Nonnull
		public String getDisplayName() {
			return "Cloudify Blueprint Selector";
		}
	}
}
