package co.cloudify.jenkins.plugin.parameters;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.model.ParameterValue;
import net.sf.json.JSONObject;

public class EnvironmentParameterValue extends ParameterValue {
	private String blueprintId;
	//	Ideally I'd have liked to store a JSONObject here, however this is impossible due
	//	to Jenkins security hardening:
	//
	//		Refusing to marshal net.sf.json.JSONObject for security reasons; see https://jenkins.io/redirect/class-filter/
	private String inputs;
	
	@DataBoundConstructor
	public EnvironmentParameterValue(String name, String blueprintId, String inputs) {
		super(name);
		this.blueprintId = blueprintId;
		this.inputs = inputs;
	}
	
	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("blueprintId", blueprintId)
				.append("inputs", inputs)
				.toString();
	}
}
