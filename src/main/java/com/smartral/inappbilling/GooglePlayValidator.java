/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.smartral.inappbilling;

import com.smartral.inappbilling.utils.io.ConnectionRequest;
import com.smartral.inappbilling.utils.io.NetworkEvent;
import com.smartral.inappbilling.utils.payment.Receipt;
import com.smartral.inappbilling.utils.processing.Result;
import com.smartral.inappbilling.utils.ui.events.ActionEvent;
import com.smartral.inappbilling.utils.ui.events.ActionListener;
import com.smartral.inappbilling.utils.util.Base64;
import com.smartral.inappbilling.utils.util.Callback;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.Signer;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.RSADigestSigner;

/**
 *
 * @author shannah
 */
public class GooglePlayValidator extends IAPValidator {

    private static final int STATUS_VALIDATION_SUCCESS = 1;
    private static final int STATUS_VALIDATION_FAILURE = 2;

    private GoogleTokenMap googleTokenMap = new GoogleTokenMap();

    @Override
    public Receipt[] validate(Receipt receipt, boolean isSubs) throws IOException {
        String orderData = receipt.getOrderData();
        Result res = Result.fromContent(orderData, "json");
        String receiptData = Result.fromContent((Map) res.get("data")).toString();
        String signature = res.getAsString("signature");

        final Receipt out = new Receipt();
        out.setTransactionId(receipt.getTransactionId());
        out.setInternalId(receipt.getInternalId());
        out.setOrderData(receipt.getOrderData());
        out.setCancellationDate(receipt.getCancellationDate());
        out.setExpiryDate(receipt.getExpiryDate());
        out.setPurchaseDate(receipt.getPurchaseDate());
        out.setPackageName(receipt.getPackageName());
        out.setStoreCode(receipt.getStoreCode());
        out.setSku(receipt.getSku());
        out.setQuantity(receipt.getQuantity());
        validatePurchase(receiptData, signature, new Callback<SubscriptionData>() {
            @Override
            public void onSucess(SubscriptionData t) {
                if (t.expirationTime > 0) {
                    out.setExpiryDate(new Date(t.expirationTime));
                }
                if (t.productId != null) {
                    out.setSku(t.productId);
                }
                if (t.startTime > 0) {
                    out.setPurchaseDate(new Date(t.startTime));
                }
                if (t.packageName != null) {
                    out.setPackageName(t.packageName);
                }
            }

            @Override
            public void onError(Object o, Throwable thrwbl, int i, String string) {
                Logger.getLogger(getClass().getSimpleName()).log(Level.INFO,"ERROR_GOOGLE_VALIDATION" + thrwbl.getLocalizedMessage());
            }

        }, isSubs);
        return new Receipt[]{out};
    }

    /**
     * Structure to hold subscription data that we load.
     */
    private class SubscriptionData {

        String packageName;
        String productId;
        String purchaseToken;
        boolean autoRenewing;
        long expirationTime;
        long startTime;

        public String toString() {
            return "SubscriptionData {packageName: " + packageName + ", productId: " + productId + ", purchaseToken: " + purchaseToken + "}";
        }
    }

    /**
     * Structure to keep track of request state in HTTP requests.
     */
    private class RequestState {

        int status;
        String message;
    }

    /**
     *
     */
    private class GoogleTokenMap {

        String accessToken;
        private Object clientID;
        private Object clientSecret;
        private Object refreshToken;
    }

