package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.client.DeploymentsClient;
import co.cloudify.rest.helpers.DefaultExecutionFollowCallback;
import co.cloudify.rest.helpers.DeploymentsHelper;
import co.cloudify.rest.helpers.ExecutionFollowCallback;
import co.cloudify.rest.helpers.ExecutionsHelper;
import co.cloudify.rest.helpers.PrintStreamLogEmitterExecutionFollower;
import co.cloudify.rest.model.Deployment;
import co.cloudify.rest.model.EventLevel;
import co.cloudify.rest.model.Execution;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.VariableResolver;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * Various utility methods used throughout the plugin.
 * 
 * @author Isaac Shabtay
 */
public class CloudifyPluginUtilities {
    public static StandardUsernamePasswordCredentials getCredentials(final String credentialsId, final Run<?, ?> run) {
        StandardUsernamePasswordCredentials creds = CredentialsProvider.findCredentialById(
                credentialsId,
                StandardUsernamePasswordCredentials.class,
                run,
                Collections.EMPTY_LIST);
        if (creds == null) {
            throw new IllegalArgumentException(String.format("Couldn't find credentials by ID: '%s'", credentialsId));
        }
        return creds;
    }

    public static EnvVars getEnvironment(final AbstractBuild build, final TaskListener listener)
            throws InterruptedException, IOException {
        EnvVars environment = build.getEnvironment(listener);
        // This results in empty variables to disappear from the environment,
        // causing them to not be expanded at all ("${inputs}" will be returned as is).
//        environment.overrideAll(build.getBuildVariables());
        return environment;
    }

    /**
     * Returns a combined map from file contents, string contents and an actual {@link Map}.
     */
    public static Map<String, Object> getCombinedMap(final FilePath workspace, final String filename, final String str,
            final Map<String, ?> map) throws IOException, InterruptedException {
        Map<String, Object> m = readYamlOrJson(workspace, filename, str);
        if (map != null) {
            m.putAll(map);
        }
        return m;
    }

    /**
     * Process a UI input string, by replacing macros in it and trimming it.
     * A resultant empty string will be returned as <code>null</code>.
     * 
     * @param s        some string
     * @param resolver build's variable resolver
     * 
     * @return Cleaned-up string, or <code>null</code> if the result was nothing.
     */
    public static String parseInput(final String s, final VariableResolver<String> resolver) {
        return StringUtils.trimToNull(
                s != null ? Util.replaceMacro(s, resolver) : null);
    }

