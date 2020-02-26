package co.cloudify.jenkins.plugin;

import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.CloudifyClient;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import net.sf.json.JSONObject;

/**
 * A build step for converting outputs and capabilities of one deployment,
 * to inputs of another.
 * 
 * @author	Isaac Shabtay
 */
public class OutputsToInputsBuildStep extends CloudifyBuildStep {
	private String	outputsLocation;
	private String	mapping;
	private	String	mappingLocation;
	private	String	inputsLocation;
	
	@DataBoundConstructor
	public OutputsToInputsBuildStep() {
		super();
	}
	
	public String getOutputsLocation() {
		return outputsLocation;
	}

	@DataBoundSetter
	public void setMapping(String mapping) {
		this.mapping = mapping;
	}
	
	public String getMapping() {
		return mapping;
	}
	
	@DataBoundSetter
	public void setMappingLocation(String mappingLocation) {
		this.mappingLocation = mappingLocation;
	}
	
	public String getMappingLocation() {
		return mappingLocation;
	}
	
	@DataBoundSetter
	public void setOutputsLocation(String outputsLocation) {
		this.outputsLocation = outputsLocation;
	}
	
	public String getInputsLocation() {
		return inputsLocation;
	}
	
	@DataBoundSetter
	public void setInputsLocation(String inputsLocation) {
		this.inputsLocation = inputsLocation;
	}
	
	private void transform(JSONObject mapping, JSONObject inputs, JSONObject source) {
		for (Map.Entry<String, String> entry: (Set<Map.Entry<String, String>>) mapping.entrySet()) {
			String from = entry.getKey();
			String to = entry.getValue();
			inputs.put(to, source.get(from));
		}
	}
	
	@Override
	protected void performImpl(Run<?, ?> run, Launcher launcher, TaskListener listener, FilePath workspace,
	        CloudifyClient cloudifyClient) throws Exception {
		EnvVars env = run.getEnvironment(listener);
		VariableResolver<String> resolver = new VariableResolver.ByMap<String>(env);

		String inputsLocation = Util.replaceMacro(this.inputsLocation, resolver);
		String outputsLocation = Util.replaceMacro(this.outputsLocation, resolver);
		String mapping = Util.replaceMacro(this.mapping, resolver);
		String mappingLocation = Util.replaceMacro(this.mappingLocation, resolver);
		
		PrintStream logger = listener.getLogger();
		FilePath inputsFile = workspace.child(inputsLocation);
		FilePath outputsFile = workspace.child(outputsLocation);
		
		JSONObject mappingJson;
		if (StringUtils.isNotBlank(mapping)) {
			mappingJson = JSONObject.fromObject(mapping);
		} else {
			logger.println(String.format("Reading inputs mapping from %s", mappingLocation));
			mappingJson = CloudifyPluginUtilities.readJson(workspace.child(mappingLocation));
		}
		JSONObject outputsJson = CloudifyPluginUtilities.readJson(outputsFile);
		JSONObject outputs = outputsJson.getJSONObject("outputs");
		JSONObject capabilities = outputsJson.getJSONObject("capabilities");
		JSONObject outputMap = mappingJson.getJSONObject("outputs");
		JSONObject capsMap = mappingJson.getJSONObject("capabilities");
		
		JSONObject inputs = new JSONObject();
		transform(outputMap, inputs, outputs);
		transform(capsMap, inputs, capabilities);
		CloudifyPluginUtilities.writeJson(inputs, inputsFile);
	}
	
	@Symbol("cfyOutputsToInputs")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		private FormValidation checkMappings(final String mapping, final String mappingLocation) {
			if (!(StringUtils.isBlank(mapping) ^ StringUtils.isBlank(mappingLocation))) {
				return FormValidation.error("Please specify either a JSON mapping, or the name of a file to contain the mapping (not both)");
			}
			return FormValidation.ok();
		}
		
		public FormValidation doCheckOutputsLocation(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
		public FormValidation doCheckMapping(@QueryParameter String value, @QueryParameter String mappingLocation) {
			return checkMappings(value, mappingLocation);
		}
		
		public FormValidation doCheckMappingLocation(@QueryParameter String value, @QueryParameter String mapping) {
			return checkMappings(mapping, value);
		}
		
		public FormValidation doCheckInputsLocation(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
        @Override
		public String getDisplayName() {
			return "Convert Cloudify Environment Outputs/Capabilities to Inputs";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("outputsLocation", outputsLocation)
				.append("mapping", mapping)
				.append("mappingLocation", mappingLocation)
				.append("inputsLocation", inputsLocation)
				.toString();
	}
}
