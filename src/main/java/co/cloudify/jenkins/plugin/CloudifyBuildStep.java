package co.cloudify.jenkins.plugin;

import java.io.IOException;

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
    private String credentialsId;
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

    /**
     * By default, a Cloudify step requires a {@link CloudifyClient} instance to
     * operate. Otherwise, the step should override this method to return
     * <code>false</code>.
     * 
     * @return <code>true</code> If a {@link CloudifyClient} instance should be
     *         prepared and passed to the actual step.
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
     * @param run            build object, as given by Jenkins
     * @param launcher       launcher object, as given by Jenkins
     * @param listener       listener object, as given by Jenkins
     * @param workspace      path to Jenkins workspace
     * @param envVars        build's environment variables
     * @param cloudifyClient a {@link CloudifyClient} instance pointing at the
     *                       Cloudify Manager installation, populated during
     *                       configuration
     * 
     * @throws Exception May be anything; unified handling is done in
     *                   {@link #perform(AbstractBuild, Launcher, BuildListener)}
     */
    protected abstract void performImpl(Run<?, ?> run, Launcher launcher, TaskListener listener, FilePath workspace,
            EnvVars envVars, CloudifyClient cloudifyClient) throws Exception;

    private CloudifyClient getCloudifyClient(final Run<?, ?> run) throws AbortException {
        if (!isCloudifyClientRequired()) {
            return null;
        }

        if (StringUtils.isBlank(credentialsId)) {
            throw new AbortException("Neither credentialsId nor username/password were provided");
        }

        StandardUsernamePasswordCredentials creds = CloudifyPluginUtilities
                .getUsernamePasswordCredentials(credentialsId, run);

        return CloudifyConfiguration.getCloudifyClient(StringUtils.trimToNull(creds.getUsername()),
                StringUtils.trimToNull(creds.getPassword().getPlainText()), StringUtils.trimToNull(tenant));
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

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("credentialsId", credentialsId).append("tenant", tenant).toString();
    }
}
