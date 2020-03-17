package co.cloudify.jenkins.plugin.callables;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.json.JsonObject;

import org.jenkinsci.remoting.RoleChecker;

import co.cloudify.jenkins.plugin.CloudifyPluginUtilities;
import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;

public class JsonFileWriterFileCallable implements FileCallable<Void> {
    /** Serialization UID. */
    private static final long serialVersionUID = 1L;

    private JsonObject json;

    public JsonFileWriterFileCallable(final JsonObject json) {
        this.json = json;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        // Nothing.
    }

    @Override
    public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(CloudifyPluginUtilities.toString(json));
        }
        return null;
    }
}
