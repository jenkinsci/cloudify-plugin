package co.cloudify.jenkins.plugin.creds;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;

public class CloudifyDomainRequirement extends DomainRequirement {
    private static final long serialVersionUID = 1L;
    private final boolean test;

    public CloudifyDomainRequirement(boolean test) {
        this.test = test;
    }

    public boolean isTest() {
        return test;
    }
}
