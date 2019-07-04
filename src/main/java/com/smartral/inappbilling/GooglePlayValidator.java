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
            public void onSuccess(SubscriptionData t) {
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
                setReceiptData(out, t);
            }

            @Override
            public void onError(Object o, Throwable thrwbl, int i, String string) {
                Logger.getLogger(getClass().getSimpleName()).log(Level.INFO, "ERROR_GOOGLE_VALIDATION" + thrwbl.getLocalizedMessage());
            }

        }, isSubs);
        return new Receipt[]{out};
    }

    /**
     * Structure to hold subscription data that we load.
     */
    public class SubscriptionData {

        String packageName;
        String productId;
        String purchaseToken;

        Integer acknowledgementState;
        long priceAmountMicros;
        String orderId;
        String kind;
        Integer cancelReason;

        String countryCode;
        Integer paymentState;
        Integer purchaseType;

        String priceCurrencyCode;
        long startTimeMillis;
        long expiryTimeMillis;
        String developerPayload;

        long userCancellationTimeMillis;

        boolean autoRenewing;
        long expirationTime;
        long startTime;

        CancelSurveyResult cancelSurveyResult;
    }

    public class CancelSurveyResult {
        Integer cancelSurveyReason;
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

    /**
     * receipt = { data: 'stringified receipt data', signature: 'receipt signature' };
     * if receipt.data is an object, it silently stringifies it
     */
    private void validatePurchase(String receiptData, String signature, Callback<SubscriptionData> cb, boolean isSubs) {
        Result res = Result.fromContent(receiptData, "json");
        SubscriptionData data = setParsedData(res);
        if (res.get("packageName") == null) {
            cb.onError(this, new RuntimeException("Receipt data is missing package name. : " + receiptData), 500, "Receipt data is missing package name: " + receiptData);
            return;
        }
        checkSubscriptionStatus(data, cb, isSubs);
    }

    private void checkSubscriptionStatus(SubscriptionData data, final Callback<SubscriptionData> cb, boolean isSubs) {
        String packageName = data.packageName;
        String subscriptionID = data.productId;
        String purchaseToken = data.purchaseToken;

        if (packageName == null) {
            cb.onError(this, new RuntimeException("No package name provided"), 500, "No package name provided");
            return;
        }

        final String url = String.format("https://www.googleapis.com/androidpublisher/v3/applications/%s/purchases/%s/%s/tokens/%s",
                packageName, isSubs ? "subscriptions" : "products", subscriptionID, purchaseToken);
        final RequestState state = new RequestState();

        if (googleTokenMap.accessToken == null) {
            // we don't have an access token yet.. just skip to it
            state.status = STATUS_VALIDATION_FAILURE;
            state.message = "No access token yet";
        } else {
            getSubscriptionInfo(url, new Callback<Result>() {
                @Override
                public void onSuccess(Result body) {
                    setParsedData(body, data, state, packageName, subscriptionID, purchaseToken);
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
                public void onSuccess(Result parsedBody) {
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
                    public void onSuccess(Result parsedBody) {
                        if (parsedBody.get("error") != null) {
                            state.status = STATUS_VALIDATION_FAILURE;
                            state.message = parsedBody.getAsString("error");
                        } else {
                            setParsedData(parsedBody, data, state, packageName, subscriptionID, purchaseToken);
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
            cb.onSuccess(data);
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
                        cb.onSuccess(res);
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
                        cb.onSuccess(Result.fromContent(new String(req.getResponseData(), "UTF-8"), "json"));
                    } else {
                        cb.onError(this, new IOException("Failed to refresh token:  Response code " + req.getResponseCode()), req.getResponseCode(), "Failed to refresh token.  Response code " + req.getResponseCode());
                    }
                } catch (UnsupportedEncodingException | IllegalArgumentException ex) {
                    cb.onError(this, ex, req.getResponseCode(), ex.getMessage());
                }
            }
        });
        req.addToQueueAndWait();
    }

    /**
     * String packageName;
     * String productId;
     * String purchaseToken;
     * <p>
     * Integer acknowledgementState;
     * String priceAmountMicros;
     * String orderId;
     * String kind;
     * Integer cancelReason;
     * <p>
     * String countryCode;
     * Integer paymentState;
     * Integer purchaseType;
     * <p>
     * String priceCurrencyCode;
     * String startTimeMillis;
     * String expiryTimeMillis;
     * String developerPayload;
     * <p>
     * long userCancellationTimeMillis;
     * <p>
     * boolean autoRenewing;
     * long expirationTime;
     * long startTime;
     * <p>
     * CancelSurveyResult cancelSurveyResult;
     *
     * @param result
     * @return
     */
    private SubscriptionData setParsedData(Result result) {
        SubscriptionData data = new SubscriptionData();
        data.packageName = result.getAsString("packageName");
        data.productId = result.getAsString("productId");
        data.purchaseToken = result.getAsString("purchaseToken");

        data.acknowledgementState = result.getAsInteger("acknowledgementState");
        data.priceAmountMicros = result.getAsLong("priceAmountMicros");
        data.orderId = result.getAsString("orderId");
        data.kind = result.getAsString("kind");
        data.cancelReason = result.getAsInteger("cancelReason");

        data.countryCode = result.getAsString("countryCode");
        data.paymentState = result.getAsInteger("paymentState");
        data.purchaseType = result.getAsInteger("purchaseType");

        data.priceCurrencyCode = result.getAsString("priceCurrencyCode");
        data.startTimeMillis = result.getAsLong("startTimeMillis");
        data.expiryTimeMillis = result.getAsLong("expiryTimeMillis");
        data.developerPayload = result.getAsString("developerPayload");
        data.userCancellationTimeMillis = result.getAsLong("userCancellationTimeMillis");

        data.autoRenewing = result.getAsBoolean("autoRenewing");
        data.expirationTime = result.getAsLong("expiryTimeMillis");
        data.startTime = result.getAsLong("startTimeMillis");
        return data;
    }

    private void setParsedData(Result parsedBody, SubscriptionData data, RequestState state,
                               String packageName, String subscriptionID, String purchaseToken) {
        data.packageName = packageName;
        data.productId = subscriptionID;
        data.purchaseToken = purchaseToken;

        data.acknowledgementState = parsedBody.getAsInteger("acknowledgementState");
        data.priceAmountMicros = parsedBody.getAsLong("priceAmountMicros");
        data.orderId = parsedBody.getAsString("orderId");
        data.kind = parsedBody.getAsString("kind");
        data.cancelReason = parsedBody.getAsInteger("cancelReason");

        data.countryCode = parsedBody.getAsString("countryCode");
        data.paymentState = parsedBody.getAsInteger("paymentState");
        data.purchaseType = parsedBody.getAsInteger("purchaseType");

        data.priceCurrencyCode = parsedBody.getAsString("priceCurrencyCode");
        data.startTimeMillis = parsedBody.getAsLong("startTimeMillis");
        data.expiryTimeMillis = parsedBody.getAsLong("expiryTimeMillis");
        data.developerPayload = parsedBody.getAsString("developerPayload");
        data.userCancellationTimeMillis = parsedBody.getAsLong("userCancellationTimeMillis");

        data.autoRenewing = parsedBody.getAsBoolean("autoRenewing");
        data.expirationTime = parsedBody.getAsLong("expiryTimeMillis");
        data.startTime = parsedBody.getAsLong("startTimeMillis");

        state.status = STATUS_VALIDATION_SUCCESS;
    }

    /**
     * private String productId;
     private String purchaseToken;
     private Integer acknowledgementState;
     private Long priceAmountMicros;
     private String orderId;
     private String kind;
     private Integer cancelReason;
     private String countryCode;
     private Integer paymentState;
     private Integer purchaseType;
     private String priceCurrencyCode;
     private Long startTimeMillis;
     private Long expiryTimeMillis;
     private String developerPayload;
     private Long userCancellationTimeMillis;
     private Boolean autoRenewing;
     private Long startTime;

     * @return
     */
    private void setReceiptData(Receipt out, SubscriptionData t) {
        out.setProductId(t.productId);
        out.setPurchaseToken(t.purchaseToken);
        out.setAcknowledgementState(t.acknowledgementState);
        out.setPriceAmountMicros(t.priceAmountMicros);
        out.setOrderId(t.orderId);
        out.setKind(t.kind);
        out.setCancelReason(t.cancelReason);
        out.setCountryCode(t.countryCode);
        out.setPaymentState(t.paymentState);
        out.setPurchaseType(t.purchaseType);
        out.setPriceCurrencyCode(t.priceCurrencyCode);
        out.setStartTimeMillis(t.startTimeMillis);
        out.setExpiryTimeMillis(t.expiryTimeMillis);
        out.setDeveloperPayload(t.developerPayload);
        out.setUserCancellationTimeMillis(t.userCancellationTimeMillis);
        out.setAutoRenewing(t.autoRenewing);
        out.setStartTime(t.startTime);
    }
}
