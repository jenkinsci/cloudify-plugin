package co.cloudify.jenkins.plugin.parameters;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.util.VariableResolver;
import hudson.util.VariableResolver.ByMap;
import net.sf.json.JSONObject;

public class EnvironmentParameterValue extends ParameterValue {
	private static final String DEPLOYMENT_ID = "deploymentId";
	private static final String BLUEPRINT_ID = "blueprintId";
	private static final String INPUTS = "inputs";
	
	/**	Serialization UID. */
	private static final long serialVersionUID = 1L;
	private String blueprintId;
	private String deploymentId;
	//	Ideally I'd have liked to store a JSONObject here, however this is impossible due
	//	to Jenkins security hardening:
	//
	//		Refusing to marshal net.sf.json.JSONObject for security reasons; see https://jenkins.io/redirect/class-filter/
	private String inputs;
	
	@DataBoundConstructor
	public EnvironmentParameterValue(String name, String blueprintId, String deploymentId, String inputs) {
		super(name);
		this.blueprintId = blueprintId;
		this.deploymentId = deploymentId;
		this.inputs = inputs;
	}
	
	@Override
	public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
		Map<String, Object> map = new HashMap<>();
		map.put(BLUEPRINT_ID, blueprintId);
		map.put(DEPLOYMENT_ID, deploymentId);
		map.put(INPUTS, inputs);
		return new ByMap<String>(Collections.singletonMap(getName(), JSONObject.fromObject(map).toString()));
	}

	public static String getBlueprintId(JSONObject json) {
		return json.getString(BLUEPRINT_ID);
	}
	
	public static String getDeploymentId(JSONObject json) {
		return json.getString(DEPLOYMENT_ID);
	}
	
	public static Map<String, Object> getInputs(JSONObject json) {
		return JSONObject.fromObject(json.getString(INPUTS));
	}
	
	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append(BLUEPRINT_ID, blueprintId)
				.append(DEPLOYMENT_ID, deploymentId)
				.append(INPUTS, inputs)
				.toString();
	}
}
