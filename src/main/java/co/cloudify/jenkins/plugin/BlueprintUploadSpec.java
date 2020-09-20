package co.cloudify.jenkins.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.cloudify.rest.client.BlueprintsClient;
import co.cloudify.rest.model.Blueprint;

/**
 * This class encapsulates all information required to upload a blueprint. It was designed
 * to cope with the fact that certain blueprint uploads would require additional resources
 * and those resources require cleanup, implying that using {@link AutoCloseable} would
 * probably be beneficial.
 * 
 * @author Isaac Shabtay
 */
public class BlueprintUploadSpec implements Serializable, AutoCloseable {
    /** Serialization UID. */
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(BlueprintUploadSpec.class);

    private static final String BLUEPRINT_FILE_NAME = "blueprint.yaml";

    private URL archiveUrl;
    private File archivePath;
    private String mainFileName;
    private String blueprintResourceName;
    private File tempBlueprintDir;

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

    public BlueprintUploadSpec(final String blueprintResourceName) {
        Validate.notNull(blueprintResourceName);
        this.blueprintResourceName = blueprintResourceName;
    }

    public Blueprint upload(final BlueprintsClient client, final String id) throws IOException {
        if (blueprintResourceName != null) {
            tempBlueprintDir = Files.createTempDirectory("cfy").toFile();
            logger.info("Created temporary directory: {}", tempBlueprintDir);
            File blueprintPath = new File(tempBlueprintDir, BLUEPRINT_FILE_NAME);
            try (InputStream resourceAsStream = BlueprintUploadSpec.class.getResourceAsStream(blueprintResourceName)) {
                if (resourceAsStream == null) {
                    throw new RuntimeException(
                            String.format("Failed locating blueprint resource: %s", blueprintResourceName));
                }
                Files.copy(resourceAsStream, blueprintPath.toPath());
            }

            return client.upload(id, tempBlueprintDir, BLUEPRINT_FILE_NAME);
        }
        if (archiveUrl != null) {
            logger.info("Uploading blueprint '{}' from {}, main filename: {}", id, archiveUrl, mainFileName);
            return client.upload(id, archiveUrl, mainFileName);
        }
        return client.uploadArchive(id, archivePath, mainFileName);
    }

    @Override
    public void close() throws Exception {
        if (tempBlueprintDir != null) {
            // A temporary directory was created for uploading this.
            // So delete it.
            logger.info("Deleting temporary directory: {}", tempBlueprintDir);
            FileUtils.deleteDirectory(tempBlueprintDir);
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("archiveUrl", archiveUrl)
                .append("archivePath", archivePath)
                .append("blueprintResourceName", blueprintResourceName)
                .append("mainFileName", mainFileName)
                .toString();
    }
}
