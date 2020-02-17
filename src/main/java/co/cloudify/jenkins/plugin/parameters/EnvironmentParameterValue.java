package co.cloudify.jenkins.plugin.parameters;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Label;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.model.queue.SubTask;
import hudson.tasks.BuildWrapper;
import hudson.util.VariableResolver;
import net.sf.json.JSONObject;

public class EnvironmentParameterValue extends ParameterValue {
	private String blueprintId;
	//	Ideally I'd have liked to store a JSONObject here, however this is impossible due
	//	to Jenkins security hardening:
	//
	//		Refusing to marshal net.sf.json.JSONObject for security reasons; see https://jenkins.io/redirect/class-filter/
	private String inputs;
	
	private VariableResolver<String> variableResolver;
	
	@DataBoundConstructor
	public EnvironmentParameterValue(String name, String blueprintId, String inputs) {
		super(name);
		this.blueprintId = blueprintId;
		this.inputs = inputs;
		
		variableResolver = new VariableResolver<String>() {
			@Override
			public String resolve(String name) {
				Map<String, Object> map = new HashMap<>();
				map.put("blueprintId", blueprintId);
				map.put("inputs", inputs);
				return JSONObject.fromObject(map).toString();
			}
		};
	}
	
	@Override
	public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
		return variableResolver;
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
