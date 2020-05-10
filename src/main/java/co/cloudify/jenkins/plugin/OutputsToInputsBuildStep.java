package co.cloudify.jenkins.plugin;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.jenkins.plugin.callables.JsonFileWriterFileCallable;
import co.cloudify.rest.client.CloudifyClient;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

/**
 * A build step for converting outputs and capabilities of one deployment, to
 * inputs of another.
 * 
 * @author Isaac Shabtay
 */
public class OutputsToInputsBuildStep extends CloudifyBuildStep {
    private String outputsLocation;
    private String mapping;
    private String mappingLocation;
    private String inputsLocation;

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

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        String inputsLocation = CloudifyPluginUtilities.expandString(envVars, this.inputsLocation);
        String outputsLocation = CloudifyPluginUtilities.expandString(envVars, this.outputsLocation);
        String mapping = CloudifyPluginUtilities.expandString(envVars, this.mapping);
        String mappingLocation = CloudifyPluginUtilities.expandString(envVars, this.mappingLocation);

        FilePath inputsFile = workspace.child(inputsLocation);
        FilePath outputsFile = workspace.child(outputsLocation);

        Map<String, Map<String, String>> mappingAsMap = CloudifyPluginUtilities.createMapping(workspace, mapping,
                mappingLocation);
        Map<String, Object> results = new HashMap<String, Object>();
        CloudifyPluginUtilities.transformOutputsFile(outputsFile, mappingAsMap, results);
        inputsFile.act(new JsonFileWriterFileCallable(CloudifyPluginUtilities.jsonFromMap(results)));
    }

    @Symbol("cfyOutputsToInputs")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        private FormValidation checkMappings(final String mapping, final String mappingLocation) {
            if (!(StringUtils.isBlank(mapping) ^ StringUtils.isBlank(mappingLocation))) {
                return FormValidation.error(
                        "Please specify either a JSON mapping, or the name of a file to contain the mapping (not both)");
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
            return Messages.OutputsToInputsBuildStep_DescriptorImpl_displayName();
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
