package emu.grasscutter.server.game.re;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ChatInfoOuterClass.ChatInfo;
import emu.grasscutter.net.proto.PlayerChatNotifyOuterClass.PlayerChatNotify;
import emu.grasscutter.net.proto.PlayerChatReqOuterClass.PlayerChatReq;
import emu.grasscutter.net.proto.PrivateChatReqOuterClass.PrivateChatReq;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BeyondReChatRedactionTest {
    @TempDir Path tempDir;

    @Test
    void redactsPrivateChatTextWithoutModifyingRawEvidence() throws Exception {
        String secret = "private-chat-content-7.1";
        byte[] payload =
                PrivateChatReq.newBuilder().setTargetUid(10002).setText(secret).build().toByteArray();
        byte[] original = payload.clone();
        JsonObject packet =
                record(
                        "private",
                        BeyondReRecorder.Direction.C2S,
                        PacketOpcodes.PrivateChatReq,
                        "PrivateChatReq",
                        payload);

        assertFalse(packet.toString().contains(secret), "decoded text must not leak into JSONL");
        assertEquals("REDACTED", packet.getAsJsonObject("decodedFields").get("text").getAsString());
        assertEquals(10002, packet.getAsJsonObject("decodedFields").get("target_uid").getAsInt());
        assertRawEvidence("private", packet, original, payload);
    }

    @Test
    void redactsNestedChatTextInBothDirectionsWithoutModifyingRawEvidence() throws Exception {
        String secret = "channel-chat-content-7.1";
        var chatInfo = ChatInfo.newBuilder().setUid(10003).setText(secret);
        byte[] request =
                PlayerChatReq.newBuilder().setChannelId(1).setChatInfo(chatInfo).build().toByteArray();
        byte[] notify =
                PlayerChatNotify.newBuilder().setChannelId(1).setChatInfo(chatInfo).build().toByteArray();
        byte[] originalRequest = request.clone();
        byte[] originalNotify = notify.clone();

        JsonObject req =
                record(
                        "chat-req",
                        BeyondReRecorder.Direction.C2S,
                        PacketOpcodes.PlayerChatReq,
                        "PlayerChatReq",
                        request);
        JsonObject sent =
                record(
                        "chat-notify",
                        BeyondReRecorder.Direction.S2C,
                        PacketOpcodes.PlayerChatNotify,
                        "PlayerChatNotify",
                        notify);

        for (JsonObject packet : new JsonObject[] {req, sent}) {
            assertFalse(
                    packet.toString().contains(secret), "nested chat text must not leak into JSONL");
            JsonObject decoded = packet.getAsJsonObject("decodedFields");
            assertEquals(1, decoded.get("channel_id").getAsInt());
            assertEquals(10003, decoded.getAsJsonObject("chat_info").get("uid").getAsInt());
            assertEquals(
                    "REDACTED", decoded.getAsJsonObject("chat_info").get("text").getAsString());
        }
        assertRawEvidence("chat-req", req, originalRequest, request);
        assertRawEvidence("chat-notify", sent, originalNotify, notify);
    }

    private JsonObject record(
            String sessionId,
            BeyondReRecorder.Direction direction,
            int opcode,
            String name,
            byte[] payload)
            throws Exception {
        var options = new ConfigContainer.BeyondReRecorderOptions();
        options.enabled = true;
        options.captureAllPackets = true;
        BeyondReRecorder.record(
                options,
                tempDir,
                new BeyondReRecorder.SessionInfo(sessionId, 10001, null),
                direction,
                opcode,
                name,
                new byte[0],
                payload);
        var lines =
                Files.readAllLines(tempDir.resolve("session-" + sessionId).resolve("packets.jsonl"));
        assertEquals(1, lines.size());
        JsonObject packet = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertEquals("KNOWN", packet.get("decodeStatus").getAsString());
        return packet;
    }

    private void assertRawEvidence(
            String sessionId, JsonObject packet, byte[] original, byte[] payload) throws Exception {
        assertArrayEquals(original, payload);
        assertArrayEquals(
                original,
                Files.readAllBytes(
                        tempDir.resolve("session-" + sessionId)
                                .resolve(packet.get("rawPath").getAsString())));
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)),
                packet.get("payloadSha256").getAsString());
    }
}
