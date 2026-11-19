package uz.edubot.security.telegram;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLDecoder;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates Mini App initData as described in the official Telegram documentation
 * ("Validating data received via the Mini App"):
 * <pre>
 * secret_key = HMAC_SHA256(key = "WebAppData", data = bot_token)
 * hash       = hex(HMAC_SHA256(key = secret_key, data = data_check_string))
 * </pre>
 * where data_check_string is every received field except {@code hash}, sorted by key,
 * formatted as {@code key=value} and joined with a line feed.
 */
@Component
@RequiredArgsConstructor
public class TelegramInitDataValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final byte[] WEB_APP_DATA_KEY = "WebAppData".getBytes(UTF_8);
    /** Tolerated clock difference between Telegram and our server. */
    private static final Duration FUTURE_SKEW = Duration.ofMinutes(1);

    private final TelegramProperties properties;
    private final Clock clock;
    private final JsonMapper jsonMapper;

    public TelegramInitData validate(String rawInitData) {
        if (rawInitData == null || rawInitData.isBlank()) {
            throw new InvalidInitDataException("empty initData");
        }
        Map<String, String> fields = parse(rawInitData);
        String hash = fields.remove("hash");
        if (hash == null) {
            throw new InvalidInitDataException("hash is missing");
        }
        verifySignature(fields, hash);

        Instant authDate = parseAuthDate(fields.get("auth_date"));
        verifyFreshness(authDate);

        TelegramUser user = parseUser(fields.get("user"));
        return new TelegramInitData(user, authDate, fields.get("start_param"));
    }

    private Map<String, String> parse(String raw) {
        Map<String, String> fields = new HashMap<>();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new InvalidInitDataException("malformed pair");
            }
            String key;
            String value;
            try {
                key = URLDecoder.decode(pair.substring(0, eq), UTF_8);
                value = URLDecoder.decode(pair.substring(eq + 1), UTF_8);
            } catch (IllegalArgumentException e) {
                throw new InvalidInitDataException("malformed encoding");
            }
            if (fields.putIfAbsent(key, value) != null) {
                throw new InvalidInitDataException("duplicate key");
            }
        }
        return fields;
    }

    private void verifySignature(Map<String, String> fields, String hash) {
        String dataCheckString = fields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));

        byte[] secretKey = hmacSha256(WEB_APP_DATA_KEY, properties.botToken().getBytes(UTF_8));
        byte[] expected = hmacSha256(secretKey, dataCheckString.getBytes(UTF_8));

        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(hash);
        } catch (IllegalArgumentException e) {
            throw new InvalidInitDataException("hash is not hex");
        }
        // Constant-time comparison.
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new InvalidInitDataException("signature mismatch");
        }
    }

    private Instant parseAuthDate(String value) {
        if (value == null) {
            throw new InvalidInitDataException("auth_date is missing");
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (NumberFormatException e) {
            throw new InvalidInitDataException("auth_date is not a number");
        }
    }

    private void verifyFreshness(Instant authDate) {
        Instant now = clock.instant();
        if (authDate.isAfter(now.plus(FUTURE_SKEW))) {
            throw new InvalidInitDataException("auth_date is in the future");
        }
        if (authDate.isBefore(now.minus(properties.initDataMaxAge()))) {
            throw new InvalidInitDataException("initData expired");
        }
    }

    private TelegramUser parseUser(String json) {
        if (json == null) {
            throw new InvalidInitDataException("user is missing");
        }
        TelegramUser user;
        try {
            user = jsonMapper.readValue(json, TelegramUser.class);
        } catch (JacksonException e) {
            throw new InvalidInitDataException("user is not valid JSON");
        }
        if (user.id() <= 0 || user.firstName() == null || user.firstName().isBlank()) {
            throw new InvalidInitDataException("user has no id or first_name");
        }
        return user;
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
