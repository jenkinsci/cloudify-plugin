package co.cloudify.jenkins.plugin;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;

import org.junit.Test;

import hudson.FilePath;

public class CloudifyPluginUtilitiesTest {
    private static Map toMap(final Object[][] matrix) {
        return Stream.of(matrix).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    }

    @Test
    public void testTransformation() throws Exception {
        Map<String, Object> outputContents = toMap(
                new Object[][] {
                        { "outputs", toMap(new Object[][] {
                                { "endpoint", "10.0.0.130" },
                                { "number", 1 }
                        }) },
                        { "capabilities", toMap(new Object[][] {
                                { "test", "testCap" }
                        }) }
                });

        Map<String, Map<String, String>> mapping = toMap(
                new Object[][] {
                        { "outputs", toMap(new Object[][] {
                                { "endpoint", "ip" },
                                { "number", "count" }
                        }) },
                        { "capabilities", toMap(new Object[][] {
                                { "test", "foo" }
                        }) }
                });

        Map<String, Object> results = new HashMap<>();
        File file = File.createTempFile("test", ".json");
        FilePath filePath = new FilePath(file);
        CloudifyPluginUtilities.writeJson(Json.createObjectBuilder(outputContents).build(), filePath);
        CloudifyPluginUtilities.transformOutputsFile(filePath, mapping, results);
        Map<String, Object> expected = toMap(new Object[][] {
                { "ip", "10.0.0.130" },
                { "foo", "testCap" },
                { "count", 1 }
        });
        assertThat(results, is(expected));
        file.delete();
    }
}
