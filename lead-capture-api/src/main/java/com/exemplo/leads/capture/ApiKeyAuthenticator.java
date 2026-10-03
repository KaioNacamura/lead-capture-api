package com.exemplo.leads.capture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Descobre de qual empresa é a chave de API enviada no cabeçalho. */
@Component
public class ApiKeyAuthenticator {

    private final JdbcTemplate jdbc;

    public ApiKeyAuthenticator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> organizationFor(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM organizations WHERE api_key_hash = ?",
                UUID.class,
                sha256(apiKey));
        return ids.stream().findFirst();
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
