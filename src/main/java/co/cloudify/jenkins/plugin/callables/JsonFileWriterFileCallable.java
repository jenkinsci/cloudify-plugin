package co.cloudify.jenkins.plugin.callables;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

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
        try (FileOutputStream fos = new FileOutputStream(f);
                OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            osw.write(CloudifyPluginUtilities.toString(json));
        }
        return null;
    }
}
