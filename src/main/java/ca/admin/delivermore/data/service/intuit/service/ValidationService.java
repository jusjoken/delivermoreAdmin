package ca.admin.delivermore.data.service.intuit.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.admin.delivermore.data.service.intuit.domain.OAuth2Configuration;
import ca.admin.delivermore.data.service.intuit.helper.HttpHelper;

@Service
public class ValidationService {

    @Autowired
    public OAuth2Configuration oAuth2Configuration;

    @Autowired
    public HttpHelper httpHelper;

    private static final HttpClient CLIENT = HttpClientBuilder.create().build();
    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    /**
     * Method to validate id token
     *
     * @param idToken
     * @return
     */
    public boolean isValidIDToken(String idToken) {
        logger.debug("IdToken String = "+idToken);
        String[] idTokenParts = idToken.split("\\.");

        if (idTokenParts.length < 3) {
            logger.debug("invalid idTokenParts length");
            return false;
        }

        String idTokenHeader = base64UrlDecode(idTokenParts[0]);
        String idTokenPayload = base64UrlDecode(idTokenParts[1]);
        byte[] idTokenSignature = base64UrlDecodeToBytes(idTokenParts[2]);

        JSONObject idTokenHeaderJson = null;
        try {
            idTokenHeaderJson = new JSONObject(idTokenHeader);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        JSONObject idTokenHeaderPayload = null;
        try {
            idTokenHeaderPayload = new JSONObject(idTokenPayload);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        //Step 1 : First check if the issuer is as mentioned in "issuer" in the discovery doc
        String issuer = null;
        try {
            issuer = idTokenHeaderPayload.getString("iss");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if(!issuer.equalsIgnoreCase(oAuth2Configuration.getIntuitIdTokenIssuer())) {
            logger.debug("issuer value mismtach");
            return false;
        }

        //Step 2 : check if the aud field in idToken is same as application's clientId
        JSONArray jsonaud = null;
        try {
            jsonaud = idTokenHeaderPayload.getJSONArray("aud");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        String aud = null;
        try {
            aud = jsonaud.getString(0);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        if(!aud.equalsIgnoreCase(oAuth2Configuration.getAppClientId())) {
            logger.debug("incorrect client id");
            return false;
        }

        //Step 3 : ensure the timestamp has not elapsed
        Long expirationTimestamp = null;
        try {
            expirationTimestamp = idTokenHeaderPayload.getLong("exp");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        Long currentTime = Instant.now().getEpochSecond();

        if((expirationTimestamp - currentTime) <= 0) {
            logger.debug("expirationTimestamp has elapsed");
            return false;
        }

        //Step 4: Verify that the ID token is properly signed by the issuer
        HashMap<String,JSONObject> keyMap = getKeyMapFromJWKSUri();
        if (keyMap == null || keyMap.isEmpty()) {
            logger.debug("unable to retrive keyMap from JWKS url");
            return false;
        }

        //first get the kid from the header.
        String keyId = null;
        try {
            keyId = idTokenHeaderJson.getString("kid");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        JSONObject keyDetails = keyMap.get(keyId);

        //now get the exponent (e) and modulo (n) to form the PublicKey
        String exponent = null;
        try {
            exponent = keyDetails.getString("e");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        String modulo = null;
        try {
            modulo = keyDetails.getString("n");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        //build the public key
        PublicKey publicKey = getPublicKey(modulo, exponent);

        byte[] data = (idTokenParts[0] + "." + idTokenParts[1]).getBytes(StandardCharsets.UTF_8);

        try {
            //verify token using public key
            boolean isSignatureValid = verifyUsingPublicKey(data, idTokenSignature, publicKey);
            logger.debug("isSignatureValid: " + isSignatureValid);
            return isSignatureValid;
        } catch (GeneralSecurityException e) {
            logger.error("Exception while validating ID token ", e);
            return false;
        }

    }

    private String base64UrlDecode(String input) {
        byte[] decodedBytes = base64UrlDecodeToBytes(input);
        String result = new String(decodedBytes, StandardCharsets.UTF_8);
        return result;
    }

    private byte[] base64UrlDecodeToBytes(String input) {
        return Base64.getUrlDecoder().decode(input);
    }

    private HashMap<String, JSONObject> getKeyMapFromJWKSUri() {

        HttpGet getJwks = new HttpGet(oAuth2Configuration.getIntuitJsksURI());
        getJwks.setHeader("Accept", "application/json");

        try {

            HttpResponse response = CLIENT.execute(getJwks);

            logger.debug("Response Code : "+ response.getStatusLine().getStatusCode());
            if (response.getStatusLine().getStatusCode() != 200) {
                logger.debug("failed JWKS URI");
                return null;
            }

            StringBuffer result = httpHelper.getResult(response);
            logger.debug("raw result for JWKS=" + result);

            HashMap<String, JSONObject> retMap = new HashMap<>();
            JSONObject jwksPayload = new JSONObject(result.toString());
            JSONArray keysArray = jwksPayload.getJSONArray("keys");

            for (int i=0;i<keysArray.length();i++) {
                JSONObject object = keysArray.getJSONObject(i);
                String keyId = object.getString("kid");
                retMap.put(keyId,object);
            }
            return retMap;
        }
        catch (Exception ex) {
            logger.error("Exception while retrieving jwks ", ex);
            return null;
        }
    }

    private PublicKey getPublicKey(String MODULUS, String EXPONENT) {
        byte[] nb = base64UrlDecodeToBytes(MODULUS);
        byte[] eb = base64UrlDecodeToBytes(EXPONENT);
        BigInteger n = new BigInteger(1, nb);
        BigInteger e = new BigInteger(1, eb);

        RSAPublicKeySpec rsaPublicKeySpec = new RSAPublicKeySpec(n, e);
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(rsaPublicKeySpec);
            return publicKey;
        } catch (Exception ex) {
            logger.error("Exception while getting public key ", ex);
            throw new RuntimeException("Cant create public key", ex);
        }
    }

    private boolean verifyUsingPublicKey(byte[] data, byte[] signature, PublicKey pubKey)
            throws GeneralSecurityException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(pubKey);
        sig.update(data);
        return sig.verify(signature);
    }

}
