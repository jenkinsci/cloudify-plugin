package co.cloudify.jenkins.plugin.callables;

import java.io.File;
import java.io.IOException;

import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.model.Blueprint;
import hudson.remoting.VirtualChannel;

public class BlueprintUploadDirFileCallable extends BlueprintUploadFileCallable {
    /** Serialization UID. */
    private static final long serialVersionUID = 1L;

    public BlueprintUploadDirFileCallable(
            final BlueprintsClient blueprintsClient,
            final String blueprintId,
            final String blueprintMainFile) {
        super(blueprintsClient, blueprintId, blueprintMainFile);
    }

    @Override
    public Blueprint invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        return blueprintsClient.upload(
                blueprintId,
                f,
                blueprintMainFile);
    }
}
