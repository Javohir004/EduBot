package uz.edubot.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Test helper that signs initData the same way Telegram does, so tests exercise the real validator
 * instead of a bypass.
 */
public final class TelegramInitDataSigner {

    private TelegramInitDataSigner() {
    }

    public static String userJson(long id, String firstName) {
        return "{\"id\":" + id + ",\"first_name\":\"" + firstName + "\",\"last_name\":\"Test\","
                + "\"username\":\"user" + id + "\",\"language_code\":\"uz\"}";
    }

    public static String sign(String botToken, long telegramId, String firstName, Instant authDate) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("query_id", "AAHdF6IQAAAAAN0XohDhrOrc");
        fields.put("user", userJson(telegramId, firstName));
        fields.put("auth_date", Long.toString(authDate.getEpochSecond()));
        return sign(botToken, fields);
    }

    public static String sign(String botToken, Map<String, String> fields) {
        String dataCheckString = fields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        byte[] secretKey = hmac("WebAppData".getBytes(UTF_8), botToken.getBytes(UTF_8));
        String hash = HexFormat.of().formatHex(hmac(secretKey, dataCheckString.getBytes(UTF_8)));

        Map<String, String> all = new LinkedHashMap<>(fields);
        all.put("hash", hash);
        return all.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, UTF_8);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
