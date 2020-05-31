package co.cloudify.jenkins.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hudson.FilePath;
import hudson.util.FormValidation;

public class CloudifyPluginUtilitiesTest {
    @TempDir
    public static File tempDir;

    @Test
    public void testGetMapFromMapOrStringJsonString() {
        String jsonAsString = "{\"key\": \"value\"}";
        Map<String, Object> result = CloudifyPluginUtilities.getMapFromMapOrString(jsonAsString, null);
        assertEquals(Collections.singletonMap("key", "value"), result);
    }

    @Test
    public void testGetMapFromMapOrStringYamlString() {
        String yamlAsString = "key: value";
        Map<String, Object> result = CloudifyPluginUtilities.getMapFromMapOrString(yamlAsString, null);
        assertEquals(Collections.singletonMap("key", "value"), result);
    }

    @Test
    public void testGetMapFromMapOrStringMap() {
        Map<String, Object> input = Collections.singletonMap("key", "value");
        Map<String, Object> result = CloudifyPluginUtilities.getMapFromMapOrString(null, input);
        assertEquals(input, result);
    }

    @Test
    public void testWriteJson() throws Exception {
        JsonObject jsonObj = Json.createObjectBuilder().add("key", "value").build();
        File outputFile = new File(tempDir, "testWriteJson.json");
        CloudifyPluginUtilities.writeJson(jsonObj, new FilePath(outputFile));
        JsonObject readObj;
        try (InputStream is = new FileInputStream(outputFile)) {
            readObj = Json.createReader(is).readObject();
        }
        assertEquals(jsonObj, readObj);
    }

    @Test
    public void testReadYaml() throws Exception {
        File outputFile = new File(tempDir, "testReadYaml.yaml");
        FileUtils.write(outputFile, "key: value", StandardCharsets.UTF_8);
        Map<String, Object> result = CloudifyPluginUtilities.readYamlOrJson(new FilePath(outputFile));
        assertEquals(Collections.singletonMap("key", "value"), result);
    }

    @Test
    public void testReadJson() throws Exception {
        File outputFile = new File(tempDir, "testReadJson.json");
        FileUtils.write(outputFile, "{\"key\": \"value\"}", StandardCharsets.UTF_8);
        Map<String, Object> result = CloudifyPluginUtilities.readYamlOrJson(new FilePath(outputFile));
        assertEquals(Collections.singletonMap("key", "value"), result);
    }

    @Test
    public void testReadYamlFromString() throws Exception {
        assertEquals(Collections.singletonMap("key", "value"), CloudifyPluginUtilities.readYamlOrJson("key: value"));
    }

    @Test
    public void testReadJsonFromString() throws Exception {
        assertEquals(Collections.singletonMap("key", "value"),
                CloudifyPluginUtilities.readYamlOrJson("{\"key\": \"value\"}"));
    }

    @Test
    public void testReadYamlOrJsonFromFileOnly() throws Exception {
        FilePath workspacePath = new FilePath(tempDir);
        String fileLocation = "testCreateMappingFromFileOnly.yaml";
        FileUtils.write(new File(tempDir, fileLocation), "key: value", StandardCharsets.UTF_8);
        Map<String, Map<String, String>> result = CloudifyPluginUtilities.readYamlOrJson(workspacePath, null,
                fileLocation);
        assertEquals(Collections.singletonMap("key", "value"), result);
    }

    @Test
    public void testReadYamlOrJsonFromStringOnly() throws Exception {
        assertEquals(Collections.singletonMap("key", "value"),
                CloudifyPluginUtilities.readYamlOrJson(null, "key: value", null));
    }

    @Test
    public void testReadYamlOrJsonFromFileAndString() throws Exception {
        FilePath workspacePath = new FilePath(tempDir);
        String fileLocation = "testCreateMappingFromFileOnly.yaml";
        FileUtils.write(new File(tempDir, fileLocation), "key1: value1", StandardCharsets.UTF_8);
        Map<String, Map<String, String>> result = CloudifyPluginUtilities.readYamlOrJson(workspacePath, "key2: value2",
                fileLocation);
        Map<String, Object> expectedResult = new LinkedHashMap<>();
        expectedResult.put("key1", "value1");
        expectedResult.put("key2", "value2");
        assertEquals(expectedResult, result);
    }

    @Test
    public void testTransform() throws Exception {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceKey1", "sourceValue1");
        source.put("sourceKey2", "sourceValue2");
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("sourceKey1", "targetKey1");
        mapping.put("sourceKey2", "targetKey2");
        Map<String, Object> result = new LinkedHashMap<>();
        CloudifyPluginUtilities.transform(mapping, result, source);
        assertEquals(2, result.size());
        assertEquals("sourceValue1", result.get("targetKey1"));
        assertEquals("sourceValue2", result.get("targetKey2"));
    }

    @Test
    public void testTransformOutputsFile() throws Exception {
        JsonObject outputsObj = Json.createObjectBuilder()
                .add("outputs", Json.createObjectBuilder()
                        .add("outputKey1", "outputValue1")
                        .add("outputKey2", "outputValue2"))
                .add("capabilities", Json.createObjectBuilder()
                        .add("capKey1", "capValue1")
                        .add("capKey2", "capValue2"))
                .build();
        File outputFile = new File(tempDir, "testTransformOutputsFile.json");
        try (OutputStream os = new FileOutputStream(outputFile)) {
            Json.createWriter(os).writeObject(outputsObj);
        }

        Map<String, Map<String, String>> mapping = new LinkedHashMap<>();
        mapping.put("outputs", Collections.singletonMap("outputKey1", "ip_address"));
        mapping.put("capabilities", Collections.singletonMap("capKey2", "port_number"));

        Map<String, Object> results = new LinkedHashMap<>();
        CloudifyPluginUtilities.transformOutputsFile(new FilePath(outputFile), mapping, results);

        assertEquals(2, results.size());
        assertEquals("outputValue1", results.get("ip_address"));
        assertEquals("capValue2", results.get("port_number"));
    }

    @Test
    public void testStringIsYamlValid() {
        assertEquals(FormValidation.ok(), CloudifyPluginUtilities.validateStringIsYamlOrJson("key: value"));
    }

    @Test
    public void testStringIsYamlJson() {
        assertEquals(FormValidation.ok(), CloudifyPluginUtilities.validateStringIsYamlOrJson("{\"key\": \"value\"}"));
    }

    @Test
    public void testStringIsYamlOrJsonInvalid() {
        assertEquals(FormValidation.Kind.ERROR,
                CloudifyPluginUtilities.validateStringIsYamlOrJson("key!@!$!$value").kind);
    }
}
