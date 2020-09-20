package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.ResourceBundle;

import javax.servlet.ServletException;
import javax.ws.rs.WebApplicationException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import co.cloudify.rest.client.CloudifyClient;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

/**
 * Configuration object for Cloudify.
 * 
 * @author Isaac Shabtay
 */
@Extension
public class CloudifyConfiguration extends GlobalConfiguration {
    private String host;
    private Boolean secured = Boolean.TRUE;
    private Boolean trustAllCerts = Boolean.FALSE;
    private String defaultTenant;
    private URL integrationBlueprintsArchiveUrl;

    @DataBoundConstructor
    public CloudifyConfiguration() {
        super();
        load();

        if (integrationBlueprintsArchiveUrl == null) {
            try {
                integrationBlueprintsArchiveUrl = new URL(ResourceBundle.getBundle("default-configuration")
                        .getString("integration.blueprints.archive.url"));
            } catch (MalformedURLException ex) {
                throw new RuntimeException("Failed retrieving location of integration bundle", ex);
            }
        }
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

    public boolean isTrustAllCerts() {
        return trustAllCerts;
    }

    @DataBoundSetter
    public void setTrustAllCerts(boolean trustAllCerts) {
        this.trustAllCerts = trustAllCerts;
    }

    public String getDefaultTenant() {
        return defaultTenant;
    }

    @DataBoundSetter
    public void setDefaultTenant(String tenant) {
        this.defaultTenant = tenant;
        save();
    }

    public URL getIntegrationBlueprintsArchiveUrl() {
        return integrationBlueprintsArchiveUrl;
    }

    @DataBoundSetter
    public void setIntegrationBlueprintsArchiveUrl(URL integrationBlueprintsArchiveUrl) {
        this.integrationBlueprintsArchiveUrl = integrationBlueprintsArchiveUrl;
        save();
    }

    public FormValidation doCheckHost(@QueryParameter String value) {
        return FormValidation.validateRequired(value);
    }

    public ListBoxModel doFillCredentialsIdItems(
            final @AncestorInPath Item item,
            @QueryParameter String credentialsId) {
        StandardListBoxModel result = new StandardListBoxModel();
        Jenkins jenkins = Jenkins.get();
        if (item == null) {
            if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
                return result.includeCurrentValue(credentialsId);
            }
        } else {
            if (!item.hasPermission(Item.EXTENDED_READ)
                    && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return result.includeCurrentValue(credentialsId);
            }
        }
        return result
                .includeMatchingAs(ACL.SYSTEM, jenkins, StandardUsernamePasswordCredentials.class,
                        Collections.emptyList(), CredentialsMatchers.always())
                .includeCurrentValue(credentialsId);
    }

    /**
     * @return The {@link CloudifyConfiguration} instance for this Jenkins
     *         installation.
     */
    public static CloudifyConfiguration get() {
        return GlobalConfiguration.all().get(CloudifyConfiguration.class);
    }

    @POST
    public FormValidation doTestConnection(
            @QueryParameter final String host,
            @QueryParameter final String credentialsId,
            @QueryParameter final String tenant,
            @QueryParameter final boolean secured) throws IOException, ServletException {
        if (Arrays.asList(host, credentialsId).stream().anyMatch(x -> StringUtils.isBlank(x))) {
            return FormValidation.error(
                    "To validate, please provide the Cloudify Manager host, and credentials to authenticate with");
        }

        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(CredentialsProvider
                .lookupCredentials(StandardUsernamePasswordCredentials.class, jenkins, ACL.SYSTEM,
                        Collections.emptyList()),
                CredentialsMatchers.withId(credentialsId));

        if (credentials == null) {
            return FormValidation.error("Could not find credentials: '%s'", credentialsId);
        }

        try {
            CloudifyClient client = CloudifyClient.create(
                    StringUtils.trim(host),
                    StringUtils.trim(credentials.getUsername()),
                    StringUtils.trim(credentials.getPassword().getPlainText()),
                    secured, StringUtils.trimToNull(StringUtils.defaultString(tenant, defaultTenant)));
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
