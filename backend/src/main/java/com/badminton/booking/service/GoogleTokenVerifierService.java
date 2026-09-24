package com.badminton.booking.service;

import com.badminton.booking.exception.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class GoogleTokenVerifierService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifierService(
            @Value("${app.google.client-id}") String clientId)
            throws GeneralSecurityException, IOException {

        this.verifier = new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(
                        Collections.singletonList(clientId)
                )
                .build();
    }

    public GoogleIdToken.Payload verify(
            String rawIdToken) {

        if (rawIdToken == null
                || rawIdToken.isBlank()) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Google ID Token không được để trống"
            );
        }

        try {
            GoogleIdToken googleIdToken =
                    verifier.verify(rawIdToken);

            if (googleIdToken == null) {
                throw new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "Google ID Token không hợp lệ hoặc đã hết hạn"
                );
            }

            GoogleIdToken.Payload payload =
                    googleIdToken.getPayload();

            if (!Boolean.TRUE.equals(
                    payload.getEmailVerified())) {

                throw new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "Email Google chưa được xác minh"
                );
            }

            return payload;

        } catch (GeneralSecurityException
                 | IOException exception) {

            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Không thể xác minh Google ID Token"
            );
        }
    }
}