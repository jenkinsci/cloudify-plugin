package co.cloudify.jenkins.plugin.callables;

import org.jenkinsci.remoting.RoleChecker;

import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.model.Blueprint;
import hudson.FilePath.FileCallable;

public abstract class BlueprintUploadFileCallable implements FileCallable<Blueprint> {
    /** Serialization UID. */
    private static final long serialVersionUID = 1L;
    protected BlueprintsClient blueprintsClient;
    protected String blueprintId;
    protected String blueprintMainFile;

    public BlueprintUploadFileCallable(
            final BlueprintsClient blueprintsClient,
            final String blueprintId,
            final String blueprintMainFile
            ) {
        this.blueprintsClient = blueprintsClient;
        this.blueprintId = blueprintId;
        this.blueprintMainFile = blueprintMainFile;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        // Nothing.
    }

}
