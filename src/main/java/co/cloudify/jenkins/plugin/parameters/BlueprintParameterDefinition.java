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
import net.sf.json.JSONObject;

public class BlueprintParameterDefinition extends ParameterDefinition {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(BlueprintParameterDefinition.class);
	
	private	String inputs;
	
	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("name", getName())
				.append("description", getDescription())
				.append("inputs", getInputs())
				.toString();
	}

	public String getInputs() {
		return inputs;
	}
	
	@DataBoundConstructor
	public BlueprintParameterDefinition(String name, String description, String inputs) {
		super(name, description);
		this.inputs = inputs;
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
		logger.info("createValue; req={}, jo={}", req, jo);
		String name = jo.getString("name");
		String blueprintId = jo.getString("blueprintId");
		String inputs = jo.getString("inputs");
		EnvironmentParameterValue value = new EnvironmentParameterValue(name, blueprintId, inputs);
		logger.info("Returning: {}", value);
		return value;
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
