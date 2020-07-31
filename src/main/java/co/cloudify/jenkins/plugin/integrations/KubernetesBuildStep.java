package co.cloudify.jenkins.plugin.integrations;

import java.io.IOException;
import java.io.InputStream;
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
 * A build step for applying a Terraform template.
 * 
 * @author Isaac Shabtay
 */
public class KubernetesBuildStep extends IntegrationBuildStep {
	private static final String INPUT_GCP_CREDENTIALS = "gcp_credentials";

	private String gcpCredentialsId;
	private String gcpCredentialsFile;
	private String k8sMaster;
	private boolean verifySsl = true;
	private boolean debug = false;
	private boolean validateStatus = true;
	private boolean allowNodeRedefinition = false;
	private String resourcePath;
	private String variablesAsString;
	private String variablesFile;
	private Map<String, Object> variables;

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

	public boolean isVerifySsl() {
		return verifySsl;
	}

	@DataBoundSetter
	public void setVerifySsl(boolean verifySsl) {
		this.verifySsl = verifySsl;
	}

	public boolean isDebug() {
		return debug;
	}

	@DataBoundSetter
	public void setDebug(boolean debug) {
		this.debug = debug;
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

	public String getResourcePath() {
		return resourcePath;
	}

	@DataBoundSetter
	public void setResourcePath(String resourcePath) {
		this.resourcePath = resourcePath;
	}

	public String getVariablesAsString() {
		return variablesAsString;
	}

	@DataBoundSetter
	public void setVariablesAsString(String parameters) {
		this.variablesAsString = parameters;
	}

	public String getVariablesFile() {
		return variablesFile;
	}

	@DataBoundSetter
	public void setVariablesFile(String variablesFile) {
		this.variablesFile = variablesFile;
	}

	public Map<String, Object> getVariables() {
		return variables;
	}

	@DataBoundSetter
	public void setVariables(Map<String, Object> variables) {
		this.variables = variables;
	}

	@Override
	protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
			final FilePath workspace, final EnvVars envVars, final CloudifyClient cloudifyClient) throws Exception {
		String gcpCredentialsId = CloudifyPluginUtilities.expandString(envVars, this.gcpCredentialsId);
		String gcpCredentialsFile = CloudifyPluginUtilities.expandString(envVars, this.gcpCredentialsFile);
		String k8sMaster = CloudifyPluginUtilities.expandString(envVars, this.k8sMaster);
		String resourcePath = CloudifyPluginUtilities.expandString(envVars, this.resourcePath);
		String variablesFile = CloudifyPluginUtilities.expandString(envVars, this.variablesFile);
		String variablesAsString = CloudifyPluginUtilities.expandString(envVars, this.variablesAsString);

		Map<String, Object> gcpCredentials;
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
		} else {
			throw new IllegalArgumentException("Either credentials ID or file must be provided");
		}

		Map<String, Object> variablesMap = CloudifyPluginUtilities.getCombinedMap(workspace, variablesFile,
				variablesAsString, this.variables);

		putIfNonNullValue(operationInputs, INPUT_GCP_CREDENTIALS, JSONObject.fromObject(gcpCredentials).toString(4));
		putIfNonNullValue(operationInputs, "kubernetes_master", k8sMaster);
		putIfNonNullValue(operationInputs, "verify_ssl", verifySsl);
		putIfNonNullValue(operationInputs, "debug", debug);
		putIfNonNullValue(operationInputs, "validate_status", validateStatus);
		putIfNonNullValue(operationInputs, "allow_node_redefinition", allowNodeRedefinition);
		putIfNonNullValue(operationInputs, "resource_path", resourcePath);
		putIfNonNullValue(operationInputs, "resource_template_variables", variablesMap);

		inputPrintPredicate = x -> !x.equals(INPUT_GCP_CREDENTIALS);
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
		return new ToStringBuilder(this).appendSuper(super.toString()).append("gcpCredentialsId", gcpCredentialsId)
				.append("gcpCredentialsFile", gcpCredentialsFile).append("k8sMaster", k8sMaster)
				.append("verifySsl", verifySsl).append("debug", debug).append("validateStatus", validateStatus)
				.append("allowNodeRedefinition", allowNodeRedefinition).append("resourcePath", resourcePath)
				.append("variablesAsString", variablesAsString).append("variablesFile", variablesFile)
				.append("variables", variables).toString();
	}
}
