package co.cloudify.jenkins.plugin.creds;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.DomainSpecificationDescriptor;

import hudson.Extension;

public class CloudifyDomainSpecification extends DomainSpecification {
    /** Serialization UID. */
    private static final long serialVersionUID = 1L;

    private boolean test;

    @DataBoundConstructor
    public CloudifyDomainSpecification(final boolean test) {
        super();
        this.test = test;
    }

    public boolean isTest() {
        return test;
    }

    @Override
    @Nonnull
    public Result test(@Nonnull DomainRequirement requirement) {
        if (requirement instanceof CloudifyDomainRequirement) {
            if (test == ((CloudifyDomainRequirement) requirement).isTest()) {
                return Result.POSITIVE;
            }
            return Result.NEGATIVE;
        }
        return Result.UNKNOWN;
    }

    @Extension
    public static class DescriptorImpl extends DomainSpecificationDescriptor {
        @Override
        public String getDisplayName() {
            return "Cloudify Manager";
        }
    }
}
