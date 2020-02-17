package co.cloudify.jenkins.plugin.parameters;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.cloudify.jenkins.plugin.CloudifyConfiguration;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.model.Blueprint;
import co.cloudify.rest.model.ListResponse;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

import javax.annotation.Nonnull;

public class BlueprintParameterDefinition extends ParameterDefinition {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(BlueprintParameterDefinition.class);
	
	private	String	blueprintId;

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.append("name", getName())
				.append("description", getDescription())
				.append("blueprintId", blueprintId)
				.toString();
	}

	@DataBoundConstructor
	public BlueprintParameterDefinition(String name, String description, String blueprintId) {
		super(StringUtils.defaultIfBlank(name, "GenericName"), "GenericDescription");
		this.blueprintId = blueprintId;
		logger.info("In DataBoundCtor; this={}", this);
	}
	
	public void setBlueprintId(String blueprintId) {
		logger.info("Setting blueprintId: {}", blueprintId);
		this.blueprintId = blueprintId;
	}
	
	public String getBlueprintId() {
		logger.info("Getting blueprintId: {}", blueprintId);
		return blueprintId;
	}
	
	@Exported
	public List<String> getChoices() {
		logger.info("In getChoices");
		List<String> choices = new LinkedList<>();
		CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
		ListResponse<Blueprint> blueprints = cloudifyClient.getBlueprintsClient().list();
		blueprints.forEach(item -> choices.add(item.getId()));
		return choices;
	}
	
	@Override
	public ParameterValue createValue(StaplerRequest req) {
		logger.info("createValue; req={}", req);
		return new StringParameterValue("myname", "myvalue");
	}
	
	@Override
	public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
		logger.info("createValue; req={}, jo={}", req, jo);
		return new StringParameterValue(jo.getString("name"), jo.getString("blueprintId"));
	}

	@Extension @Symbol({"cloudify","cloudifyBlueprintParam"})
	public static class BlueprintParameterDescriptor extends ParameterDescriptor {
		@Override
		@Nonnull
		public String getDisplayName() {
			return "Cloudify Blueprint Selector";
		}
		
		public ListBoxModel doFillBlueprintIdItems() {
			logger.info("in doFillBlueprintIdItems()");
			ListBoxModel model = new ListBoxModel();
			CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
			ListResponse<Blueprint> blueprints = cloudifyClient.getBlueprintsClient().list();
			blueprints.forEach(item -> model.add(item.getId()));
			return model;
		}
	}
}
