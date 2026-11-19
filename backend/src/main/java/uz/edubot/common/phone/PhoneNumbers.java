package uz.edubot.common.phone;

import java.util.Optional;

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

    /** Masks all but the last four digits, for logs and lists: {@code +998*****4567}. */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 8) {
            return "***";
        }
        return e164.substring(0, 4) + "*".repeat(e164.length() - 8) + e164.substring(e164.length() - 4);
    }
}
