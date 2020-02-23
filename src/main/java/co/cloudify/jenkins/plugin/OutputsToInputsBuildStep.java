package co.cloudify.jenkins.plugin;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.CloudifyClient;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;

public class OutputsToInputsBuildStep extends CloudifyBuildStep {
	private String	outputsLocation;
	private String	mapping;
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
	
	protected void transform(JSONObject mapping, JSONObject inputs, JSONObject source) {
		for (Map.Entry<String, String> entry: (Set<Map.Entry<String, String>>) mapping.entrySet()) {
			String from = entry.getKey();
			String to = entry.getValue();
			inputs.put(to, source.get(from));
		}
	}
	
	@Override
	protected void perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener,
	        CloudifyClient cloudifyClient) throws Exception {
		FilePath workspace = build.getWorkspace();
		FilePath inputsFile = workspace.child(inputsLocation);
		FilePath outputsFile = workspace.child(outputsLocation);
		
		JSONObject mappingJson = JSONObject.fromObject(mapping);
		JSONObject outputsJson;
		
		try (InputStream is = outputsFile.read()) {
			outputsJson = JSONObject.fromObject(IOUtils.toString(is, StandardCharsets.UTF_8));
		}
		JSONObject outputs = outputsJson.getJSONObject("outputs");
		JSONObject capabilities = outputsJson.getJSONObject("capabilities");
		JSONObject outputMap = mappingJson.getJSONObject("outputs");
		JSONObject capsMap = mappingJson.getJSONObject("capabilities");
		
		JSONObject inputs = new JSONObject();
		transform(outputMap, inputs, outputs);
		transform(capsMap, inputs, capabilities);
		
		try (OutputStreamWriter osw = new OutputStreamWriter(
				inputsFile.write())) {
			osw.write(inputs.toString(4));
		}
	}
	
	@Symbol("cfyOutputsToInputs")
	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public FormValidation doCheckOutputsLocation(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
		public FormValidation doCheckMapping(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
		public FormValidation doCheckInputsLocation(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
		
        @Override
		public String getDisplayName() {
			return "Convert Cloudify outputs/caps to inputs";
		}
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.appendSuper(super.toString())
				.append("outputsLocation", outputsLocation)
				.append("mapping", mapping)
				.append("inputsLocation", inputsLocation)
				.toString();
	}
}
