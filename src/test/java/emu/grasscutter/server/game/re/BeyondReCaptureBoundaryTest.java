package emu.grasscutter.server.game.re;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BeyondReCaptureBoundaryTest {
    @TempDir Path tempDir;

    @Test
    void capturesUnknownOpcodeOnlyWhenAllPacketCaptureIsExplicitlySelected() throws Exception {
        var options = new ConfigContainer.BeyondReRecorderOptions();
        options.enabled = true;
        record(options, "unknown", new byte[0], new byte[] {0x08, 0x01});
        assertFalse(Files.exists(tempDir.resolve("session-unknown")));

        options.captureAllPackets = true;
        byte[] payload = new byte[] {0x08, 0x01};
        record(options, "unknown", new byte[0], payload);
        JsonObject packet = onlyPacket("unknown");
        assertEquals("UNKNOWN", packet.get("name").getAsString());
        assertEquals("RAW", packet.get("decodeStatus").getAsString());
        assertFalse(packet.get("watched").getAsBoolean());
        assertArrayEquals(payload, Files.readAllBytes(rawPath("unknown", packet)));
    }

    @Test
    void retainsUnknownHeaderFieldsAlongsideCorrelationMetadata() throws Exception {
        var options = new ConfigContainer.BeyondReRecorderOptions();
        options.enabled = true;
        options.captureAllPackets = true;
        byte[] header =
                PacketHead.newBuilder()
                        .setClientSequenceId(73)
                        .setSentMs(1234567890L)
                        .setUnknownFields(
                                UnknownFieldSet.newBuilder()
                                        .addField(
                                                321,
                                                UnknownFieldSet.Field.newBuilder()
                                                        .addVarint(99)
                                                        .build())
                                        .build())
                        .build()
                        .toByteArray();
        byte[] original = header.clone();

        record(options, "header", header, new byte[0]);
        JsonObject packet = onlyPacket("header");
        assertEquals(73, packet.get("clientSequenceId").getAsInt());
        assertEquals(1234567890L, packet.get("sentMs").getAsLong());
        assertEquals("header:C2S:73", packet.get("correlationId").getAsString());
        JsonObject unknownField = null;
        for (var field : packet.getAsJsonArray("headerWireTree")) {
            JsonObject node = field.getAsJsonObject();
            if (node.get("fieldNumber").getAsInt() == 321) unknownField = node;
        }
        assertNotNull(unknownField);
        assertEquals("99", unknownField.get("varint").getAsString());
        assertEquals(0, unknownField.get("wireType").getAsInt());
        assertArrayEquals(original, header);
    }

    @Test
    void malformedHeaderDoesNotPreventRawPayloadCapture() throws Exception {
        var options = new ConfigContainer.BeyondReRecorderOptions();
        options.enabled = true;
        options.captureAllPackets = true;
        byte[] payload = new byte[] {0x08, 0x01};
        record(options, "bad-header", new byte[] {(byte) 0x80}, payload);

        JsonObject packet = onlyPacket("bad-header");
        assertTrue(packet.has("headerWireError"));
        assertFalse(packet.has("clientSequenceId"));
        assertFalse(packet.has("sentMs"));
        assertFalse(packet.has("correlationId"));
        assertEquals("RAW", packet.get("decodeStatus").getAsString());
        assertArrayEquals(payload, Files.readAllBytes(rawPath("bad-header", packet)));
    }

    private void record(
            ConfigContainer.BeyondReRecorderOptions options,
            String id,
            byte[] header,
            byte[] payload) {
        BeyondReRecorder.record(
                options,
                tempDir,
                new BeyondReRecorder.SessionInfo(id, null, null),
                BeyondReRecorder.Direction.C2S,
                65000,
                "UNKNOWN",
                header,
                payload);
    }

    private JsonObject onlyPacket(String id) throws Exception {
        var lines = Files.readAllLines(tempDir.resolve("session-" + id).resolve("packets.jsonl"));
        assertEquals(1, lines.size());
        return JsonParser.parseString(lines.get(0)).getAsJsonObject();
    }

    private Path rawPath(String id, JsonObject packet) {
        return tempDir.resolve("session-" + id).resolve(packet.get("rawPath").getAsString());
    }
}
