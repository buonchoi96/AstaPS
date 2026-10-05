package emu.grasscutter.server.game.re;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetAuthkeyRspOuterClass.GetAuthkeyRsp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BeyondReAuthkeyRedactionTest {
    @TempDir Path tempDir;

    @Test
    void redactsGeneratedAuthkeyOnlyInExplicitResearchCaptureWithoutChangingRawBytes()
            throws Exception {
        var options = new ConfigContainer.BeyondReRecorderOptions();
        options.enabled = true;
        assertFalse(options.captureAllPackets);
        assertFalse(BeyondReSemantic.isWatched(options, PacketOpcodes.GetAuthkeyRsp, "GetAuthkeyRsp"));
        options.watchOpcodes = new int[] {PacketOpcodes.GetAuthkeyRsp};
        String secret = "synthetic-authkey-private-value";
        byte[] payload =
                GetAuthkeyRsp.newBuilder()
                        .setAuthkey(secret)
                        .setGameBiz("hk4e_global")
                        .setRetcode(1)
                        .build()
                        .toByteArray();
        byte[] original = payload.clone();

        BeyondReRecorder.record(
                options,
                tempDir,
                new BeyondReRecorder.SessionInfo("authkey", 10001, null),
                BeyondReRecorder.Direction.S2C,
                PacketOpcodes.GetAuthkeyRsp,
                "GetAuthkeyRsp",
                new byte[0],
                payload);

        Path sessionDir = tempDir.resolve("session-authkey");
        var lines = Files.readAllLines(sessionDir.resolve("packets.jsonl"));
        assertEquals(1, lines.size());
        assertFalse(lines.get(0).contains(secret));
        JsonObject packet = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertEquals("KNOWN", packet.get("decodeStatus").getAsString());
        JsonObject decoded = packet.getAsJsonObject("decodedFields");
        assertEquals("REDACTED", decoded.get("authkey").getAsString());
        assertEquals("hk4e_global", decoded.get("game_biz").getAsString());
        assertEquals(1, decoded.get("retcode").getAsInt());
        assertArrayEquals(original, payload);
        assertArrayEquals(
                original,
                Files.readAllBytes(sessionDir.resolve(packet.get("rawPath").getAsString())));
    }

    @Test
    void redactsAuthkeySpellingsInPlainAndObfuscatedNestedStructures() {
        Map<String, Object> decoded =
                Map.of(
                        "authkey", "plain-secret",
                        "auth_key", "underscored-secret",
                        "auth-key", "hyphenated-secret",
                        "AUTHKEY", "uppercase-secret",
                        "nested", List.of(Map.of("authkey", "nested-secret", "uid", 10001)),
                        "ABCDEFGHIJK", Map.of("authkey", "obfuscated-parent-secret"),
                        "game_biz", "hk4e_global");

        Map<String, Object> redacted = BeyondReSemantic.redactDecodedFields(decoded);
        for (String key : List.of("authkey", "auth_key", "auth-key", "AUTHKEY")) {
            assertEquals("REDACTED", redacted.get(key));
        }
        assertFalse(redacted.toString().contains("-secret"));
        assertEquals("hk4e_global", redacted.get("game_biz"));
        assertTrue(redacted.toString().contains("10001"));
        assertEquals("plain-secret", decoded.get("authkey"));
    }
}