    // receipt is an object
    /*
    * receipt = { data: 'stringified receipt data', signature: 'receipt signature' };
    * if receipt.data is an object, it silently stringifies it
     */
    private void validatePurchase(String receiptData, String signature, Callback<SubscriptionData> cb, boolean isSubs) {
        Result res = Result.fromContent(receiptData, "json");
        SubscriptionData data = new SubscriptionData();
        if (res.get("packageName") == null) {
            cb.onError(this, new RuntimeException("Receipt data is missing package name. : " + receiptData), 500, "Receipt data is missing package name: " + receiptData);
            return;
        }
        data.packageName = res.getAsString("packageName");
        data.purchaseToken = res.getAsString("purchaseToken");
        data.autoRenewing = res.getAsBoolean("autoRenewing");
        data.startTime = res.getAsLong("purchaseTime");
        data.productId = res.getAsString("productId");
        if (data.packageName == null) {
            
        }
        checkSubscriptionStatus(data, cb, isSubs);
    };
    
    
    private void checkSubscriptionStatus(final SubscriptionData data, final Callback<SubscriptionData> cb, boolean isSubs) {
        String packageName = data.packageName;
        String subscriptionID = data.productId;
        String purchaseToken = data.purchaseToken;

        if (packageName == null) {
            cb.onError(this, new RuntimeException("No package name provided"), 500, "No package name provided");
            return;
        }

        final String url = "https://www.googleapis.com/androidpublisher/v2/applications/" + packageName
                + "/purchases/" + (isSubs ? "subscriptions" : "products") + "/" + subscriptionID + "/tokens/" + purchaseToken;
        final RequestState state = new RequestState();

        if (googleTokenMap.accessToken == null) {
            // we don't have an access token yet.. just skip to it
            state.status = STATUS_VALIDATION_FAILURE;
            state.message = "No access token yet";
        } else {
            getSubscriptionInfo(url, new Callback<Result>() {

                @Override
                public void onSucess(Result body) {
                    data.autoRenewing = body.getAsBoolean("autoRenewing");
                    data.expirationTime = body.getAsLong("expiryTimeMillis");
                    data.startTime = body.getAsLong("startTimeMillis");
                    state.status = STATUS_VALIDATION_SUCCESS;

                }

                @Override
                public void onError(Object o, Throwable thrwbl, int i, String string) {
                    state.status = STATUS_VALIDATION_FAILURE;
                    state.message = string;
                }

            });
        }

        if (state.status == STATUS_VALIDATION_FAILURE) {
            // Try to refresh the google token
            refreshGoogleTokens(new Callback<Result>() {

                @Override
                public void onSucess(Result parsedBody) {
                    if (parsedBody.get("error") != null) {
                        state.status = STATUS_VALIDATION_FAILURE;
                        state.message = parsedBody.getAsString("error");
                    } else {
                        googleTokenMap.accessToken = parsedBody.getAsString("access_token");
                        state.status = STATUS_VALIDATION_SUCCESS;
                    }
                }

                @Override
                public void onError(Object o, Throwable thrwbl, int i, String string) {
                    state.status = STATUS_VALIDATION_FAILURE;
                    state.message = string;
                }

            });

            if (state.status == STATUS_VALIDATION_SUCCESS) {
                getSubscriptionInfo(url, new Callback<Result>() {

                    @Override
                    public void onSucess(Result parsedBody) {
                        if (parsedBody.get("error") != null) {
                            try {
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }
                            state.status = STATUS_VALIDATION_FAILURE;
                            state.message = parsedBody.getAsString("error");
                            //cb.onError(this, new IOException(state.message), 500, state.message);
                            //return;
                        } else {

                            data.autoRenewing = parsedBody.getAsBoolean("autoRenewing");
                            data.expirationTime = parsedBody.getAsLong("expiryTimeMillis");
                            data.startTime = parsedBody.getAsLong("startTimeMillis");

                            state.status = STATUS_VALIDATION_SUCCESS;
                            //googleTokenMap.accessToken =  parsedBody.getAsString(KEYS_CLIENT_ID);
                            state.status = STATUS_VALIDATION_SUCCESS;

                        }
                    }

                    @Override
                    public void onError(Object o, Throwable thrwbl, int i, String string) {
                        state.status = STATUS_VALIDATION_FAILURE;
                        state.message = string;
                    }
                });
            }
        }

        if (state.status == STATUS_VALIDATION_SUCCESS) {
            cb.onSucess(data);
        } else {
            cb.onError(cb, new IOException(state.message), state.status, state.message);
        }
    }

