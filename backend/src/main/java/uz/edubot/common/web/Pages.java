package uz.edubot.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class Pages {

    public static final int MAX_SIZE = 100;

    private Pages() {
    }

    /** Clamps client paging input so a request can never load an unbounded page. */
    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SIZE), sort);
    }

    /** {@code "ali"} -> {@code "%ali%"}; blank -> match everything. Escapes LIKE wildcards. */
    public static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }
        String escaped = query.strip().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
