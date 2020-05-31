package co.cloudify.jenkins.plugin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.cloudify.rest.client.BlueprintsClient;

public class BlueprintUploadSpecTest {
    private static final String BLUEPRINT_ID = "blueprint-id";
    private static final URL ARCHIVE_URL;
    private static final File ARCHIVE_PATH = new File(SystemUtils.getJavaIoTmpDir(), "blueprint.tar.gz");
    private static final File BLUEPRINT_FILE = new File(SystemUtils.getJavaIoTmpDir(), "blueprint.yaml");
    private static final String MAIN_FILE_NAME = "blueprint.yaml";
    private BlueprintsClient blueprintsClient;

    static {
        try {
            ARCHIVE_URL = new URL("http://example.com");
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @BeforeEach
    public void initTest() {
        blueprintsClient = mock(BlueprintsClient.class);
    }

    @Test
    public void testArchiveUrl() throws Exception {
        BlueprintUploadSpec spec = new BlueprintUploadSpec(ARCHIVE_URL, MAIN_FILE_NAME);
        blueprintsClient = spy(blueprintsClient);
        spec.upload(blueprintsClient, BLUEPRINT_ID);
        verify(blueprintsClient, only()).upload(BLUEPRINT_ID, ARCHIVE_URL, MAIN_FILE_NAME);
    }

    @Test
    public void testArchivePath() throws Exception {
        BlueprintUploadSpec spec = new BlueprintUploadSpec(ARCHIVE_PATH, MAIN_FILE_NAME);
        blueprintsClient = spy(blueprintsClient);
        spec.upload(blueprintsClient, BLUEPRINT_ID);
        verify(blueprintsClient, only()).uploadArchive(BLUEPRINT_ID, ARCHIVE_PATH, MAIN_FILE_NAME);
    }

    @Test
    public void testBlueprintFile() throws Exception {
        BlueprintUploadSpec spec = new BlueprintUploadSpec(BLUEPRINT_FILE);
        blueprintsClient = spy(blueprintsClient);
        spec.upload(blueprintsClient, BLUEPRINT_ID);
        verify(blueprintsClient, only()).upload(BLUEPRINT_ID, SystemUtils.getJavaIoTmpDir(), BLUEPRINT_FILE.getName());
    }
}
