/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.smartral.inappbilling;

import com.smartral.inappbilling.utils.payment.Receipt;

import java.io.IOException;

/**
 *
 * @author shannah
 */
public abstract class IAPValidator {

    private String appleSecret;
    private String googleClientId;
    private String googlePrivateKey;

    public static IAPValidator getValidatorForPlatform(String storeCode, boolean isSandBox, boolean isExcludeOldReceipts) {
        if (null == storeCode) {
            return null;
        }
        switch (storeCode) {
            case Receipt.STORE_CODE_ITUNES:
                return new AppleIAPValidator(isSandBox, isExcludeOldReceipts);
            case Receipt.STORE_CODE_PLAY:
                return new GooglePlayValidator();
            default:
                return null;
        }
    }

    public String getAppleSecret() {
        return appleSecret;
    }

    public void setAppleSecret(String secret) {
        appleSecret = secret;
    }

    public abstract Receipt[] validate(Receipt receipt, boolean isSubs) throws IOException;

    /**
     * @return the googleClientId
     */
    public String getGoogleClientId() {
        return googleClientId;
    }

    /**
     * @param googleClientId the googleClientId to set
     */
    public void setGoogleClientId(String googleClientId) {
        this.googleClientId = googleClientId;
    }

    /**
     * @return the googlePrivateKey
     */
    public String getGooglePrivateKey() {
        return googlePrivateKey;
    }

    /**
     * @param googlePrivateKeyFile the googlePrivateKey to set
     */
    public void setGooglePrivateKey(String googlePrivateKeyFile) {
        this.googlePrivateKey = googlePrivateKeyFile;
    }
}
