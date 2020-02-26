package co.cloudify.jenkins.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

import co.cloudify.rest.client.CloudifyClient;
import co.cloudify.rest.client.DeploymentsClient;
import co.cloudify.rest.helpers.DeploymentsHelper;
import co.cloudify.rest.helpers.ExecutionFollowCallback;
import co.cloudify.rest.helpers.ExecutionsHelper;
import co.cloudify.rest.helpers.PrintStreamLogEmitterExecutionFollower;
import co.cloudify.rest.model.Deployment;
import co.cloudify.rest.model.Execution;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.TaskListener;
import net.sf.json.JSONObject;

public class CloudifyPluginUtilities {
	/**
	 * Write a JAXB-annotated object to a file as JSON.
	 * We isolate this functionality to one class only, as it uses facilities
	 * that are not a part of the JDK and are likely to be included as standard
	 * in the future.
	 * 
	 * @param object     object to serialize
	 * @param outputFile file to write to
	 * 
	 * @throws	IOException	Some I/O error has occured.
	 */
	public static void writeBoundObject(final Object object, final File outputFile) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.registerModule(new JaxbAnnotationModule());
		mapper.writeValue(outputFile, object);
	}
	
	/**
	 * Reads a JSON file from a {@link FilePath}.
	 * 
	 * @param	path	path to the resource
	 * 
	 * @return	A JSON object.
	 * 
	 * @throws	IOException				May be thrown by underlying framework.
	 * @throws	InterruptedException	May be thrown by underlying framework.
	 */
	public static JSONObject readJson(final FilePath path) throws IOException, InterruptedException {
		try (InputStream is = path.read()) {
			return JSONObject.fromObject(IOUtils.toString(is, StandardCharsets.UTF_8));
		}
	}
	
	/**
	 * Writes a JSON object to a {@link FilePath}.
	 * 
	 * @param	object	JSON object to write
	 * @param	path	path to write to
	 * 
	 * @throws	IOException	Some problem occured during serialization.
	 */
	public static void writeJson(final JSONObject object, final FilePath path) throws IOException, InterruptedException {
		try (OutputStreamWriter os = new OutputStreamWriter(path.write())) {
			os.write(object.toString(4));
		}
	}
	
	public static Map<String, Object> createInputsMap(final FilePath workspace, final TaskListener listener, final String inputsText, final String inputsFile) throws IOException, InterruptedException {
		PrintStream jenkinsLog = listener.getLogger();
		Map<String, Object> inputsMap = new HashMap<String, Object>();
		if (StringUtils.isNotBlank(inputsText)) {
			inputsMap.putAll(
					JSONObject.fromObject(inputsText));
		}
		if (StringUtils.isNotBlank(inputsFile)) {
			FilePath expectedLocation = workspace.child(inputsFile);
			if (expectedLocation.exists()) {
				jenkinsLog.println(String.format("Reading inputs from %s", expectedLocation));
				inputsMap.putAll(CloudifyPluginUtilities.readJson(expectedLocation));
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
			String inputs,
			String inputsLocation,
			String outputsLocation
			) throws IOException, InterruptedException {
		PrintStream logger = listener.getLogger();

		Map<String, Object> inputsMap = CloudifyPluginUtilities.createInputsMap(workspace, listener, inputs, inputsLocation);
		ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(client, logger);
		
		try {
			Deployment deployment = DeploymentsHelper.createDeploymentAndWait(client, deploymentId, blueprintId, inputsMap, follower);
			Execution execution = ExecutionsHelper.install(client, deployment.getId(), follower);
			ExecutionsHelper.validate(execution, "Environment setup failed");
			
			DeploymentsClient deploymentsClient = client.getDeploymentsClient();
			Map<String, Object> outputs = deploymentsClient.getOutputs(deployment);
			Map<String, Object> capabilities = deploymentsClient.getCapabilities(deployment);
			
			CloudifyEnvironmentData data = new CloudifyEnvironmentData(deployment, outputs, capabilities);

			if (StringUtils.isNotBlank(outputsLocation)) {
				JSONObject outputContents = new JSONObject();
				outputContents.put("outputs", outputs);
				outputContents.put("capabilities", capabilities);
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
	
	public static void deleteEnvironment(
			final TaskListener listener,
			final CloudifyClient client,
			final String deploymentId,
			final Boolean ignoreFailure
			) throws IOException, InterruptedException {
		PrintStream logger = listener.getLogger();
		ExecutionFollowCallback follower = new PrintStreamLogEmitterExecutionFollower(client, logger);
		
		try {
			logger.println(String.format("Uninstalling Cloudify environment; deployment ID: %s", deploymentId));
			Execution execution = ExecutionsHelper.uninstall(client, deploymentId, ignoreFailure, follower);
			ExecutionsHelper.validate(execution, "Failed tearing down environment");
			logger.println(String.format("Deleting deployment: %s", deploymentId));
			DeploymentsHelper.deleteDeploymentAndWait(client, deploymentId);
		} catch (Exception ex) {
			ex.printStackTrace(logger);
			throw new AbortException("Failed tearing down environment");
		}
	}
}
