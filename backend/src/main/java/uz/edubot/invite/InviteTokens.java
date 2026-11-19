package uz.edubot.invite;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 128-bit random tokens, URL-safe (fits Telegram's 64-character /start parameter limit).
 */
final class InviteTokens {

    /** Prefix of the Telegram deep-link parameter: {@code /start inv_<token>}. */
    static final String START_PREFIX = "inv_";

    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteTokens() {
    }

    static String generate() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
