package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.util.Arrays;

import co.cloudify.rest.client.CloudifyClient;
import hudson.AbortException;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.tasks.Builder;

/**
 * Consolidates some boilerplate code.
 * 
 * @author	Isaac Shabtay
 *
 */
public abstract class CloudifyBuildStep extends Builder {
	protected abstract void perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener,
			CloudifyClient cloudifyClient) throws Exception;
	
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
	        throws InterruptedException, IOException {
		listener.started(Arrays.asList(new Cause.UserIdCause()));
		CloudifyClient client = CloudifyConfiguration.getCloudifyClient();
		
		try {
			perform(build, launcher, listener, client);
			listener.finished(Result.SUCCESS);
			return true;
		} catch (IOException | InterruptedException ex) {
			//	Print to the build log, as Jenkins doesn't do this.
			//	This is good for diagnostics.
			ex.printStackTrace(listener.getLogger());
			throw ex;
		} catch (Exception ex) {
			ex.printStackTrace(listener.getLogger());
			listener.finished(Result.FAILURE);
			throw new AbortException(String.format("Unexpected exception was raised: [%s]", ex));
		}
	}
}
