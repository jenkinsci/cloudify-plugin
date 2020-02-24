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
 */
public abstract class CloudifyBuildStep extends Builder {
	/**
	 * This should be the main, "real" implementation of {@link #perform(AbstractBuild, Launcher, BuildListener)}.
	 * Implementations need not worry about using the listener, or handle top-level exceptions; this is
	 * done by the wrapper.
	 * 
	 * @param	build			build object, as given by Jenkins
	 * @param	launcher		launcher object, as given by Jenkins
	 * @param	listener		listener object, as given by Jenkins
	 * @param	cloudifyClient	a {@link CloudifyClient} instance pointing at the Cloudify Manager
	 * 							installation, populated during configuration
	 * @throws	Exception		May be anything; unified handling is done in {@link #perform(AbstractBuild, Launcher, BuildListener)}
	 */
	protected abstract void perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener,
			CloudifyClient cloudifyClient) throws Exception;
	
	/**
	 * Generic implementation of a Cloudify build step: perform some boilerplate code
	 * before and after the main implementation.
	 */
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
