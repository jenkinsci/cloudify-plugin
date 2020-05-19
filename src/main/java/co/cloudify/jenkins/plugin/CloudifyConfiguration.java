package co.cloudify.jenkins.plugin;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import co.cloudify.rest.client.CloudifyClient;
import hudson.EnvVars;
import hudson.Extension;
import hudson.util.FormValidation;
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
    private String defaultTenant;

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

    public String getDefaultTenant() {
        return defaultTenant;
    }

    @DataBoundSetter
    public void setDefaultTenant(String tenant) {
        this.defaultTenant = tenant;
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

    /*
     * Commented out for now, as we don't get username/password as inputs here
     * anymore. We may resurrect this in the future and ask for username/password
     * just for the purpose of testing the connection.
     */
//    public FormValidation doTestConnection(
//            @QueryParameter final String host,
//            @QueryParameter final String username,
//            @QueryParameter final Secret password,
//            @QueryParameter final String tenant,
//            @QueryParameter final boolean secured) throws IOException, ServletException {
//        try {
//            CloudifyClient client = CloudifyClient.create(host, username, password.getPlainText(), secured, tenant);
//            client.getManagerClient().getVersion();
//            return FormValidation.ok("Connection successful");
//        } catch (WebApplicationException ex) {
//            return FormValidation.error(ex, "Connection error");
//        }
//    }

    public static CloudifyClient getCloudifyClient(final EnvVars envVars, final String tenant) {
        String cfyUsername = envVars.get(CloudifyPluginUtilities.ENVVAR_CFY_USERNAME, null);
        String cfyPassword = envVars.get(CloudifyPluginUtilities.ENVVAR_CFY_PASSWORD, null);

        return getCloudifyClient(cfyUsername, cfyPassword, tenant);
    }

    public static CloudifyClient getCloudifyClient(final String username, final String password,
            final String tenant) {
        return getCloudifyClient(CloudifyConfiguration.get(), username, password, tenant);
    }

    public static CloudifyClient getCloudifyClient(final CloudifyConfiguration config, final String username,
            final String password, final String tenant) {
        Validate.notBlank(username);
        Validate.notBlank(password);

        String effectiveTenant = tenant != null ? tenant : StringUtils.trimToNull(config.getDefaultTenant());
        effectiveTenant = StringUtils.defaultString(effectiveTenant, CloudifyClient.DEFAULT_TENANT_ID);
        return CloudifyClient.create(
                config.getHost(), username,
                password,
                config.isSecured(), effectiveTenant)
                .withToken();
    }
}
