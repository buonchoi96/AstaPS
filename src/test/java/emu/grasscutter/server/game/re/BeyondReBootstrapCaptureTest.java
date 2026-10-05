package emu.grasscutter.server.game.re;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.BeyondPlayerInfo._BeyondPlayerInfo;
import emu.grasscutter.net.proto.WorldPlayerInfoNotifyOuterClass.WorldPlayerInfoNotify;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BeyondReBootstrapCaptureTest {
    @TempDir Path tempDir;

    @Test
    void defaultWatchFilterCapturesBootstrapPresenceWhenExplicitlyEnabled() throws Exception {
        var options = new ConfigContainer.BeyondReRecorderOptions();
        options.enabled = true;
        assertFalse(options.captureAllPackets);
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
                new BeyondReRecorder.SessionInfo("bootstrap", 10005, null),
                BeyondReRecorder.Direction.S2C,
                PacketOpcodes.WorldPlayerInfoNotify,
                "WorldPlayerInfoNotify",
                new byte[0],
                payload);

        Path sessionDir = tempDir.resolve("session-bootstrap");
        Path packets = sessionDir.resolve("packets.jsonl");
        assertTrue(Files.exists(packets), "default watch filter must retain bootstrap presence");
        var lines = Files.readAllLines(packets);
        assertEquals(1, lines.size());
        JsonObject packet = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertTrue(packet.get("watched").getAsBoolean());
        assertEquals("KNOWN", packet.get("decodeStatus").getAsString());
        assertEquals(
                10005,
                packet.getAsJsonObject("decodedFields")
                        .getAsJsonArray("_beyond_player_info_list")
                        .get(0)
                        .getAsJsonObject()
                        .get("uid")
                        .getAsInt());
        assertArrayEquals(
                payload, Files.readAllBytes(sessionDir.resolve(packet.get("rawPath").getAsString())));
    }

    @Test
    void bootstrapWatchDoesNotEnableRecordingOrCaptureUnrelatedPackets() throws Exception {
        var options = new ConfigContainer.BeyondReRecorderOptions();
        assertFalse(options.enabled);
        var session = new BeyondReRecorder.SessionInfo("disabled-bootstrap", null, null);
        BeyondReRecorder.record(
                options,
                tempDir,
                session,
                BeyondReRecorder.Direction.S2C,
                PacketOpcodes.WorldPlayerInfoNotify,
                "WorldPlayerInfoNotify",
                new byte[0],
                new byte[0]);

        options.enabled = true;
        BeyondReRecorder.record(
                options,
                tempDir,
                session,
                BeyondReRecorder.Direction.C2S,
                PacketOpcodes.PingReq,
                "PingReq",
                new byte[0],
                new byte[0]);
        try (var entries = Files.list(tempDir)) {
            assertEquals(0, entries.count());
        }
    }
}
