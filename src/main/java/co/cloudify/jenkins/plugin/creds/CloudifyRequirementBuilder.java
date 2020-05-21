package co.cloudify.jenkins.plugin.creds;

import java.util.ArrayList;
import java.util.List;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import edu.umd.cs.findbugs.annotations.NonNull;

public class CloudifyRequirementBuilder {
    private boolean test;

    private CloudifyRequirementBuilder() {
    }

    @NonNull
    public CloudifyRequirementBuilder create() {
        return new CloudifyRequirementBuilder();
    }

    @NonNull
    public CloudifyRequirementBuilder withTestServer(boolean test) {
        this.test = test;
        return this;
    }

    @NonNull
    public List<DomainRequirement> build() {
        List<DomainRequirement> result = new ArrayList<>();
        result.add(new CloudifyDomainRequirement(test)); // (2)
        result.addAll(URIRequirementBuilder.create() // (3)
                .withUri(test
                        ? "https://test.acme.example.com/"
                        : "https://prod.acme.example.com/")
                .build());
        return result;
    }
}
