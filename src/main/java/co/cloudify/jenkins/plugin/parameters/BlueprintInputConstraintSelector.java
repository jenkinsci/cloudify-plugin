package co.cloudify.jenkins.plugin.parameters;

import java.util.List;
import java.util.Map;
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
import co.cloudify.rest.model.Blueprint;
import co.cloudify.rest.model.BlueprintInput;
import co.cloudify.rest.model.ConstraintType;
import co.cloudify.rest.model.InputConstraint;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;

public class BlueprintInputConstraintSelector extends ParameterDefinition{
	private static final long serialVersionUID = 1L;
	
	private	String blueprintId;
	private String inputName;
	private String selectedValue;
	
	@DataBoundConstructor
	public BlueprintInputConstraintSelector(String name, String description) {
		super(name, description);
	}
	
	public String getBlueprintId() {
		return blueprintId;
	}

	@DataBoundSetter
	public void setBlueprintId(String blueprintId) {
		this.blueprintId = blueprintId;
	}
	
	public String getInputName() {
		return inputName;
	}
	
	@DataBoundSetter
	public void setInputName(String inputName) {
		this.inputName = inputName;
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
		CloudifyClient client = CloudifyConfiguration.getCloudifyClient();
		Blueprint blueprint = client.getBlueprintsClient().get(blueprintId);
		Map<String, BlueprintInput> inputs = blueprint.getPlan().getInputs();
		BlueprintInput bpInput = inputs.get(inputName);
		if (bpInput == null) {
			throw new IllegalArgumentException(String.format("Blueprint '%s' has no input named '%s'", blueprintId, inputName));
		}
		List<InputConstraint> constraints = bpInput.getConstraints();
		List<InputConstraint> relevant = constraints
				.stream()
				.filter(x -> x.getType() == ConstraintType.valid_values)
				.collect(Collectors.toList());
		if (relevant.size() != 1) {
			throw new IllegalArgumentException(
					String.format(
							"Input '%' of blueprint '%s' contains %d constraints of type '%s'; expected exactly one",
							inputName, blueprintId, relevant.size(), ConstraintType.valid_values));
		}
		List<Object> options = (List<Object>) relevant.get(0).getValue();
		return options
				.stream()
				.map(x -> x.toString())
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

	@Extension @Symbol("cloudifyInputConstraintValueSelector")
	public static class BlueprintInputConstraintParameterDescriptor extends ParameterDescriptor {
		@Override
		@Nonnull
		public String getDisplayName() {
			return "Cloudify Constrained Input Value Selector";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("blueprintId", blueprintId)
				.append("inputName", inputName)
				.append("selectedValue", selectedValue)
				.toString();
	}
}
