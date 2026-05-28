package com.g2rain.gateway.utils;


import com.g2rain.common.utils.Strings;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;

import java.text.ParseException;
import java.util.Objects;

/**
 * Token 与 DPoP 客户端公钥绑定校验（RFC 7638 JWK Thumbprint）。
 */
public final class ClientPubKeyMatcher {

    private ClientPubKeyMatcher() {
    }

    public static boolean matches(String clientPublicKey, JWK proofJwk) {
        if (Strings.isBlank(clientPublicKey) || Objects.isNull(proofJwk)) {
            return false;
        }

        try {
            JWK stored = JWK.parse(clientPublicKey);
            return stored.toPublicJWK().computeThumbprint()
                .equals(proofJwk.toPublicJWK().computeThumbprint());
        } catch (ParseException | JOSEException e) {
            return false;
        }
    }
}
