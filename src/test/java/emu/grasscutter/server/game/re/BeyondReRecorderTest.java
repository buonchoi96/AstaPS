package emu.grasscutter.server.game.re;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.BeyondPlayerInfo._BeyondPlayerInfo;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.PingReqOuterClass.PingReq;
import emu.grasscutter.net.proto.WorldPlayerInfoNotifyOuterClass.WorldPlayerInfoNotify;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BeyondReRecorderTest {
    @TempDir Path tempDir;

    @Test
    void disabledRecorderIsInert() throws Exception {
        var options = options(false);

        BeyondReRecorder.record(
                options,
                tempDir,
                new BeyondReRecorder.SessionInfo("disabled", 10001, "127.0.0.1:22101"),
                BeyondReRecorder.Direction.C2S,
                PacketOpcodes.PingReq,
                "PingReq",
                new byte[0],
                new byte[] {0x08, 0x01});

        try (var entries = Files.list(tempDir)) {
            assertEquals(0, entries.count(), "disabled mode must not create capture artifacts");
        }
    }

    @Test
    void capturesKnownHandledPacketWithPacketHeadMetadata() throws Exception {
        var options = options(true);
        byte[] header =
                PacketHead.newBuilder()
                        .setClientSequenceId(42)
                        .setSentMs(1_234_567_890L)
                        .build()
                        .toByteArray();
        byte[] payload = PingReq.newBuilder().build().toByteArray();

        BeyondReRecorder.record(
                options,
                tempDir,
                new BeyondReRecorder.SessionInfo("handled", 10002, "10.0.0.2:22101"),
                BeyondReRecorder.Direction.C2S,
                PacketOpcodes.PingReq,
                "PingReq",
                header,
                payload);

        JsonObject packet = onlyPacket("handled");
        assertEquals("C2S", packet.get("direction").getAsString());
        assertEquals(PacketOpcodes.PingReq, packet.get("opcode").getAsInt());
        assertEquals("PingReq", packet.get("name").getAsString());
        assertEquals(42, packet.get("clientSequenceId").getAsInt());
        assertEquals(1_234_567_890L, packet.get("sentMs").getAsLong());
        assertEquals("KNOWN", packet.get("decodeStatus").getAsString());
        assertEquals(10002, packet.get("playerUid").getAsInt());
        assertEquals("10.0.0.2:22101", packet.get("remoteEndpoint").getAsString());
    }

    @Test
    void capturesUnhandledUgcCandidateAndKeepsCandidateSemantics() throws Exception {
        var options = options(true);
        byte[] payload = new byte[] {0x08, 0x01};

        BeyondReRecorder.record(
                options,
                tempDir,
                new BeyondReRecorder.SessionInfo("unhandled", null, null),
                BeyondReRecorder.Direction.C2S,
                20869,
                "_UgcDungeonSaveDataReq",
                new byte[0],
                payload);

        JsonObject packet = onlyPacket("unhandled");
        assertTrue(packet.get("watched").getAsBoolean());
        assertEquals("UGC_CANDIDATE_SAVE", packet.get("semanticEvent").getAsString());
        assertEquals("PARTIAL", packet.get("decodeStatus").getAsString());

        var semanticLines =
                Files.readAllLines(sessionDir("unhandled").resolve("semantic-events.jsonl"));
        assertEquals(1, semanticLines.size());
        assertEquals(
                "UGC_CANDIDATE_SAVE",
                JsonParser.parseString(semanticLines.get(0))
                        .getAsJsonObject()
                        .get("event")
                        .getAsString());
        assertFalse(semanticLines.get(0).contains("MILIASTRA"));
    }

    @Test
    void recordsVerifiedBeyondPresenceSemanticEventWithPacketRecordLink() throws Exception {
        var options = options(true);
        byte[] payload =
                WorldPlayerInfoNotify.newBuilder()
                        .addBeyondPlayerInfoList(
                                _BeyondPlayerInfo.newBuilder()
                                        .setUid(10005)
                                        .setOnlineStateValue(1)
                                        .setWorldTypeValue(0))
                        .build()
                        .toByteArray();

        BeyondReRecorder.record(
                options,
                tempDir,
                new BeyondReRecorder.SessionInfo("presence", 10005, null),
                BeyondReRecorder.Direction.S2C,
                PacketOpcodes.WorldPlayerInfoNotify,
                "WorldPlayerInfoNotify",
                new byte[0],
                payload);

        JsonObject packet = onlyPacket("presence");
        assertEquals("KNOWN", packet.get("decodeStatus").getAsString());
        assertEquals("BEYOND_PLAYER_PRESENCE", packet.get("semanticEvent").getAsString());

        var semanticLines =
                Files.readAllLines(sessionDir("presence").resolve("semantic-events.jsonl"));
        assertEquals(1, semanticLines.size());
        JsonObject semantic = JsonParser.parseString(semanticLines.get(0)).getAsJsonObject();
        assertEquals("BEYOND_PLAYER_PRESENCE", semantic.get("event").getAsString());
        assertEquals(packet.get("recordId").getAsString(), semantic.get("recordId").getAsString());
        assertEquals(
                packet.get("payloadSha256").getAsString(),
                semantic.get("payloadSha256").getAsString());
    }

    @Test
    void persistsLargePayloadWithoutTruncation() throws Exception {
        var options = options(true);
        byte[] payload = new byte[512 * 1024 + 17];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31);
        }

        BeyondReRecorder.record(
                options,
                tempDir,
                new BeyondReRecorder.SessionInfo("large", 10003, null),
                BeyondReRecorder.Direction.S2C,
                65000,
                "UNKNOWN",
                new byte[0],
                payload);

        JsonObject packet = onlyPacket("large");
        Path raw = sessionDir("large").resolve(packet.get("rawPath").getAsString());
        assertEquals(payload.length, Files.size(raw));
        assertArrayEquals(payload, Files.readAllBytes(raw));
    }

    @Test
    void malformedProtobufCannotBreakSubsequentLogging() throws Exception {
        var options = options(true);
        var session = new BeyondReRecorder.SessionInfo("malformed", null, null);

        assertDoesNotThrow(
                () ->
                        BeyondReRecorder.record(
                                options,
                                tempDir,
                                session,
                                BeyondReRecorder.Direction.C2S,
                                65001,
                                "UNKNOWN",
                                new byte[0],
                                new byte[] {(byte) 0x80}));
        assertDoesNotThrow(
                () ->
                        BeyondReRecorder.record(
                                options,
                                tempDir,
                                session,
                                BeyondReRecorder.Direction.C2S,
                                65002,
                                "UNKNOWN",
                                new byte[0],
                                new byte[] {0x08, 0x01}));

        var lines = Files.readAllLines(sessionDir("malformed").resolve("packets.jsonl"));
        assertEquals(2, lines.size());
        assertEquals(
                "FAILED",
                JsonParser.parseString(lines.get(0))
                        .getAsJsonObject()
                        .get("decodeStatus")
                        .getAsString());
        assertEquals(
                "RAW",
                JsonParser.parseString(lines.get(1))
                        .getAsJsonObject()
                        .get("decodeStatus")
                        .getAsString());
    }

    @Test
    void semanticRedactionRemovesSecretsButKeepsNonSensitiveFields() {
        Map<String, Object> decoded =
                Map.of(
                        "uid", 12345,
                        "hall_passcode", "hunter2",
                        "chat_text", "private chat body",
                        "account_id", "account-123",
                        "session_id", "session-456",
                        "ABCDEFGHIJK", "unknown private text",
                        "nested",
                                Map.of(
                                        "access_token", "token-abc",
                                        "display_name", "safe-name"));

        Map<String, Object> redacted = BeyondReSemantic.redactDecodedFields(decoded);
        String json = redacted.toString();

        assertEquals(12345, redacted.get("uid"));
        assertFalse(json.contains("hunter2"));
        assertFalse(json.contains("private chat body"));
        assertFalse(json.contains("account-123"));
        assertFalse(json.contains("session-456"));
        assertFalse(json.contains("unknown private text"));
        assertFalse(json.contains("token-abc"));
        assertTrue(json.contains("safe-name"));
        assertTrue(json.contains("REDACTED"));
    }

    @Test
    void classifiesBeyondConsolePacketRolesWithoutGuessingSemantics() {
        assertEquals("REQ", BeyondReSemantic.packetKind("_UgcDungeonSaveDataReq"));
        assertEquals("RSP", BeyondReSemantic.packetKind("_UgcEnterDungeonRsp"));
        assertEquals("NOTIFY", BeyondReSemantic.packetKind("_BeyondHallChangeTagsNotify"));
        assertEquals("OTHER", BeyondReSemantic.packetKind("UNKNOWN"));
        assertEquals("OTHER", BeyondReSemantic.packetKind(null));
    }

    @Test
    void pairsReqAndRspBySequenceWithoutPairingNotifications() throws Exception {
        var options = options(true);
        byte[] header =
                PacketHead.newBuilder()
                        .setClientSequenceId(73)
                        .setSentMs(1_234_567_891L)
                        .build()
                        .toByteArray();
        var session = new BeyondReRecorder.SessionInfo("pairing", 10006, null);

        BeyondReRecorder.record(
                options,
                tempDir,
                session,
                BeyondReRecorder.Direction.C2S,
                1971,
                "_UgcEnterDungeonReq",
                header,
                new byte[] {0x08, 0x01});
        BeyondReRecorder.record(
                options,
                tempDir,
                session,
                BeyondReRecorder.Direction.S2C,
                1660,
                "_UgcEnterDungeonRsp",
                header,
                new byte[0]);
        BeyondReRecorder.record(
                options,
                tempDir,
                session,
                BeyondReRecorder.Direction.S2C,
                5825,
                "_UgcDungeonPlayRecordNotify",
                header,
                new byte[0]);

        List<String> lines =
                Files.readAllLines(sessionDir("pairing").resolve("packets.jsonl"));
        assertEquals(3, lines.size());
        JsonObject req = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        JsonObject rsp = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        JsonObject notify = JsonParser.parseString(lines.get(2)).getAsJsonObject();

        assertEquals(73, BeyondReRecorder.clientSequenceId(header));
        assertEquals("pairing:73", req.get("requestCorrelationId").getAsString());
        assertEquals(
                req.get("requestCorrelationId").getAsString(),
                rsp.get("requestCorrelationId").getAsString());
        assertFalse(notify.has("requestCorrelationId"));
        assertNotEquals(
                req.get("correlationId").getAsString(), rsp.get("correlationId").getAsString());
    }

    @Test
    void watchesWholeUgcDungeonFamilyWithoutCapturingUnrelatedUgcByName() {
        var options = options(true);
        options.captureAllPackets = false;
        options.watchOpcodes = new int[0];
        options.watchNamePrefixes = new String[0];

        assertTrue(BeyondReSemantic.isWatched(options, 1, "EditUgcDungeonNotify"));
        assertTrue(BeyondReSemantic.isWatched(options, 2, "_UgcDungeonSaveDataReq"));
        assertTrue(BeyondReSemantic.isWatched(options, 3, "_UgcEnterDungeonRsp"));
        assertFalse(BeyondReSemantic.isWatched(options, 4, "GetUgcReq"));
        assertFalse(BeyondReSemantic.isWatched(options, 5, "MusicGameCreateBeatmapReq"));
    }

    @Test
    void rawPayloadHashMatchesPersistedBytesAndIsDeduplicated() throws Exception {
        var options = options(true);
        byte[] payload = "same raw payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var session = new BeyondReRecorder.SessionInfo("hash", 10004, null);

        BeyondReRecorder.record(
                options,
                tempDir,
                session,
                BeyondReRecorder.Direction.C2S,
                65003,
                "UNKNOWN",
                new byte[0],
                payload);
        BeyondReRecorder.record(
                options,
                tempDir,
                session,
                BeyondReRecorder.Direction.S2C,
                65004,
                "UNKNOWN",
                new byte[0],
                payload);

        List<String> lines = Files.readAllLines(sessionDir("hash").resolve("packets.jsonl"));
        JsonObject first = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        JsonObject second = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        String expectedHash = sha256(payload);

        assertEquals(expectedHash, first.get("payloadSha256").getAsString());
        assertEquals(expectedHash, second.get("payloadSha256").getAsString());
        assertEquals(first.get("rawPath").getAsString(), second.get("rawPath").getAsString());
        Path raw = sessionDir("hash").resolve(first.get("rawPath").getAsString());
        assertArrayEquals(payload, Files.readAllBytes(raw));
        try (var files = Files.list(sessionDir("hash").resolve("raw"))) {
            assertEquals(1, files.count(), "identical raw payloads should share one artifact");
        }
    }

    private ConfigContainer.BeyondReRecorderOptions options(boolean enabled) {
        var options = new ConfigContainer.BeyondReRecorderOptions();
        options.enabled = enabled;
        options.captureAllPackets = true;
        options.wireTreeMaxDepth = 4;
        options.wireTreeMaxFields = 512;
        options.wireTreeMaxNestedBytes = 64 * 1024;
        return options;
    }

    private Path sessionDir(String id) {
        return tempDir.resolve("session-" + id);
    }

    private JsonObject onlyPacket(String id) throws Exception {
        var lines = Files.readAllLines(sessionDir(id).resolve("packets.jsonl"));
        assertEquals(1, lines.size());
        return JsonParser.parseString(lines.get(0)).getAsJsonObject();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
