package emu.grasscutter.server.game.re;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.game.beyond.BeyondPlayerStateService;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.packet.send.PacketGetBeyondPlayerInfoRsp;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BeyondReRecoveredPresenceTest {
    @TempDir Path tempDir;

    @Test
    void decodesRecoveredRequestAndResponseWhileRetainingRawUnknownFields() throws Exception {
        var options = new ConfigContainer.BeyondReRecorderOptions();
        options.enabled = true;
        byte[] request = {0x70, 1, 0x50, 9, (byte) 0xa0, 0x06, 7}; // unknown field 100
        byte[] header = PacketHead.newBuilder().setClientSequenceId(314).build().toByteArray();
        var response = new PacketGetBeyondPlayerInfoRsp(header, request,
                new BeyondPlayerStateService(), uid -> uid == 1);
        var session = new BeyondReRecorder.SessionInfo("presence", 1, null);
        BeyondReRecorder.record(options, tempDir, session, BeyondReRecorder.Direction.C2S,
                5960, "_GetBeyondPlayerInfoReq", header, request);
        BeyondReRecorder.record(options, tempDir, session, BeyondReRecorder.Direction.S2C,
                9779, "_GetBeyondPlayerInfoRsp", response.getHeader(), response.getData());
        var directory = tempDir.resolve("session-presence");
        var lines = Files.readAllLines(directory.resolve("packets.jsonl"));
        assertEquals(2, lines.size());
        var req = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        var rsp = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        assertEquals("KNOWN", req.get("decodeStatus").getAsString());
        assertEquals("KNOWN", rsp.get("decodeStatus").getAsString());
        assertEquals(9, req.getAsJsonObject("decodedFields").get("reason").getAsInt());
        assertEquals(1, rsp.getAsJsonObject("decodedFields").getAsJsonArray("_beyond_player_info")
                .get(0).getAsJsonObject().get("uid").getAsInt());
        assertEquals(req.get("requestCorrelationId"), rsp.get("requestCorrelationId"));
        assertArrayEquals(request, Files.readAllBytes(directory.resolve(req.get("rawPath").getAsString())));
        boolean preservedUnknown = false;
        for (var field : req.getAsJsonArray("wireTree")) {
            if (field.getAsJsonObject().get("fieldNumber").getAsInt() == 100) preservedUnknown = true;
        }
        assertTrue(preservedUnknown);
    }

    @Test
    void malformedRecoveredRequestKeepsRawCaptureAndReportsFailedDecode() throws Exception {
        var options = new ConfigContainer.BeyondReRecorderOptions();
        options.enabled = true;
        byte[] payload = {(byte) 0x80};
        BeyondReRecorder.record(options, tempDir, new BeyondReRecorder.SessionInfo("bad", 1, null),
                BeyondReRecorder.Direction.C2S, 5960, "_GetBeyondPlayerInfoReq", new byte[0], payload);
        var directory = tempDir.resolve("session-bad");
        var record = JsonParser.parseString(Files.readString(directory.resolve("packets.jsonl"))).getAsJsonObject();
        assertEquals("FAILED", record.get("decodeStatus").getAsString());
        assertArrayEquals(payload, Files.readAllBytes(directory.resolve(record.get("rawPath").getAsString())));
    }
}
