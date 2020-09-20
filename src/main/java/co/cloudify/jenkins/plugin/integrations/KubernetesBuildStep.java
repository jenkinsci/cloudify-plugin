package co.cloudify.jenkins.plugin.integrations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

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

/**
 * A build step for creating Kubernetes resources.
 * 
 * @author Isaac Shabtay
 */
public class KubernetesBuildStep extends IntegrationBuildStep {
    private static final String API_OPTIONS_API_KEY = "api_key";
    private static final String INPUT_CLIENT_CONFIG = "client_config";
    private static final String INPUT_DEFINITION = "definition";
    private static final String INPUT_OPTIONS = "options";
    private static final String INPUT_VALIDATE_STATUS = "validate_status";
    private static final String INPUT_ALLOW_NODE_REDEFINITION = "allow_node_redefinition";

    private String gcpCredentialsId;
    private String gcpCredentialsFile;
    private String k8sMaster;
    private String apiKeyCredentialsId;
    private String apiKeyFile;
    private String caCert;
    private String sslCertFile;
    private String sslKeyFile;
    private boolean skipSslVerification;
    private boolean k8sDebug;
    private String definitionAsString;
    private String definitionFile;
    private Map<String, Object> definition;
    private String optionsAsString;
    private String optionsFile;
    private Map<String, Object> options;
    private String namespace;
    private boolean validateStatus = false;
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

    public String getApiKeyCredentialsId() {
        return apiKeyCredentialsId;
    }

    @DataBoundSetter
    public void setApiKeyCredentialsId(String apiKeyCredentialsId) {
        this.apiKeyCredentialsId = apiKeyCredentialsId;
    }

    public String getApiKeyFile() {
        return apiKeyFile;
    }

    @DataBoundSetter
    public void setApiKeyFile(String apiKeyFile) {
        this.apiKeyFile = apiKeyFile;
    }

    public String getCaCert() {
        return caCert;
    }

    @DataBoundSetter
    public void setCaCert(String caCert) {
        this.caCert = caCert;
    }

    public String getSslCertFile() {
        return sslCertFile;
    }

    @DataBoundSetter
    public void setSslCertFile(String sslCertFile) {
        this.sslCertFile = sslCertFile;
    }

    public String getSslKeyFile() {
        return sslKeyFile;
    }

    @DataBoundSetter
    public void setSslKeyFile(String sslKeyFile) {
        this.sslKeyFile = sslKeyFile;
    }

    public boolean isSkipSslVerification() {
        return skipSslVerification;
    }

    @DataBoundSetter
    public void setSkipSslVerification(boolean skipSslVerification) {
        this.skipSslVerification = skipSslVerification;
    }

    public boolean isK8sDebug() {
        return k8sDebug;
    }

    @DataBoundSetter
    public void setK8sDebug(boolean k8sDebug) {
        this.k8sDebug = k8sDebug;
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

    public String getNamespace() {
        return namespace;
    }

    @DataBoundSetter
    public void setNamespace(String namespace) {
        this.namespace = namespace;
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
        String k8sMaster = CloudifyPluginUtilities.expandString(envVars, this.k8sMaster);
        String apiKeyCredentialsId = CloudifyPluginUtilities.expandString(envVars, this.apiKeyCredentialsId);
        String apiKeyFile = CloudifyPluginUtilities.expandString(envVars, this.apiKeyFile);
        String caCert = CloudifyPluginUtilities.expandString(envVars, this.caCert);
        String sslCertFile = CloudifyPluginUtilities.expandString(envVars, this.sslCertFile);
        String sslKeyFile = CloudifyPluginUtilities.expandString(envVars, this.sslKeyFile);
        String definitionAsString = CloudifyPluginUtilities.expandString(envVars, this.definitionAsString);
        String definitionFile = CloudifyPluginUtilities.expandString(envVars, this.definitionFile);
        String optionsAsString = CloudifyPluginUtilities.expandString(envVars, this.optionsAsString);
        String optionsFile = CloudifyPluginUtilities.expandString(envVars, this.optionsFile);
        String namespace = CloudifyPluginUtilities.expandString(envVars, this.namespace);

        Map<String, Object> definitionMap = CloudifyPluginUtilities.getCombinedMap(workspace, definitionFile,
                definitionAsString, this.definition);
        Map<String, Object> optionsMap = CloudifyPluginUtilities.getCombinedMap(workspace, optionsFile,
                optionsAsString, this.options);

        // Prepare the Client Config.

        Map<String, Object> clientConfig = new HashMap<>();

        Map<String, Object> gcpCredentials = CloudifyPluginUtilities.readYamlOrJsonCredentials(run, workspace,
                gcpCredentialsId, gcpCredentialsFile);

        // If GCP authentication was provided, then put "authentication" in Client Config.

        if (gcpCredentials != null) {
            clientConfig.put("authentication", Collections.singletonMap("gcp_service_account", gcpCredentials));
        }

        Map<String, Object> apiOptionsMap = new HashMap<>();

        if (k8sMaster != null) {
            apiOptionsMap.put("host", k8sMaster);
        }

        // If API key credentials were provided, add them, but only if none
        // were provided already.

        String apiKeyCredentials = CloudifyPluginUtilities.readStringCredentials(run, workspace, apiKeyCredentialsId,
                apiKeyFile);

        putIfNonNullValue(apiOptionsMap, API_OPTIONS_API_KEY, apiKeyCredentials);
        putIfNonNullValue(apiOptionsMap, "ssl_ca_cert", caCert);
        putIfNonNullValue(apiOptionsMap, "cert_file", sslCertFile);
        putIfNonNullValue(apiOptionsMap, "key_file", sslKeyFile);
        apiOptionsMap.put("verify_ssl", !skipSslVerification);
        apiOptionsMap.put("debug", k8sDebug);

        Map<String, Object> clientConfigConfiguration = new HashMap<>();
        clientConfigConfiguration.put("api_options", apiOptionsMap);

        clientConfig.put("configuration", clientConfigConfiguration);

        // Handle options.

        if (namespace != null) {
            optionsMap.put("namespace", namespace);
        }

        operationInputs.put(INPUT_CLIENT_CONFIG, clientConfig);
        operationInputs.put(INPUT_DEFINITION, definitionMap);
        operationInputs.put(INPUT_OPTIONS, optionsMap);
        operationInputs.put(INPUT_VALIDATE_STATUS, validateStatus);
        operationInputs.put(INPUT_ALLOW_NODE_REDEFINITION, allowNodeRedefinition);

        inputPrintPredicate = x -> !x.equals(INPUT_CLIENT_CONFIG);
        super.performImpl(run, launcher, listener, workspace, envVars, cloudifyClient);
    }

    @Override
    protected String getIntegrationName() {
        return "kubernetes";
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
                .append("apiKeyCredentialId", apiKeyCredentialsId)
                .append("apiKeyFile", apiKeyFile)
                .append("caCert", caCert)
                .append("sslCertFile", sslCertFile)
                .append("sslKeyFile", sslKeyFile)
                .append("skipSslVerification", skipSslVerification)
                .append("k8sDebug", k8sDebug)
                .append("definitionAsString", definitionAsString)
                .append("definitionFile", definitionFile)
                .append("definition", definition)
                .append("optionsAsString", optionsAsString)
                .append("optionsFile", optionsFile)
                .append("options", options)
                .append("namespace", namespace)
                .append("validateStatus", validateStatus)
                .append("allowNodeRedefinition", allowNodeRedefinition)
                .toString();
    }
}