    /**
     * Get an {@link ExecutionFollowCallback} instance based on user parameters.
     * 
     * @param printLogs      should event/logs be printed?
     * @param debugOutput    should debug-level logs be printed
     * @param cloudifyClient REST client
     * @param jenkinsLog     Jenkins' log stream
     * 
     * @return A suitable {@link ExecutionFollowCallback} to use.
     */
    public static ExecutionFollowCallback getExecutionFollowCallback(
            final boolean printLogs,
            final boolean debugOutput,
            final CloudifyClient cloudifyClient,
            final PrintStream jenkinsLog) {
        ExecutionFollowCallback callback = printLogs
                ? new PrintStreamLogEmitterExecutionFollower(cloudifyClient, jenkinsLog,
                        debugOutput ? EventLevel.debug : EventLevel.info)
                : DefaultExecutionFollowCallback.getInstance();
        return callback;
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
    public static void writeJson(final JsonObject object, final FilePath path)
            throws IOException, InterruptedException {
        JsonWriterFactory fac = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
        try (OutputStreamWriter os = new OutputStreamWriter(path.write(), StandardCharsets.UTF_8);
                JsonWriter jw = fac.createWriter(os)) {
            jw.writeObject(object);
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
    public static Map<String, Object> readYamlOrJson(final FilePath path) throws IOException, InterruptedException {
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
    public static Map<String, Object> readYamlOrJson(final String str) {
        if (StringUtils.isBlank(str)) {
            return Collections.EMPTY_MAP;
        }
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(str, JSONObject.class);
        } catch (IOException ex) {
            return JSONObject.fromObject(str);
        }
    }

    /**
     * Creates a {@link Map} from a combination of a file and string contents.
     * 
     * @param workspace    build's workspace root
     * @param contents     YAML/JSON contents, as a string (may be <code>null</code>)
     * @param contentsFile workspace location of a YAML/JSON file (may be <code>null</code>,
     *                     may not exist)
     * 
     * @return Combined {@link Map}.
     * 
     * @throws IOException          Thrown by underlying code.
     * @throws InterruptedException Thrown by underlying code.
     */
    public static <T> Map<String, T> readYamlOrJson(final FilePath workspace, final String contentsFile,
            final String contents) throws IOException, InterruptedException {
        Map mapping = new LinkedHashMap<>();
        if (contentsFile != null) {
            Validate.notNull(workspace, "'contentsFile' was provided, but workspace location is null");
            FilePath contentsFilePath = workspace.child(contentsFile);
            mapping.putAll(readYamlOrJson(contentsFilePath));
        }
        if (contents != null) {
            mapping.putAll(readYamlOrJson(contents));
        }
        return mapping;
    }

    protected static void transform(Map<String, String> mapping, Map<String, Object> result,
            Map<String, Object> source) {
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String from = entry.getKey();
            String to = entry.getValue();
            result.put(to, source.get(from));
        }
    }

    /**
     * Transform a standard outputs/capabilities file by using a mapping.
     * 
     * @param outputsContents outputs/capabilities
     * @param mapping         mapping structure
     * @param result          {@link Map} to populate with results
     */
    public static void transformOutputsFile(final FilePath outputsFile, final Map<String, Map<String, String>> mapping,
            final Map<String, Object> results) throws IOException, InterruptedException {
        Map<String, Object> outputsContents = readYamlOrJson(outputsFile);
        for (Map.Entry<String, Map<String, String>> entry : mapping.entrySet()) {
            transform(entry.getValue(), results,
                    (Map<String, Object>) outputsContents.getOrDefault(entry.getKey(), Collections.EMPTY_MAP));
        }
    }

    /**
     * Given inputs in the form of a string, and in a form of a file, return a {@link Map}
     * of combined inputs.
     * 
     * @param workspace   Jenkins' build workspace
     * @param listener    Jenkins' task listener
     * @param inputsText  inputs as a string, in YAML/JSON format (may be <code>null</code>)
     * @param inputsFile  inputs as a YAML/JSON file (may be <code>null</code>)
     * @param mapping     inputs mapping, as a YAML/JSON string (may be <code>null</code>)
     * @param mappingFile inputs mapping, as a YAML/JSON file (may be <code>null</code>)
     * 
     * @return A {@link Map} representing combined inputs.
     * 
     * @throws IOException          Thrown by underlying code.
     * @throws InterruptedException Thrown by underlying code.
     */
    public static Map<String, Object> createInputsMap(
            final FilePath workspace, final TaskListener listener, final String inputsText, final String inputsFile,
            final String mapping, final String mappingFile) throws IOException, InterruptedException {
        PrintStream jenkinsLog = listener.getLogger();
        Map<String, Object> inputsMap = new HashMap<String, Object>();
        if (inputsText != null) {
            inputsMap.putAll(readYamlOrJson(inputsText));
        }
        if (inputsFile != null) {
            FilePath expectedLocation = workspace.child(inputsFile);
            if (expectedLocation.exists()) {
                jenkinsLog.println(String.format("Reading inputs from %s", expectedLocation));
                Map<String, Map<String, String>> mappingJson = readYamlOrJson(workspace, mappingFile, mapping);
                if (mappingJson != null) {
                    transformOutputsFile(expectedLocation, mappingJson, inputsMap);
                }
            } else {
                jenkinsLog.println(String.format("Deployment inputs file not found, skipping: %s", inputsFile));
            }
        }
        return inputsMap;
    }

    public static CloudifyEnvironmentData createEnvironment(
            TaskListener listener,
            FilePath workspace,
            CloudifyClient client,
            String blueprintId,
            String deploymentId,
            Map<String, Object> inputs,
            String outputsLocation,
            boolean echoInputs,
            boolean echoOutputs,
            boolean debugOutput) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        ExecutionFollowCallback follower = CloudifyPluginUtilities.getExecutionFollowCallback(true,
                debugOutput, client, logger);

        try {
            logger.println(
                    echoInputs ? String.format(
                            "Creating deployment '%s' from blueprint '%s' using the following inputs: %s",
                            deploymentId, blueprintId, JSONObject.fromObject(inputs).toString(4))
                            : String.format(
                                    "Creating deployment '%s' from blueprint '%s'",
                                    deploymentId, blueprintId));
            Deployment deployment = DeploymentsHelper.createDeploymentAndWait(client, deploymentId, blueprintId,
                    inputs, follower, ExecutionsHelper.DEFAULT_POLLING_INTERVAL);
            Execution execution = ExecutionsHelper.install(client, deployment.getId(), follower,
                    ExecutionsHelper.DEFAULT_POLLING_INTERVAL);
            ExecutionsHelper.validateCompleted(execution, "Environment setup failed");

            DeploymentsClient deploymentsClient = client.getDeploymentsClient();
            Map<String, Object> outputs = deploymentsClient.getOutputs(deployment);
            Map<String, Object> capabilities = deploymentsClient.getCapabilities(deployment);

            CloudifyEnvironmentData data = new CloudifyEnvironmentData(deployment, outputs, capabilities);

            JsonObject dataJsonObject = data.toJson();

            if (echoOutputs) {
                logger.println(String.format("Environment data: %s",
                        toString(dataJsonObject)));
            }
            if (outputsLocation != null) {
                FilePath outputFilePath = workspace.child(outputsLocation);
                logger.println(String.format(
                        "Writing environment data to %s", outputFilePath));
                CloudifyPluginUtilities.writeJson(dataJsonObject, outputFilePath);
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
            boolean echoInputs,
            boolean echoOutputs,
            boolean debugOutput) throws IOException, InterruptedException {
        Map<String, Object> inputsMap = CloudifyPluginUtilities.createInputsMap(
                workspace, listener, inputs, inputsLocation,
                mapping, mappingLocation);
        return createEnvironment(listener, workspace, client, blueprintId, deploymentId, inputsMap, outputsLocation,
                echoInputs, echoOutputs, debugOutput);
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
            final long pollingInterval,
            final Boolean ignoreFailure,
            final boolean debugOutput) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        ExecutionFollowCallback follower = CloudifyPluginUtilities.getExecutionFollowCallback(true,
                debugOutput, client, logger);
        try {
            logger.println(String.format("Uninstalling Cloudify environment; deployment ID: %s", deploymentId));
            Execution execution = ExecutionsHelper.uninstall(client, deploymentId, ignoreFailure, follower,
                    ExecutionsHelper.DEFAULT_POLLING_INTERVAL);
            ExecutionsHelper.validateCompleted(execution, "Failed tearing down environment");
            logger.println(String.format("Deleting deployment: %s", deploymentId));
            DeploymentsHelper.deleteDeploymentAndWait(client, deploymentId, pollingInterval);
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

    public static JsonObject jsonFromMap(final Map<String, Object> map) {
        return Json.createObjectBuilder(map).build();
    }

    /**
     * Convert a JSON to a string.
     * Theoretically, I'd have liked to use {@link JsonGenerator} on Jenkins'
     * logger, however {@link JsonGenerator} closes the stream when it's
     * {@link JsonGenerator#close()} method is called. That causes Jenkins'
     * log to stop working.
     * 
     * @param json JSON object to convert to a string.
     * 
     * @return String representation of the JSON object.
     */
    public static String toString(final JsonObject json) {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = Json
                .createGeneratorFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true))
                .createGenerator(sw)) {
            gen.write(json);
        }
        return sw.toString();
    }

    public static String getPassword(final Secret secret, final String s) {
        if (secret != null) {
            return secret.getPlainText();
        }
        return s;
    }

    /**
     * Expand a string input, for variables.
     * 
     * @param value string to expand
     * 
     * @return Expanded value.
     */
    public static String expandString(final EnvVars envVars, final String value) {
        if (envVars != null) {
            return StringUtils.trimToNull(envVars.expand(value));
        }
        return value;
    }
}
