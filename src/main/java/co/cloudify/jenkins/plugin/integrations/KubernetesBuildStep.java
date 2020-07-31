package co.cloudify.jenkins.plugin.integrations;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.common.IdCredentials;

import co.cloudify.jenkins.plugin.BlueprintUploadSpec;
import co.cloudify.jenkins.plugin.CloudifyPluginUtilities;
import co.cloudify.jenkins.plugin.Messages;
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
import net.sf.json.JSONObject;

/**
 * A build step for creating Kubernetes resources.
 * 
 * @author Isaac Shabtay
 */
public class KubernetesBuildStep extends IntegrationBuildStep {
    private static final String INPUT_CLIENT_CONFIG = "client_config";
    private static final String INPUT_DEFINITION = "definition";
    private static final String INPUT_OPTIONS = "options";
    private static final String INPUT_VALIDATE_STATUS = "validate_status";
    private static final String INPUT_ALLOW_NODE_REDEFINITION = "allow_node_redefinition";

    private String gcpCredentialsId;
    private String gcpCredentialsFile;
    private String k8sMaster;
    private String apiOptionsAsString;
    private String apiOptionsFile;
    private Map<String, Object> apiOptions;
    private String definitionAsString;
    private String definitionFile;
    private Map<String, Object> definition;
    private String optionsAsString;
    private String optionsFile;
    private Map<String, Object> options;
    private boolean validateStatus = true;
    private boolean allowNodeRedefinition = false;

    @DataBoundConstructor
    public KubernetesBuildStep() {
        super();
    }

    public String getGcpCredentialsId() {
        return gcpCredentialsId;
    }

    @DataBoundSetter
    public void setGcpCredentialsId(String gcpCredentialsId) {
        this.gcpCredentialsId = gcpCredentialsId;
    }

    public String getGcpCredentialsFile() {
        return gcpCredentialsFile;
    }

    @DataBoundSetter
    public void setGcpCredentialsFile(String gcpCredentialsFile) {
        this.gcpCredentialsFile = gcpCredentialsFile;
    }

    public String getK8sMaster() {
        return k8sMaster;
    }

    @DataBoundSetter
    public void setK8sMaster(String k8sMaster) {
        this.k8sMaster = k8sMaster;
    }

    public String getApiOptionsAsString() {
        return apiOptionsAsString;
    }

    @DataBoundSetter
    public void setApiOptionsAsString(String apiOptionsAsString) {
        this.apiOptionsAsString = apiOptionsAsString;
    }

    public String getApiOptionsFile() {
        return apiOptionsFile;
    }

    @DataBoundSetter
    public void setApiOptionsFile(String apiOptionsFile) {
        this.apiOptionsFile = apiOptionsFile;
    }

    public Map<String, Object> getApiOptions() {
        return apiOptions;
    }

    @DataBoundSetter
    public void setApiOptions(Map<String, Object> apiOptions) {
        this.apiOptions = apiOptions;
    }

    public String getDefinitionAsString() {
        return definitionAsString;
    }

    @DataBoundSetter
    public void setDefinitionAsString(String definitionAsString) {
        this.definitionAsString = definitionAsString;
    }

    public String getDefinitionFile() {
        return definitionFile;
    }

    @DataBoundSetter
    public void setDefinitionFile(String definitionFile) {
        this.definitionFile = definitionFile;
    }

    public Map<String, Object> getDefinition() {
        return definition;
    }

    @DataBoundSetter
    public void setDefinition(Map<String, Object> definition) {
        this.definition = definition;
    }

    public String getOptionsAsString() {
        return optionsAsString;
    }

    @DataBoundSetter
    public void setOptionsAsString(String optionsAsString) {
        this.optionsAsString = optionsAsString;
    }

    public String getOptionsFile() {
        return optionsFile;
    }

