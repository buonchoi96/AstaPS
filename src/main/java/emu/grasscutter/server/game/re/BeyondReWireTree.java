package emu.grasscutter.server.game.re;

import emu.grasscutter.config.ConfigContainer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, non-destructive protobuf wire decoder used only as research evidence. */
final class BeyondReWireTree {
    record Result(boolean success, List<Map<String, Object>> fields, String error) {}

    private BeyondReWireTree() {}

    static Result decode(byte[] input, ConfigContainer.BeyondReRecorderOptions options) {
        byte[] bytes = input == null ? new byte[0] : input;
        Limits limits =
                new Limits(
                        Math.max(0, options.wireTreeMaxDepth),
                        Math.max(1, options.wireTreeMaxFields),
                        Math.max(0, options.wireTreeMaxNestedBytes));
        try {
            Cursor cursor = new Cursor(bytes);
            Counter counter = new Counter();
            List<Map<String, Object>> fields = parseFields(cursor, limits, counter, 0, -1);
            if (cursor.position != bytes.length) {
                throw new WireException("trailing bytes at offset " + cursor.position);
            }
            return new Result(true, fields, null);
        } catch (Throwable e) {
            return new Result(false, List.of(), e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static List<Map<String, Object>> parseFields(
            Cursor cursor, Limits limits, Counter counter, int depth, int endGroupField)
            throws WireException {
        List<Map<String, Object>> fields = new ArrayList<>();
        while (cursor.position < cursor.bytes.length) {
            if (counter.count >= limits.maxFields) {
                Map<String, Object> truncated = new LinkedHashMap<>();
                truncated.put("truncated", true);
                truncated.put("reason", "max-fields");
                fields.add(truncated);
                cursor.position = cursor.bytes.length;
                break;
            }

            int start = cursor.position;
            long tag = readVarint(cursor);
            int fieldNumber = (int) (tag >>> 3);
            int wireType = (int) (tag & 7);
            if (fieldNumber <= 0) throw new WireException("invalid field number at offset " + start);
            if (wireType == 4) {
                if (endGroupField == fieldNumber) return fields;
                throw new WireException("unexpected end-group at offset " + start);
            }

            counter.count++;
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("fieldNumber", fieldNumber);
            field.put("wireType", wireType);
            switch (wireType) {
                case 0 -> field.put("varint", Long.toUnsignedString(readVarint(cursor)));
                case 1 -> field.put("fixed64Hex", readFixedHex(cursor, 8));
                case 2 -> {
                    long lengthValue = readVarint(cursor);
                    if (lengthValue > Integer.MAX_VALUE) {
                        throw new WireException("length-delimited field too large");
                    }
                    int length = (int) lengthValue;
                    byte[] value = cursor.readBytes(length);
                    field.put("byteLength", length);
                    field.put("bytesSha256", sha256(value));
                    if (depth < limits.maxDepth
                            && length > 0
                            && length <= limits.maxNestedBytes) {
                        Cursor nestedCursor = new Cursor(value);
                        int before = counter.count;
                        try {
                            List<Map<String, Object>> nested =
                                    parseFields(nestedCursor, limits, counter, depth + 1, -1);
                            if (nestedCursor.position == value.length && !nested.isEmpty()) {
                                field.put("nestedInterpretation", "CANDIDATE");
                                field.put("nestedFields", nested);
                            }
                        } catch (WireException ignored) {
                            counter.count = before;
                        }
                    }
                }
                case 3 -> {
                    if (depth >= limits.maxDepth) {
                        throw new WireException("group exceeds max depth");
                    }
                    field.put(
                            "groupFields",
                            parseFields(cursor, limits, counter, depth + 1, fieldNumber));
                    field.put("nestedInterpretation", "CANDIDATE");
                }
                case 5 -> field.put("fixed32Hex", readFixedHex(cursor, 4));
                default -> throw new WireException("unsupported wire type " + wireType);
            }
            field.put(
                    "rawSliceSha256",
                    sha256(Arrays.copyOfRange(cursor.bytes, start, cursor.position)));
            fields.add(field);
        }
        if (endGroupField >= 0) throw new WireException("unterminated group " + endGroupField);
        return fields;
    }

    private static long readVarint(Cursor cursor) throws WireException {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (cursor.position >= cursor.bytes.length) {
                throw new WireException("truncated varint at offset " + cursor.position);
            }
            int current = cursor.bytes[cursor.position++] & 0xff;
            value |= (long) (current & 0x7f) << shift;
            if ((current & 0x80) == 0) return value;
        }
        throw new WireException("varint exceeds 10 bytes");
    }

    private static String readFixedHex(Cursor cursor, int length) throws WireException {
        return HexFormat.of().formatHex(cursor.readBytes(length));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record Limits(int maxDepth, int maxFields, int maxNestedBytes) {}

    private static final class Counter {
        int count;
    }

    private static final class Cursor {
        final byte[] bytes;
        int position;

        Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        byte[] readBytes(int length) throws WireException {
            if (length < 0 || position > bytes.length - length) {
                throw new WireException("truncated field at offset " + position);
            }
            byte[] value = Arrays.copyOfRange(bytes, position, position + length);
            position += length;
            return value;
        }
    }

    private static final class WireException extends Exception {
        WireException(String message) {
            super(message);
        }
    }
}
