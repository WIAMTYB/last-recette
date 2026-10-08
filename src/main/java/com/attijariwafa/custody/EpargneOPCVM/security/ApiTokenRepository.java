package com.attijariwafa.custody.EpargneOPCVM.security;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ApiTokenRepository {
    private static final String TOKEN_QUERY = """
            SELECT token_id, client_code, secret_hash, active, expires_at
            FROM api_client_token
            WHERE token_id = :tokenId
            """;
    private static final String AUTHORIZATION_QUERY = """
            SELECT path_pattern, http_method
            FROM api_client_token_authorization
            WHERE token_id = :tokenId
            ORDER BY path_pattern, http_method
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ApiTokenRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ApiTokenRecord> findByTokenId(String tokenId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("tokenId", tokenId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(TOKEN_QUERY, parameters);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> row = rows.getFirst();
        List<ApiTokenAuthorization> authorizations = jdbcTemplate.query(
                AUTHORIZATION_QUERY,
                parameters,
                (rs, rowNum) -> new ApiTokenAuthorization(
                        rs.getString("path_pattern"),
                        rs.getString("http_method")));

        return Optional.of(new ApiTokenRecord(
                stringValue(row.get("TOKEN_ID")),
                stringValue(row.get("CLIENT_CODE")),
                stringValue(row.get("SECRET_HASH")),
                booleanValue(row.get("ACTIVE")),
                localDateTimeValue(row.get("EXPIRES_AT")),
                Collections.unmodifiableList(authorizations)));
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && ("true".equalsIgnoreCase(value.toString()) || "1".equals(value.toString()));
    }

    private static LocalDateTime localDateTimeValue(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}
