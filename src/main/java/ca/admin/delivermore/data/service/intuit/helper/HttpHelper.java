package ca.admin.delivermore.data.service.intuit.helper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.admin.delivermore.collector.data.Config;
import ca.admin.delivermore.data.service.intuit.domain.OAuth2Configuration;

@Service
public class HttpHelper {

    @Autowired
    public OAuth2Configuration oAuth2Configuration;

    public HttpPost addHeader(HttpPost post) {
        String clientCredentials = oAuth2Configuration.getAppClientId() + ":" + oAuth2Configuration.getAppClientSecret();
        String base64ClientIdSec = Base64.getEncoder().encodeToString(clientCredentials.getBytes(StandardCharsets.UTF_8));
        post.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        post.setHeader("Authorization", "Basic " + base64ClientIdSec);
        post.setHeader("Accept", "application/json");
        return post;
    }

    public List<NameValuePair> getUrlParameters(String action) {
        List<NameValuePair> urlParameters = new ArrayList<>();
        String refreshToken = Config.getInstance().getQBORefreshToken();
        if ("revoke".equals(action)) {
            urlParameters.add(new BasicNameValuePair("token", refreshToken));
        } else if ("refresh".equals(action)) {
            urlParameters.add(new BasicNameValuePair("refresh_token", refreshToken));
            urlParameters.add(new BasicNameValuePair("grant_type", "refresh_token"));
        } else {
            String auth_code = Config.getInstance().getQBOAuthCode();
            urlParameters.add(new BasicNameValuePair("code", auth_code));
            urlParameters.add(new BasicNameValuePair("redirect_uri", oAuth2Configuration.getAppRedirectUri()));
            urlParameters.add(new BasicNameValuePair("grant_type", "authorization_code"));
        }
        return urlParameters;
    }

    public StringBuffer getResult(HttpResponse response) throws IOException {
        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        return result;
    }

}
