package uz.edubot.student;

import java.time.LocalDate;

/**
 * Validated, normalized student fields (phone already in E.164).
 */
public record StudentData(String firstName, String lastName, LocalDate birthDate, String phone, String note) {
}