    /**
     * Generates a private key from a PKCS#8 encoded string.
     *
     * @param key
     * @return
     */
    private RSAPrivateKey getRSAPrivateKey(String key) {
        String privKeyPEM = key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "");
        try {
            byte[] encodedPrivateKey = Base64.decode(privKeyPEM.getBytes("UTF-8"));
            ASN1Sequence primitive = (ASN1Sequence) ASN1Sequence.fromByteArray(encodedPrivateKey);
            Enumeration<?> e = primitive.getObjects();
            BigInteger v = ((ASN1Integer) e.nextElement()).getValue();

            int version = v.intValue();
            if (version != 0 && version != 1) {
                throw new IllegalArgumentException("wrong version for RSA private key");
            }
            e.nextElement();
            DEROctetString octetString = (DEROctetString) e.nextElement();
            encodedPrivateKey = octetString.getOctets();
            primitive = (ASN1Sequence) ASN1Sequence.fromByteArray(encodedPrivateKey);
            return RSAPrivateKey.getInstance(primitive);
        } catch (IOException | IllegalArgumentException e2) {
            throw new RuntimeException(e2);
        }
    }

    /**
     * Create JWT token. See
     * https://developers.google.com/identity/protocols/OAuth2ServiceAccount#delegatingauthority
     *
     * @param payload
     * @return
     */
    private String createJWT(String payload) {
        try {
            Map header = new HashMap();
            header.put("alg", "RS256");
            header.put("typ", "JWT");

            Map claims = new HashMap();
            claims.put("iss", getGoogleClientId());
            claims.put("scope", "https://www.googleapis.com/auth/androidpublisher");
            claims.put("aud", "https://www.googleapis.com/oauth2/v4/token");
            claims.put("exp", String.valueOf(System.currentTimeMillis() / 1000l + 1800));
            claims.put("iat", String.valueOf(System.currentTimeMillis() / 1000l));

            String headerEnc = Base64.encodeNoNewline(Result.fromContent(header).toString().getBytes("UTF-8")).replace('+', '-').replace('/', '_').replace("=", " ");
            String claimsEnc = Base64.encodeNoNewline(Result.fromContent(claims).toString().getBytes("UTF-8")).replace('+', '-').replace('/', '_').replace("=", " ");
            String sigContent = headerEnc + "." + claimsEnc;

            Digest digest = new SHA256Digest();
            Signer signer = new RSADigestSigner(digest);

            String pkey = getGooglePrivateKey();
            RSAPrivateKey rpkey = getRSAPrivateKey(pkey);
            signer.init(true, new RSAKeyParameters(true, rpkey.getModulus(), rpkey.getPrivateExponent()));

            byte[] sigBytes = sigContent.getBytes("UTF-8");
            signer.update(sigBytes, 0, sigBytes.length);

            byte[] sig = signer.generateSignature();

            RSAKeyParameters kp = new RSAKeyParameters(false, rpkey.getModulus(), rpkey.getPublicExponent());
            signer.init(false, kp);
            signer.update(sigBytes, 0, sigBytes.length);
            boolean res = signer.verifySignature(sig);
            if (!res) {
                throw new RuntimeException("Failed to verify signature after creating it");
            }

            String jwt = headerEnc + "." + claimsEnc + "." + Base64.encodeNoNewline(sig).replace('+', '-').replace('/', '_').replace("=", " ");
            return jwt;
        } catch (UnsupportedEncodingException | CryptoException | RuntimeException ex) {
            throw new RuntimeException(ex);
        }

    }

    void getSubscriptionInfo(String url, final Callback<Result> cb) {
        if (googleTokenMap.accessToken == null) {
            cb.onError(this, new RuntimeException("Failed to get subscription info because no access token was found."), 500, "Failed to get subscription info because no access token was found.");
            return;
        }
        final ConnectionRequest req = new ConnectionRequest();
        req.setCookieJar(new Hashtable());
        req.setHttpMethod("GET");
        req.addRequestHeader("Authorization", "Bearer " + googleTokenMap.accessToken);
        req.addRequestHeader("Accept", "application/json");
        req.setFailSilently(true);
        req.setReadResponseForErrors(true);
        req.addResponseListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent t) {
                try {
                    if (req.getResponseCode() >= 200 && req.getResponseCode() < 300) {
                        Result res = Result.fromContent(new String(req.getResponseData(), "UTF-8"), "json");
                        cb.onSucess(res);
                    } else {
                        cb.onError(this, new RuntimeException("Failed to get subscription info: response code " + req.getResponseCode()), req.getResponseCode(), "Failed to get subscription info: response code " + req.getResponseCode());
                    }
                } catch (UnsupportedEncodingException | IllegalArgumentException ex) {
                    try {
                    } catch (Exception ex2) {
                    }
                    cb.onError(this, ex, 500, ex.getMessage());
                }
            }
        });
        req.setUrl(url);
        req.addToQueueAndWait();

    }

    private void refreshGoogleTokens(final Callback<Result> cb) {
        final ConnectionRequest req = new ConnectionRequest();
        req.setCookieJar(new Hashtable());
        req.setHttpMethod("POST");
        req.setUrl("https://www.googleapis.com/oauth2/v4/token");
        req.setWriteRequest(true);
        req.setFailSilently(true);
        req.setReadResponseForErrors(true);
        req.addArgument("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        req.addArgument("assertion", createJWT(""));
        req.addResponseListener(new ActionListener<NetworkEvent>() {
            @Override
            public void actionPerformed(NetworkEvent evt) {
                try {
                    if (req.getResponseCode() >= 200 && req.getResponseCode() < 300) {
                        cb.onSucess(Result.fromContent(new String(req.getResponseData(), "UTF-8"), "json"));
                    } else {
                        try {
                        } catch (Exception ex2) {
                        }
                        cb.onError(this, new IOException("Failed to refresh token:  Response code " + req.getResponseCode()), req.getResponseCode(), "Failed to refresh token.  Response code " + req.getResponseCode());
                    }
                } catch (UnsupportedEncodingException | IllegalArgumentException ex) {
                    try {
                    } catch (Exception ex2) {
                    }
                    cb.onError(this, ex, req.getResponseCode(), ex.getMessage());
                }
            }
        });
        req.addToQueueAndWait();
    }
}
