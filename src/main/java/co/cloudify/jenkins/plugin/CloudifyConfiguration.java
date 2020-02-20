package co.cloudify.jenkins.plugin;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.CloudifyClient;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;

@Extension
public class CloudifyConfiguration extends GlobalConfiguration {
	public static CloudifyConfiguration get() {
		return GlobalConfiguration.all().get(CloudifyConfiguration.class);
	}

	private String host;
	private String username;
	private Secret password;
	private Boolean secured = Boolean.FALSE;
	private String tenant;

	@DataBoundConstructor
	public CloudifyConfiguration() {
		super();
		load();
	}

	public String getHost() {
		return host;
	}

	@DataBoundSetter
	public void setHost(String host) {
		this.host = host;
		save();
	}

	public String getUsername() {
		return username;
	}

	@DataBoundSetter
	public void setUsername(String username) {
		this.username = username;
		save();
	}

	public Secret getPassword() {
		return password;
	}

	@DataBoundSetter
	public void setPassword(Secret password) {
		this.password = password;
		save();
	}

	public boolean isSecured() {
		return secured;
	}

	@DataBoundSetter
	public void setSecured(boolean secured) {
		this.secured = secured;
		save();
	}

	public String getTenant() {
		return tenant;
	}

	@DataBoundSetter
	public void setTenant(String tenant) {
		this.tenant = tenant;
		save();
	}

	public FormValidation doCheckHost(@QueryParameter String value) {
		return FormValidation.validateRequired(value);
	}

	public FormValidation doCheckUsername(@QueryParameter String value) {
		return FormValidation.validateRequired(value);
	}

	public FormValidation doCheckPassword(@QueryParameter String value) {
		return FormValidation.validateRequired(value);
	}

	public FormValidation doCheckTenant(@QueryParameter String value) {
		return FormValidation.validateRequired(value);
	}

	public static CloudifyClient getCloudifyClient() {
		CloudifyConfiguration config = CloudifyConfiguration.get();
		return CloudifyClient.create(
		        config.getHost(), config.getUsername(),
		        Secret.toString(config.getPassword()),
		        config.isSecured(), config.getTenant());
	}
}
