package co.cloudify.jenkins.plugin;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.CloudifyClient;
import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;

@Extension
public class CloudifyConfiguration extends GlobalConfiguration {
	public static CloudifyConfiguration get() {
		return GlobalConfiguration.all().get(CloudifyConfiguration.class);
	}

	private String host;
	private String username;
	private String password;
	private	boolean	secured;
	private String tenant;

	public CloudifyConfiguration() {
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

	public String getPassword() {
		return password;
	}
	
	@DataBoundSetter
	public void setPassword(String password) {
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

	public FormValidation doCheckLabel(@QueryParameter String value) {
		if (StringUtils.isEmpty(host)) {
			return FormValidation.error("Please specify a host");
		}
		if (StringUtils.isEmpty(username)) {
			return FormValidation.error("Please specify a username");
		}
		if (StringUtils.isEmpty(password)) {
			return FormValidation.error("Please specify a password");
		}
		if (StringUtils.isEmpty(tenant)) {
			return FormValidation.error("Please specify a tenant");
		}
		return FormValidation.ok();
	}
	
	public static CloudifyClient getCloudifyClient() {
		CloudifyConfiguration config = CloudifyConfiguration.get();
		//return CloudifyClient.create(config.getHost(), config.getUsername(), config.getPassword(), config.isSecured(), config.getTenant());
		return CloudifyClient.create("10.239.3.110", "admin", "admin", false, "default_tenant");
	}
}
