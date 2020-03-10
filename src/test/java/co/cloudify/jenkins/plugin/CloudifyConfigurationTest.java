package co.cloudify.jenkins.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;

public class CloudifyConfigurationTest {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    /**
     * Tries to exercise enough code paths to catch common mistakes:
     * <ul>
     * <li>missing {@code load}
     * <li>missing {@code save}
     * <li>misnamed or absent getter/setter
     * <li>misnamed {@code textbox}
     * </ul>
     */
    @Test
    public void uiAndStorage() {
        rr.then(r -> {
            CloudifyConfiguration cloudifyConfiguration = CloudifyConfiguration.get();
            assertNull("not set initially", cloudifyConfiguration.getHost());
            HtmlForm config = r.createWebClient().goTo("configure").getFormByName("config");
            HtmlTextInput hostTextBox = config.getInputByName("_.host");
            hostTextBox.setText("hello");
            HtmlTextInput usernameTextBox = config.getInputByName("_.username");
            usernameTextBox.setText("hello");
            HtmlPasswordInput passwordTextBox = config.getInputByName("_.password");
            passwordTextBox.setText("hello");
            HtmlTextInput tenantTextBox = config.getInputByName("_.tenant");
            tenantTextBox.setText("hello");
            HtmlCheckBoxInput securedCheckBox = config.getInputByName("_.secured");
            securedCheckBox.setChecked(true);
            r.submit(config);
            assertEquals("global config page let us edit it", "hello", cloudifyConfiguration.getHost());
        });
        rr.then(r -> {
            assertEquals("still there after restart of Jenkins", "hello", CloudifyConfiguration.get().getHost());
        });
    }

}
