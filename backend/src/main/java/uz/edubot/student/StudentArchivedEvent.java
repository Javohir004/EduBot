package uz.edubot.student;

import java.util.UUID;

/** Published synchronously inside the archiving transaction. */
public record StudentArchivedEvent(UUID studentId) {
}
