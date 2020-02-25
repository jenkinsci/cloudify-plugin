package co.cloudify.jenkins.plugin.parameters;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import co.cloudify.jenkins.plugin.CloudifyConfiguration;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.model.Blueprint;
import co.cloudify.rest.model.ListResponse;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;

public class BlueprintSelectorParameterDefinition extends ParameterDefinition {
	private static final long serialVersionUID = 1L;
	
	private	String blueprintId;
	private String filter;
	
	@DataBoundConstructor
	public BlueprintSelectorParameterDefinition(String name, String description) {
		super(name, description);
	}
	
	public String getBlueprintId() {
		return blueprintId;
	}

	@DataBoundSetter
	public void setBlueprintId(String blueprintId) {
		this.blueprintId = blueprintId;
	}
	
	public String getFilter() {
		return filter;
	}
	
	@DataBoundSetter
	public void setFilter(String filter) {
		this.filter = filter;
	}
	
	@Exported
	public List<String> getChoices() {
		String effectiveFilter = StringUtils.trimToNull(filter);
		CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
		ListResponse<Blueprint> blueprints = cloudifyClient.getBlueprintsClient().list(effectiveFilter);
		return blueprints
				.stream()
				.map(x -> x.getId())
				.collect(Collectors.toList());
	}
	
	@Override
	public ParameterValue createValue(StaplerRequest req) {
		return null;
	}
	
	@Override
	public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
		String name = jo.getString("name");
		String blueprintId = jo.getString("blueprintId");
		return new StringParameterValue(name, blueprintId);
	}

	@Extension @Symbol("cloudifyBlueprintParam")
	public static class BlueprintSelectorParameterDescriptor extends ParameterDescriptor {
		@Override
		@Nonnull
		public String getDisplayName() {
			return "Cloudify Blueprint Selector";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("blueprintId", blueprintId)
				.append("filter", filter)
				.toString();
	}
}
