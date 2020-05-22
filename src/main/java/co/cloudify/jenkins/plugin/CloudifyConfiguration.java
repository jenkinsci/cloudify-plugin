package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.ws.rs.WebApplicationException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import co.cloudify.rest.client.CloudifyClient;
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
        if (Arrays.asList(host, username, password.getPlainText()).stream().anyMatch(x -> StringUtils.isBlank(x))) {
            return FormValidation.error(
                    "To validate, please provide the Cloudify Manager host, username, and password to authenticate with");
        }

        try {
            CloudifyClient client = CloudifyClient.create(
                    StringUtils.trim(host),
                    StringUtils.trim(username),
                    StringUtils.trim(password.getPlainText()),
                    secured, StringUtils.defaultString(tenant, defaultTenant));
            client.getManagerClient().getVersion();
            return FormValidation.ok("Connection successful");
        } catch (WebApplicationException ex) {
            return FormValidation.error(ex, "Connection error");
        }
    }

    public static CloudifyClient getCloudifyClient(final StandardUsernamePasswordCredentials creds,
            final String tenant) {
        return getCloudifyClient(creds.getUsername(), creds.getPassword().getPlainText(), tenant);
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
