package uz.edubot.common.phone;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import uz.edubot.common.error.ApiException;

/**
 * Normalizes phone numbers to E.164 ({@code +998901234567}). A bare 9-digit number is treated as
 * an Uzbek mobile number. Formatting characters (spaces, dashes, brackets) are ignored.
 */
public final class PhoneNumbers {

    private static final String UZ_COUNTRY_CODE = "998";

    private PhoneNumbers() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        if (!trimmed.matches("\\+?[0-9 ()\\-]{7,24}")) {
            return Optional.empty();
        }
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.length() == 9) {
            digits = UZ_COUNTRY_CODE + digits;
        }
        if (digits.length() < 10 || digits.length() > 15) {
            return Optional.empty();
        }
        if (digits.startsWith(UZ_COUNTRY_CODE) && digits.length() != 12) {
            return Optional.empty();
        }
        return Optional.of("+" + digits);
    }

    /** Normalized phone or a 400 {@code INVALID_PHONE} API error. */
    public static String require(String raw) {
        return normalize(raw).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Invalid phone number"));
    }

    /** Like {@link #require} but blank input means "no phone". */
    public static String optional(String raw) {
        return raw == null || raw.isBlank() ? null : require(raw);
    }

    /** Masks all but the last four digits, for logs and lists: {@code +998*****4567}. */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 8) {
            return "***";
        }
        return e164.substring(0, 4) + "*".repeat(e164.length() - 8) + e164.substring(e164.length() - 4);
    }
}
