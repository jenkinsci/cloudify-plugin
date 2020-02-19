package co.cloudify.jenkins.plugin;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

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
	public static void writeJson(final Object object, final File outputFile) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.registerModule(new JaxbAnnotationModule());
		mapper.writeValue(outputFile, object);
	}
}
