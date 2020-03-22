package co.cloudify.jenkins.plugin.parameters;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import co.cloudify.jenkins.plugin.CloudifyConfiguration;
import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.model.ListResponse;
import co.cloudify.rest.model.Tenant;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;

/**
 * Parameter definition class for a parameter type that displays a list of
 * tenants.
 * 
 * @author Isaac Shabtay
 */
public class TenantsListParameterDefinition extends ParameterDefinition {
    private static final long serialVersionUID = 1L;

    private String tenantId;

    @DataBoundConstructor
    public TenantsListParameterDefinition(String name, String description) {
        super(name, description);
    }

    public String getTenantId() {
        return tenantId;
    }

    @DataBoundSetter
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Exported
    public List<String> getChoices() {
        CloudifyClient cloudifyClient = CloudifyConfiguration.getCloudifyClient();
        ListResponse<Tenant> tenants = cloudifyClient.getTenantsClient().list();
        return tenants
                .stream()
                .map(Tenant::getName)
                .collect(Collectors.toList());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        return null;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        String name = jo.getString("name");
        String tenantId = jo.getString("tenantId");
        return new StringParameterValue(name, tenantId);
    }

    @Extension
    @Symbol({ "cloudify", "cloudifyTenantParam" })
    public static class TenantSelectorParameterDescriptor extends ParameterDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return Messages.TenantsListParameterDefinition_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("tenantId", tenantId)
                .toString();
    }
}
