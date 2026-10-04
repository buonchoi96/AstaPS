package emu.grasscutter.server.game.re;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in structured packet recorder for Beyond reverse-engineering evidence. */
public final class BeyondReRecorder {
    public enum Direction {
        C2S,
        S2C
    }

    public record SessionInfo(String sessionId, Integer playerUid, String remoteEndpoint) {}

    private static final Object WRITE_LOCK = new Object();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private BeyondReRecorder() {}

    /**
     * Records one already-decoded packet without allowing recorder failures to affect gameplay.
     *
     * <p>The raw payload artifact is persisted before any semantic interpretation.
     */
    public static void record(
            ConfigContainer.BeyondReRecorderOptions options,
            Path outputRoot,
            SessionInfo session,
            Direction direction,
            int opcode,
            String name,
            byte[] header,
            byte[] payload) {
        if (options == null || !options.enabled || outputRoot == null || session == null) return;

        boolean watched = BeyondReSemantic.isWatched(options, opcode, name);
        if (!options.captureAllPackets && !watched) return;

        byte[] safeHeader = header == null ? new byte[0] : header.clone();
        byte[] safePayload = payload == null ? new byte[0] : payload.clone();
        try {
            recordInternal(
                    options,
                    outputRoot,
                    session,
                    direction == null ? Direction.C2S : direction,
                    opcode,
                    name == null ? "UNKNOWN" : name,
                    safeHeader,
                    safePayload,
                    watched);
        } catch (Throwable failure) {
            try {
                Grasscutter.getLogger()
                        .debug(
                                "Beyond RE recorder skipped packet {} ({}): {}",
                                opcode,
                                name,
                                failure.toString());
            } catch (Throwable ignored) {
                // Research logging must never be able to fail a game session.
            }
        }
    }

