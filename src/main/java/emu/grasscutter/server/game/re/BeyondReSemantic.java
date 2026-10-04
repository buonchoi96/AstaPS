package emu.grasscutter.server.game.re;

import emu.grasscutter.config.ConfigContainer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Watch classification and semantic-log redaction for Beyond RE captures. */
public final class BeyondReSemantic {
    private static final Map<Integer, String> UGC_CANDIDATE_EVENTS =
            Map.ofEntries(
                    Map.entry(1971, "UGC_CANDIDATE_ENTER"),
                    Map.entry(1660, "UGC_CANDIDATE_ENTER"),
                    Map.entry(1986, "UGC_CANDIDATE_ROOM_CHANGE"),
                    Map.entry(24272, "UGC_CANDIDATE_ROOM_CHANGE"),
                    Map.entry(20869, "UGC_CANDIDATE_SAVE"),
                    Map.entry(25221, "UGC_CANDIDATE_PUBLISH"),
                    Map.entry(28882, "UGC_CANDIDATE_PUBLISH_SETTINGS"),
                    Map.entry(4555, "UGC_CANDIDATE_PUBLISH_SETTINGS"),
                    Map.entry(20707, "UGC_CANDIDATE_TEAM_CREATE"),
                    Map.entry(26819, "UGC_CANDIDATE_TEAM_CREATE"),
                    Map.entry(5825, "UGC_CANDIDATE_PLAY_RECORD"),
                    Map.entry(5257, "UGC_CANDIDATE_SETTLE"),
                    Map.entry(25669, "UGC_CANDIDATE_PUBLISH_RESULT"));

    private BeyondReSemantic() {}

    public static boolean isWatched(
            ConfigContainer.BeyondReRecorderOptions options, int opcode, String name) {
        if (options == null) return false;
        if (options.watchOpcodes != null) {
            for (int watched : options.watchOpcodes) {
                if (watched == opcode) return true;
            }
        }

        String packetName = name == null ? "" : name;
        if (options.watchNamePrefixes != null) {
            for (String prefix : options.watchNamePrefixes) {
                if (prefix != null && !prefix.isEmpty() && packetName.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    public static String semanticEvent(int opcode, String name) {
        // Deliberately candidate-labelled; this does not assert Miliastra protocol reuse.
        return UGC_CANDIDATE_EVENTS.get(opcode);
    }

    public static Map<String, Object> redactDecodedFields(Map<String, Object> decoded) {
        if (decoded == null || decoded.isEmpty()) return Map.of();
        Map<String, Object> redacted = new LinkedHashMap<>();
        decoded.forEach(
                (key, value) ->
                        redacted.put(key, isSensitiveKey(key) ? "REDACTED" : redactValue(value)));
        return redacted;
    }

    private static Object redactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach(
                    (key, nestedValue) -> {
                        String textKey = String.valueOf(key);
                        nested.put(
                                textKey,
                                isSensitiveKey(textKey)
                                        ? "REDACTED"
                                        : redactValue(nestedValue));
                    });
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> list = new ArrayList<>();
            iterable.forEach(item -> list.add(redactValue(item)));
            return list;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                list.add(redactValue(Array.get(value, i)));
            }
            return list;
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.contains("passcode")
                || normalized.contains("password")
                || normalized.contains("credential")
                || normalized.contains("access_token")
                || normalized.contains("refresh_token")
                || normalized.contains("auth_token")
                || normalized.contains("session_token")
                || normalized.contains("session_key")
                || normalized.contains("auth_key")
                || normalized.contains("secret")
                || normalized.contains("chat_text")
                || normalized.contains("chat_content")
                || normalized.contains("report_text")
                || normalized.contains("report_reason");
    }
}
