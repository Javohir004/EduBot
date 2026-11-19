package uz.edubot.security.telegram;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import uz.edubot.common.error.ApiException;

/**
 * The client always gets the same generic message; {@link #getReason()} is for server logs only.
 */
@Getter
public class InvalidInitDataException extends ApiException {

    private final String reason;

    public InvalidInitDataException(String reason) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_INIT_DATA", "Telegram authentication failed");
        this.reason = reason;
    }
}
