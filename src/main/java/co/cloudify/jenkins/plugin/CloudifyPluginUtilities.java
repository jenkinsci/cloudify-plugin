package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.client.DeploymentsClient;
import co.cloudify.rest.helpers.DeploymentsHelper;
import co.cloudify.rest.helpers.ExecutionFollowCallback;
import co.cloudify.rest.helpers.ExecutionsHelper;
import co.cloudify.rest.helpers.PrintStreamLogEmitterExecutionFollower;
import co.cloudify.rest.model.Deployment;
import co.cloudify.rest.model.EventLevel;
import co.cloudify.rest.model.Execution;
import hudson.AbortException;
import hudson.FilePath;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * Various utility methods used throughout the plugin.
 * 
 * @author Isaac Shabtay
 */
public class CloudifyPluginUtilities {
    public static String parseInput(final String s, final VariableResolver<String> resolver) {
        return StringUtils.trimToNull(Util.replaceMacro(s, resolver));
    }

    /**
     * Write a JAXB-annotated object to a file as JSON. We isolate this
     * functionality to one class only, as it uses facilities that are not a part of
     * the JDK and are likely to be included as standard in the future.
     * 
     * @param object     object to serialize
     * @param outputFile file to write to
     * 
     * @throws IOException Some I/O error has occured.
     */
    public static void writeBoundObject(final Object object, final FilePath outputFile)
            throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.registerModule(new JaxbAnnotationModule());
        try (OutputStream os = outputFile.write()) {
            mapper.writeValue(os, object);
        }
    }

    /**
     * Writes a JSON object to a {@link FilePath}.
     * 
     * @param object JSON object to write
     * @param path   path to write to
     * 
     * @throws IOException Some problem occured during serialization.
     */
    public static void writeJson(final JSONObject object, final FilePath path)
            throws IOException, InterruptedException {
        try (OutputStreamWriter os = new OutputStreamWriter(path.write())) {
            os.write(object.toString(4));
        }
    }

    /**
     * Reads a JSON file from a {@link FilePath}.
     * 
     * @param path path to the resource
     * 
     * @return A JSON object.
     * 
     * @throws IOException          May be thrown by underlying framework.
     * @throws InterruptedException May be thrown by underlying framework.
     */
    public static JSONObject readYamlOrJson(final FilePath path) throws IOException, InterruptedException {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try (InputStream is = path.read()) {
                return mapper.readValue(is, JSONObject.class);
            }
        } catch (IOException ex) {
            try (InputStream is = path.read()) {
                return JSONObject.fromObject(IOUtils.toString(is, StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Read a YAML or a JSON from a string.
     * 
     * @param str some string
     * 
     * @return A {@link JSONObject} containing the parsed data.
     */
    public static JSONObject readYamlOrJson(final String str) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(str, JSONObject.class);
        } catch (IOException ex) {
            return JSONObject.fromObject(str);
        }
    }

    public static JSONObject createMapping(final FilePath workspace, final String mappingString,
            final String mappingLocation) throws IOException, InterruptedException {
        JSONObject mapping = null;
        if (StringUtils.isNotBlank(mappingLocation)) {
            FilePath mappingFile = workspace.child(mappingLocation);
            mapping = readYamlOrJson(mappingFile);
        } else if (StringUtils.isNotBlank(mappingString)) {
            mapping = readYamlOrJson(mappingString);
        }
        return mapping;
    }

    private static void transform(JSONObject mapping, Map<String, Object> result, JSONObject source) {
        for (Map.Entry<String, String> entry : (Set<Map.Entry<String, String>>) mapping.entrySet()) {
            String from = entry.getKey();
            String to = entry.getValue();
            result.put(to, source.get(from));
        }
    }

    public static void transformOutputsFile(final JSONObject outputsContents, final JSONObject mapping,
            final Map<String, Object> result) {
        JSONObject outputs = outputsContents.getJSONObject("outputs");
        JSONObject capabilities = outputsContents.getJSONObject("capabilities");
        JSONObject outputMap = mapping.getJSONObject("outputs");
        JSONObject capsMap = mapping.getJSONObject("capabilities");
        transform(outputMap, result, outputs);
        transform(capsMap, result, capabilities);
    }

    public static Map<String, Object> createInputsMap(
            final FilePath workspace, final TaskListener listener, final String inputsText, final String inputsFile,
            final String mapping, final String mappingFile) throws IOException, InterruptedException {
        PrintStream jenkinsLog = listener.getLogger();
        Map<String, Object> inputsMap = new HashMap<String, Object>();
        if (StringUtils.isNotBlank(inputsText)) {
            inputsMap.putAll(readYamlOrJson(inputsText));
        }
        if (StringUtils.isNotBlank(inputsFile)) {
            FilePath expectedLocation = workspace.child(inputsFile);
            if (expectedLocation.exists()) {
                jenkinsLog.println(String.format("Reading inputs from %s", expectedLocation));
                JSONObject inputsFromFile = CloudifyPluginUtilities.readYamlOrJson(expectedLocation);
                JSONObject mappingJson = createMapping(workspace, mapping, mappingFile);
                if (mappingJson != null) {
                    transformOutputsFile(inputsFromFile, mappingJson, inputsMap);
                }
            } else {
                jenkinsLog.println(String.format("Deployment inputs file not found, skipping: %s", inputsFile));
            }
        }
        return inputsMap;
    }

    /**
     * Create a Cloudify environment.
     * 
     * @param listener        Jenkins task listener
     * @param workspace       Jenkins workspace location
     * @param client          Cloudify client object
     * @param blueprintId     blueprint ID
     * @param deploymentId    deployment ID
     * @param inputs          deployment inputs
     * @param inputsLocation  location of file containing deployment inputs
     * @param mapping         YAML/JSON string containing input mappings
     * @param mappingLocation location of input mappings file
     * @param outputsLocation location of outputs file
     * @param echoOutputs     whether to echo outputs to the build log
     * @param debugOutput     whether to emit debug-level logging
     * 
     * @return A {@link CloudifyEnvironmentData} instance containing information about the new environment.
     * 
     * @throws IOException          Percolated from called code.
     * @throws InterruptedException Percolated from called code.
     */
    public static CloudifyEnvironmentData createEnvironment(
            TaskListener listener,
            FilePath workspace,
            CloudifyClient client,
            String blueprintId,
            String deploymentId,
            String inputs,
            String inputsLocation,
            String mapping,
            String mappingLocation,
            String outputsLocation,
            boolean echoOutputs,
            boolean debugOutput) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();

        Map<String, Object> inputsMap = CloudifyPluginUtilities.createInputsMap(
                workspace, listener, inputs, inputsLocation,
                mapping, mappingLocation);
        ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(
                client, logger, debugOutput ? EventLevel.debug : EventLevel.info);

        try {
            logger.println(String.format(
                    "Creating deployment '%s' from blueprint '%s' using the following inputs: %s",
                    deploymentId, blueprintId, JSONObject.fromObject(inputsMap).toString(4)));
            Deployment deployment = DeploymentsHelper.createDeploymentAndWait(client, deploymentId, blueprintId,
                    inputsMap, follower);
            Execution execution = ExecutionsHelper.install(client, deployment.getId(), follower);
            ExecutionsHelper.validate(execution, "Environment setup failed");

            DeploymentsClient deploymentsClient = client.getDeploymentsClient();
            Map<String, Object> outputs = deploymentsClient.getOutputs(deployment);
            Map<String, Object> capabilities = deploymentsClient.getCapabilities(deployment);

            CloudifyEnvironmentData data = new CloudifyEnvironmentData(deployment, outputs, capabilities);

            JSONObject outputContents = new JSONObject();
            outputContents.put("outputs", outputs);
            outputContents.put("capabilities", capabilities);

            if (echoOutputs) {
                logger.println(
                        String.format(
                                "Outputs and capabilities: %s", outputContents.toString(4)));
            }
            if (StringUtils.isNotBlank(outputsLocation)) {
                FilePath outputFilePath = workspace.child(outputsLocation);
                logger.println(String.format(
                        "Writing outputs and capabilities to %s", outputFilePath));
                CloudifyPluginUtilities.writeJson(outputContents, outputFilePath);
            }

            return data;
        } catch (IOException | InterruptedException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace(logger);
            throw new AbortException("Failed during environment creation");
        }
    }

    /**
     * Delete an environment.
     * 
     * @param listener      Jenkins task listener
     * @param client        Cloudify client object
     * @param deploymentId  deployment ID
     * @param ignoreFailure whether to ignore failures during deletion
     * @param debugOutput   emit debug statements
     * 
     * @throws IOException          Percolated from called code
     * @throws InterruptedException Percolated from called code
     */
    public static void deleteEnvironment(
            final TaskListener listener,
            final CloudifyClient client,
            final String deploymentId,
            final Boolean ignoreFailure,
            final boolean debugOutput) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(
                client, logger, debugOutput ? EventLevel.debug : EventLevel.info);
        try {
            logger.println(String.format("Uninstalling Cloudify environment; deployment ID: %s", deploymentId));
            Execution execution = ExecutionsHelper.uninstall(client, deploymentId, ignoreFailure, follower);
            ExecutionsHelper.validate(execution, "Failed tearing down environment");
            logger.println(String.format("Deleting deployment: %s", deploymentId));
            DeploymentsHelper.deleteDeploymentAndWait(client, deploymentId);
        } catch (Exception ex) {
            // Print the stack trace, as AbortException doesn't support
            // root causes and we don't want to lose the root cause.
            ex.printStackTrace(logger);
            throw new AbortException("Failed tearing down environment");
        }
    }

    /**
     * Validates whether a string is a valid YAML or JSON.
     * 
     * @param value value to check
     * 
     * @return A {@link FormValidation} instance representing the result.
     */
    public static FormValidation validateStringIsYamlOrJson(final String value) {
        if (StringUtils.isNotBlank(value)) {
            try {
                readYamlOrJson(value);
            } catch (JSONException ex) {
                return FormValidation.error("Invalid YAML/JSON string");
            }
        }
        return FormValidation.ok();
    }
}
