package co.cloudify.jenkins.plugin;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;

import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.model.Blueprint;

public class BlueprintUploadSpec implements Serializable {
    /** Serialization UID. */
    private static final long serialVersionUID = 1L;

    private URL archiveUrl;
    private File archivePath;
    private String mainFileName;
    private File blueprintFile;

    public BlueprintUploadSpec(final URL archiveUrl, final String mainFileName) {
        Validate.notNull(archiveUrl);
        Validate.notEmpty(mainFileName);
        this.archiveUrl = archiveUrl;
        this.mainFileName = mainFileName;
    }

    public BlueprintUploadSpec(final File archivePath, final String mainFileName) {
        Validate.notNull(archivePath);
        Validate.notEmpty(mainFileName);
        this.archivePath = archivePath;
        this.mainFileName = mainFileName;
    }

    public BlueprintUploadSpec(final File blueprintFile) {
        Validate.notNull(blueprintFile);
        this.blueprintFile = blueprintFile;
    }

    public Blueprint upload(final BlueprintsClient client, final String id) throws IOException {
        if (blueprintFile != null) {
            return client.upload(id, blueprintFile.getParentFile(), blueprintFile.getName());
        }
        if (archiveUrl != null) {
            return client.upload(id, archiveUrl, mainFileName);
        }
        return client.uploadArchive(id, archivePath, mainFileName);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("archiveUrl", archiveUrl)
                .append("archivePath", archivePath)
                .append("blueprintFile", blueprintFile)
                .append("mainFileName", mainFileName)
                .toString();
    }
}
