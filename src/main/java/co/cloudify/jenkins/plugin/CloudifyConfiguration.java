package co.cloudify.jenkins.plugin;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.ws.rs.WebApplicationException;

import org.apache.commons.lang3.Validate;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.CloudifyClient;
import hudson.EnvVars;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;

/**
 * Configuration object for Cloudify.
 * 
 * @author Isaac Shabtay
 */
@Extension
public class CloudifyConfiguration extends GlobalConfiguration {
    private String host;
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

    /**
     * @return The {@link CloudifyConfiguration} instance for this Jenkins
     *         installation.
     */
    public static CloudifyConfiguration get() {
        return GlobalConfiguration.all().get(CloudifyConfiguration.class);
    }

    public FormValidation doTestConnection(
            @QueryParameter final String host,
            @QueryParameter final String username,
            @QueryParameter final Secret password,
            @QueryParameter final String tenant,
            @QueryParameter final boolean secured) throws IOException, ServletException {
        try {
            CloudifyClient client = CloudifyClient.create(host, username, password.getPlainText(), secured, tenant);
            client.getManagerClient().getVersion();
            return FormValidation.ok("Connection successful");
        } catch (WebApplicationException ex) {
            return FormValidation.error(ex, "Connection error");
        }
    }

    public static CloudifyClient getCloudifyClient(final EnvVars envVars) {
        String cfyUsername = envVars.get(CloudifyPluginUtilities.ENVVAR_CFY_USERNAME, null);
        String cfyPassword = envVars.get(CloudifyPluginUtilities.ENVVAR_CFY_PASSWORD, null);

        return getCloudifyClient(cfyUsername, cfyPassword);
    }

    public static CloudifyClient getCloudifyClient(final String username, final String password) {
        return getCloudifyClient(CloudifyConfiguration.get(), username, password);
    }

    public static CloudifyClient getCloudifyClient(final CloudifyConfiguration config, final String username,
            final String password) {
        Validate.notBlank(username);
        Validate.notBlank(password);
        return CloudifyClient.create(
                config.getHost(), username,
                password,
                config.isSecured(), config.getTenant())
                .withToken();
    }
}
