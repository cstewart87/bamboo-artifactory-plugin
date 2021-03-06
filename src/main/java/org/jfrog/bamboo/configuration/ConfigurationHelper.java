package org.jfrog.bamboo.configuration;

import com.atlassian.bamboo.configuration.AdministrationConfigurationManager;
import com.atlassian.bamboo.utils.EscapeChars;
import com.google.common.collect.Maps;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.jfrog.bamboo.util.ConstantValues;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.jfrog.bamboo.util.ConstantValues.ADMIN_CONFIG_SERVLET_CONTEXT_NAME;

/**
 * A helper class to be used for the Artifactory tasks configuration.
 */
public class ConfigurationHelper implements Serializable {
    private static ConfigurationHelper instance = new ConfigurationHelper();
    private AdministrationConfigurationManager administrationConfigurationManager;
    private HttpClient httpClient = new HttpClient();

    private ConfigurationHelper() {
    }

    public static ConfigurationHelper getInstance() {
        return instance;
    }

    public void setAdministrationConfigurationManager(AdministrationConfigurationManager administrationConfigurationManager) {
        this.administrationConfigurationManager = administrationConfigurationManager;
    }

    public BuildJdkOverride getBuildJdkOverride(String planKey) {
        Map<String, String> variables = getAllVariables(planKey);

        BuildJdkOverride override = new BuildJdkOverride();
        override.setOverride(Boolean.valueOf(variables.get(BuildJdkOverride.SHOULD_OVERRIDE_JDK_KEY)));
        String envVar = variables.get(BuildJdkOverride.OVERRIDE_JDK_ENV_VAR_KEY);
        override.setOverrideWithEnvVarName(envVar == null ? "JAVA_HOME" : envVar);

        return override;
    }

    public Map<String, String> getAllVariables(String planKey) {
        HashMap<String, String> params = Maps.newHashMap();
        params.put(ConstantValues.PLAN_KEY_PARAM, planKey);
        String requestUrl = prepareRequestUrl(ADMIN_CONFIG_SERVLET_CONTEXT_NAME, params);
        GetMethod getMethod = new GetMethod(requestUrl);
        InputStream responseStream = null;
        try {
            executeMethod(requestUrl, getMethod);

            JsonFactory jsonFactory = new JsonFactory();
            ObjectMapper mapper = new ObjectMapper();
            jsonFactory.setCodec(mapper);

            responseStream = getMethod.getResponseBodyAsStream();
            if (responseStream == null) {
                return Maps.newHashMap();
            }

            JsonParser parser = jsonFactory.createJsonParser(responseStream);
            return parser.readValueAs(Map.class);
        } catch (IOException ioe) {
            return Maps.newHashMap();
        } finally {
            getMethod.releaseConnection();
            IOUtils.closeQuietly(responseStream);
        }
    }

    private String prepareRequestUrl(String servletName, Map<String, String> params) {
        String bambooBaseUrl = administrationConfigurationManager.getAdministrationConfiguration().getBaseUrl();
        StringBuilder builder = new StringBuilder(bambooBaseUrl);
        if (!bambooBaseUrl.endsWith("/")) {
            builder.append("/");
        }
        StringBuilder requestUrlBuilder = builder.append("plugins/servlet/").append(servletName);
        if (params.size() != 0) {
            requestUrlBuilder.append("?");

            for (Map.Entry<String, String> param : params.entrySet()) {
                if (!requestUrlBuilder.toString().endsWith("?")) {
                    requestUrlBuilder.append("&");
                }
                requestUrlBuilder.append(param.getKey()).append("=").append(EscapeChars.forURL(param.getValue()));
            }
        }

        return requestUrlBuilder.toString();
    }

    /**
     * Executes the given HTTP method
     *
     * @param requestUrl Full request URL
     * @param getMethod  HTTP GET method
     */
    private void executeMethod(String requestUrl, GetMethod getMethod) throws IOException {
        int responseCode = httpClient.executeMethod(getMethod);
        if (responseCode == HttpStatus.SC_NOT_FOUND) {
            throw new IOException("Unable to find requested resource: " + requestUrl);
        } else if (responseCode != HttpStatus.SC_OK) {
            throw new IOException("Failed to retrieve requested resource: " + requestUrl + ". Response code: " +
                    responseCode + ", Message: " + getMethod.getStatusText());
        }
    }
}
