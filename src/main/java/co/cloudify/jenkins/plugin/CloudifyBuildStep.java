package co.cloudify.jenkins.plugin;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import co.cloudify.rest.client.CloudifyClient;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;

/**
 * Consolidates some boilerplate code.
 * 
 * @author Isaac Shabtay
 */
public abstract class CloudifyBuildStep extends Builder implements SimpleBuildStep {
    // We accept either the credentials ID, or a username/password pair.
    // While using a credentials ID would be easier in most cases, I found at
    // least one case when this doesn't work: using user-scoped credentials within
    // pipelines. Apparently, findCredentialsById won't work for these (but it works
    // for freestyle jobs).
    // Therefore, we allow both forms, so users can pick what works best for them.
    private String credentialsId;
    private String username;
    private String password;
    private String tenant;

    public String getTenant() {
        return tenant;
    }

    @DataBoundSetter
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
    }

    @DataBoundSetter
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * By default, a Cloudifys step requires a {@link CloudifyClient} instance to operate.
     * Otherwise, the step should override this method to return <code>false</code>.
     * 
     * @return <code>true</code> If a {@link CloudifyClient} instance should
     *         be prepared and passed to the actual step.
     */
    protected boolean isCloudifyClientRequired() {
        return true;
    }

    /**
     * This should be the main, "real" implementation of
     * {@link #perform(AbstractBuild, Launcher, BuildListener)}. Implementations
     * need not worry about using the listener, or handle top-level exceptions; this
     * is done by the wrapper.
     * 
     * @param build          build object, as given by Jenkins
     * @param launcher       launcher object, as given by Jenkins
     * @param listener       listener object, as given by Jenkins
     * @param cloudifyClient a {@link CloudifyClient} instance pointing at the
     *                       Cloudify Manager installation, populated during
     *                       configuration
     * 
     * @throws Exception May be anything; unified handling is done in
     *                   {@link #perform(AbstractBuild, Launcher, BuildListener)}
     */
    protected abstract void performImpl(Run<?, ?> run, Launcher launcher, TaskListener listener,
            FilePath workspace, EnvVars envVars, CloudifyClient cloudifyClient) throws Exception;

    private CloudifyClient getCloudifyClient(final Run<?, ?> run) throws AbortException {
        if (!isCloudifyClientRequired()) {
            return null;
        }

        String username, password;
        if (StringUtils.isNotBlank(this.username)) {
            username = this.username;
            password = this.password;
        } else if (StringUtils.isNotBlank(credentialsId)) {
            StandardUsernamePasswordCredentials creds = CloudifyPluginUtilities.getCredentials(credentialsId, run);
            username = creds.getUsername();
            password = creds.getPassword().getPlainText();
        } else {
            throw new AbortException("Neither credentialsId nor username/password were provided");
        }

        return CloudifyConfiguration.getCloudifyClient(
                StringUtils.trimToNull(username),
                StringUtils.trimToNull(password),
                StringUtils.trimToNull(tenant));
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        CloudifyClient client = getCloudifyClient(run);
        try {
            performImpl(run, launcher, listener, workspace, run.getEnvironment(listener), client);
        } catch (IOException | InterruptedException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace(listener.getLogger());
            throw new AbortException("Failed performing step");
        }
    }

    /**
     * Generic implementation of a Cloudify build step: perform some boilerplate
     * code before and after the main implementation.
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        listener.started(Arrays.asList(new Cause.UserIdCause()));
        CloudifyClient client = getCloudifyClient(build);
        EnvVars envVars = CloudifyPluginUtilities.getEnvironment(build, listener);

        try {
            performImpl(build, launcher, listener, build.getWorkspace(), envVars, client);
            listener.finished(Result.SUCCESS);
            return true;
        } catch (IOException | InterruptedException ex) {
            // Print to the build log, as Jenkins doesn't do this.
            // This is good for diagnostics.
            ex.printStackTrace(listener.getLogger());
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace(listener.getLogger());
            listener.finished(Result.FAILURE);
            throw new AbortException(String.format("Unexpected exception was raised: [%s]", ex));
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("credentialsId", credentialsId)
                .append("tenant", tenant)
                .toString();
    }
}
