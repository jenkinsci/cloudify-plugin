package co.cloudify.jenkins.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

import hudson.FilePath;
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
}