    private static void recordInternal(
            ConfigContainer.BeyondReRecorderOptions options,
            Path outputRoot,
            SessionInfo session,
            Direction direction,
            int opcode,
            String name,
            byte[] header,
            byte[] payload,
            boolean watched)
            throws Exception {
        String safeSessionId = sanitizeSessionId(session.sessionId());
        Path sessionDir = outputRoot.resolve("session-" + safeSessionId);
        Path rawDir = sessionDir.resolve("raw");
        String payloadSha256 = sha256(payload);
        String rawRelative = "raw/" + payloadSha256 + ".bin";
        Path rawPath = sessionDir.resolve(rawRelative);

        long monotonicNanos = System.nanoTime();
        long wallEpochMs = System.currentTimeMillis();
        String wallTime = Instant.ofEpochMilli(wallEpochMs).toString();

        synchronized (WRITE_LOCK) {
            Files.createDirectories(rawDir);
            writeRawIfAbsent(rawPath, payload);
            writeManifestIfAbsent(sessionDir.resolve("manifest.json"), options);
            ensureFile(sessionDir.resolve("semantic-events.jsonl"));
        }

        PacketHeadMetadata packetHead = parsePacketHead(header);
        BeyondReWireTree.Result headerTree = BeyondReWireTree.decode(header, options);
        BeyondReWireTree.Result payloadTree = BeyondReWireTree.decode(payload, options);
        TypedDecode typedDecode = decodeTyped(name, payload);

        String semanticEvent = BeyondReSemantic.semanticEvent(opcode, name);
        String decodeStatus;
        if (typedDecode.knownType()) {
            decodeStatus = typedDecode.success() ? "KNOWN" : "FAILED";
        } else if (!payloadTree.success()) {
            decodeStatus = "FAILED";
        } else if (watched) {
            decodeStatus = "PARTIAL";
        } else {
            decodeStatus = "RAW";
        }

        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("direction", direction.name());
        packet.put("monotonicNanos", monotonicNanos);
        packet.put("wallTime", wallTime);
        packet.put("wallEpochMs", wallEpochMs);
        packet.put("sessionId", session.sessionId());
        putIfNotNull(packet, "playerUid", session.playerUid());
        putIfNotNull(packet, "remoteEndpoint", session.remoteEndpoint());
        packet.put("opcode", opcode);
        packet.put("name", name);
        packet.put("watched", watched);
        packet.put("headerLength", header.length);
        packet.put("payloadLength", payload.length);
        putIfNotNull(packet, "clientSequenceId", packetHead.clientSequenceId());
        putIfNotNull(packet, "sentMs", packetHead.sentMs());
        packet.put("payloadSha256", payloadSha256);
        packet.put("rawPath", rawRelative);
        packet.put("decodeStatus", decodeStatus);
        if (!typedDecode.decodedFields().isEmpty()) {
            packet.put(
                    "decodedFields",
                    BeyondReSemantic.redactDecodedFields(typedDecode.decodedFields()));
        }
        if (headerTree.success()) {
            packet.put("headerWireTree", headerTree.fields());
        } else {
            packet.put("headerWireError", headerTree.error());
        }
        if (payloadTree.success()) {
            packet.put("wireTree", payloadTree.fields());
        } else {
            packet.put("wireTreeError", payloadTree.error());
        }
        putIfNotNull(packet, "semanticEvent", semanticEvent);

        String correlationId =
                packetHead.clientSequenceId() == null
                        ? null
                        : session.sessionId()
                                + ":"
                                + direction.name()
                                + ":"
                                + packetHead.clientSequenceId();
        putIfNotNull(packet, "correlationId", correlationId);

        synchronized (WRITE_LOCK) {
            Files.writeString(
                    sessionDir.resolve("packets.jsonl"),
                    GSON.toJson(packet) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            if (semanticEvent != null) {
                Map<String, Object> semantic = new LinkedHashMap<>();
                semantic.put("event", semanticEvent);
                semantic.put("direction", direction.name());
                semantic.put("sessionId", session.sessionId());
                semantic.put("opcode", opcode);
                semantic.put("name", name);
                semantic.put("payloadSha256", payloadSha256);
                putIfNotNull(semantic, "correlationId", correlationId);
                Files.writeString(
                        sessionDir.resolve("semantic-events.jsonl"),
                        GSON.toJson(semantic) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        }
    }

    private static void writeRawIfAbsent(Path rawPath, byte[] payload) throws Exception {
        try {
            Files.write(rawPath, payload, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ignored) {
            // SHA-256 names make identical payload artifacts naturally deduplicate.
        }
    }

    private static void writeManifestIfAbsent(
            Path manifestPath, ConfigContainer.BeyondReRecorderOptions options) throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("formatVersion", 1);
        manifest.put("createdAt", Instant.now().toString());
        manifest.put("clientVersion", emu.grasscutter.GameConstants.VERSION);
        manifest.put("astaPsCommit", System.getProperty("grasscutter.git.commit", "unknown"));
        manifest.put("upstreamBaseSha", System.getProperty("grasscutter.upstream.commit", "unknown"));
        manifest.put("rawPayloadWarning", "Raw research captures may contain sensitive player data.");
        Map<String, Object> recorder = new LinkedHashMap<>();
        recorder.put("captureAllPackets", options.captureAllPackets);
        recorder.put("wireTreeMaxDepth", options.wireTreeMaxDepth);
        recorder.put("wireTreeMaxFields", options.wireTreeMaxFields);
        recorder.put("wireTreeMaxNestedBytes", options.wireTreeMaxNestedBytes);
        recorder.put("watchNamePrefixes", options.watchNamePrefixes);
        recorder.put("watchOpcodes", options.watchOpcodes);
        manifest.put("recorderOptions", recorder);
        try {
            Files.writeString(
                    manifestPath,
                    GSON.toJson(manifest) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ignored) {
        }
    }

    private static void ensureFile(Path path) throws Exception {
        try {
            Files.createFile(path);
        } catch (FileAlreadyExistsException ignored) {
        }
    }

    private static PacketHeadMetadata parsePacketHead(byte[] header) {
        if (header.length == 0) return new PacketHeadMetadata(null, null);
        try {
            PacketHead packetHead = PacketHead.parseFrom(header);
            return new PacketHeadMetadata(packetHead.getClientSequenceId(), packetHead.getSentMs());
        } catch (Throwable ignored) {
            return new PacketHeadMetadata(null, null);
        }
    }

    private static TypedDecode decodeTyped(String name, byte[] payload) {
        if (name == null || name.isBlank() || "UNKNOWN".equals(name)) {
            return new TypedDecode(false, false, Map.of());
        }
        try {
            String className = "emu.grasscutter.net.proto." + name + "OuterClass$" + name;
            Class<?> type = Class.forName(className);
            Method parseFrom = type.getMethod("parseFrom", byte[].class);
            Object parsed = parseFrom.invoke(null, payload);
            if (!(parsed instanceof Message message)) {
                return new TypedDecode(true, false, Map.of());
            }
            return new TypedDecode(true, true, messageFields(message));
        } catch (ClassNotFoundException | NoSuchMethodException missingType) {
            return new TypedDecode(false, false, Map.of());
        } catch (Throwable invalidPayload) {
            return new TypedDecode(true, false, Map.of());
        }
    }

    private static Map<String, Object> messageFields(Message message) {
        Map<String, Object> decoded = new LinkedHashMap<>();
        for (Map.Entry<FieldDescriptor, Object> entry : message.getAllFields().entrySet()) {
            decoded.put(entry.getKey().getName(), normalizeProtoValue(entry.getValue()));
        }
        return decoded;
    }

    private static Object normalizeProtoValue(Object value) {
        if (value instanceof Message message) return messageFields(message);
        if (value instanceof ByteString bytes) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("byteLength", bytes.size());
            summary.put("sha256", sha256(bytes.toByteArray()));
            return summary;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) normalized.add(normalizeProtoValue(item));
            return normalized;
        }
        if (value instanceof com.google.protobuf.Descriptors.EnumValueDescriptor enumValue) {
            return enumValue.getName();
        }
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sanitizeSessionId(String sessionId) {
        String value = sessionId == null || sessionId.isBlank() ? "unknown" : sessionId;
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private record PacketHeadMetadata(Integer clientSequenceId, Long sentMs) {}

    private record TypedDecode(
            boolean knownType, boolean success, Map<String, Object> decodedFields) {}
}