    @DataBoundSetter
    public void setOptionsFile(String optionsFile) {
        this.optionsFile = optionsFile;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    @DataBoundSetter
    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public boolean isValidateStatus() {
        return validateStatus;
    }

    @DataBoundSetter
    public void setValidateStatus(boolean validateStatus) {
        this.validateStatus = validateStatus;
    }

    public boolean isAllowNodeRedefinition() {
        return allowNodeRedefinition;
    }

    @DataBoundSetter
    public void setAllowNodeRedefinition(boolean allowNodeRedefinition) {
        this.allowNodeRedefinition = allowNodeRedefinition;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace, final EnvVars envVars, final CloudifyClient cloudifyClient) throws Exception {
        String gcpCredentialsId = CloudifyPluginUtilities.expandString(envVars, this.gcpCredentialsId);
        String gcpCredentialsFile = CloudifyPluginUtilities.expandString(envVars, this.gcpCredentialsFile);
        String apiOptionsAsString = CloudifyPluginUtilities.expandString(envVars, this.apiOptionsAsString);
        String apiOptionsFile = CloudifyPluginUtilities.expandString(envVars, this.apiOptionsFile);
        String k8sMaster = CloudifyPluginUtilities.expandString(envVars, this.k8sMaster);
        String definitionAsString = CloudifyPluginUtilities.expandString(envVars, this.definitionAsString);
        String definitionFile = CloudifyPluginUtilities.expandString(envVars, this.definitionFile);
        String optionsAsString = CloudifyPluginUtilities.expandString(envVars, this.optionsAsString);
        String optionsFile = CloudifyPluginUtilities.expandString(envVars, this.optionsFile);

        Map<String, Object> apiOptionsMap = CloudifyPluginUtilities.getCombinedMap(workspace, apiOptionsFile,
                apiOptionsAsString,
                this.apiOptions);
        Map<String, Object> definitionMap = CloudifyPluginUtilities.getCombinedMap(workspace, definitionFile,
                definitionAsString, this.definition);
        Map<String, Object> optionsMap = CloudifyPluginUtilities.getCombinedMap(workspace, optionsFile,
                optionsAsString, this.options);

        // Prepare the Client Config.

        Map<String, Object> clientConfig = new HashMap<>();

        Map<String, Object> gcpCredentials = null;
        if (gcpCredentialsId != null) {
            IdCredentials gcpIdCredentials = CloudifyPluginUtilities.getCredentials(gcpCredentialsId,
                    IdCredentials.class, run);
            if (gcpIdCredentials == null) {
                throw new IllegalArgumentException(String.format("Credentials not found: %s", gcpIdCredentials));
            }
            if (gcpIdCredentials instanceof StringCredentials) {
                gcpCredentials = JSONObject
                        .fromObject(((StringCredentials) gcpIdCredentials).getSecret().getPlainText());
            } else if (gcpIdCredentials instanceof FileCredentials) {
                try (InputStream is = ((FileCredentials) gcpIdCredentials).getContent()) {
                    gcpCredentials = JSONObject.fromObject(is);
                }
            } else {
                throw new IllegalArgumentException(String.format("Credentials '%s' are of an unhandled type: %s",
                        gcpCredentialsId, gcpIdCredentials.getClass().getName()));
            }

        } else if (gcpCredentialsFile != null) {
            gcpCredentials = CloudifyPluginUtilities.readYamlOrJson(workspace.child(gcpCredentialsFile));
        }

        // If GCP authentication was provided, then put "authentication" in Client Config.

        if (gcpCredentials != null) {
            clientConfig.put("authentication", Collections.singletonMap("gcp_service_account", gcpCredentials));
        }

        Map<String, Object> clientConfigConfiguration = new HashMap<>();

        if (k8sMaster != null) {
            apiOptionsMap.put("host", k8sMaster);
        }

        // Only add to ClientConfig->Configuration if not empty.
        if (!apiOptionsMap.isEmpty()) {
            clientConfigConfiguration.put("api_options", apiOptionsMap);
        }
        // Only add to ClientConfig if not empty.
        if (!clientConfigConfiguration.isEmpty()) {
            clientConfig.put("configuration", clientConfigConfiguration);
        }

        operationInputs.put(INPUT_CLIENT_CONFIG, clientConfig);
        operationInputs.put(INPUT_DEFINITION, definitionMap);
        operationInputs.put(INPUT_OPTIONS, optionsMap);
        putIfNonNullValue(operationInputs, INPUT_VALIDATE_STATUS, validateStatus);
        putIfNonNullValue(operationInputs, INPUT_ALLOW_NODE_REDEFINITION, allowNodeRedefinition);

        inputPrintPredicate = x -> !x.equals(INPUT_CLIENT_CONFIG);
        super.performImpl(run, launcher, listener, workspace, envVars, cloudifyClient);
    }

    @Override
    protected String getIntegrationName() {
        return "kubernetes";
    }

    @Override
    protected String getIntegrationVersion() {
        return "1.0";
    }

    @Override
    protected BlueprintUploadSpec getBlueprintUploadSpec() throws IOException {
        return new BlueprintUploadSpec("/blueprints/k8s/blueprint.yaml");
    }

    @Symbol("cfyKubernetes")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheckTemplateUrl(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        @Override
        public String getDisplayName() {
            return Messages.KubernetesBuildStep_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("gcpCredentialsId", gcpCredentialsId)
                .append("gcpCredentialsFile", gcpCredentialsFile)
                .append("k8sMaster", k8sMaster)
                .append("apiOptionsAsString", apiOptionsAsString)
                .append("apiOptionsFile", apiOptionsFile)
                .append("apiOptions", apiOptions)
                .append("definitionAsString", definitionAsString)
                .append("definitionFile", definitionFile)
                .append("definition", definition)
                .append("optionsAsString", optionsAsString)
                .append("optionsFile", optionsFile)
                .append("options", options)
                .append("validateStatus", validateStatus)
                .append("allowNodeRedefinition", allowNodeRedefinition)
                .toString();
    }
}
