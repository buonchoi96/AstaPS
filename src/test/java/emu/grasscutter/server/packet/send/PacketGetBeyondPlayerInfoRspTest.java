package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.game.beyond.BeyondPlayerState;
import emu.grasscutter.game.beyond.BeyondPlayerStateService;
import emu.grasscutter.net.proto.BeyondPlayerInfo._BeyondPlayerInfo;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import org.junit.jupiter.api.Test;

final class PacketGetBeyondPlayerInfoRspTest {
    @Test
    void answersRequestedKnownPresenceAndEchoesReasonAndSequence() throws Exception {
        var states = new BeyondPlayerStateService();
        states.put(BeyondPlayerState.onlineBeyond(2, BeyondPlayerState.DetailState.EDIT, true,
                BeyondPlayerState.UnresolvedDetailFields.EMPTY));
        states.put(BeyondPlayerState.onlineTeyvat(4));
        // 7.1 source: UID list field 14, reason field 10. Use an independent wire fixture.
        byte[] payload = {0x70, 1, 0x70, 2, 0x70, 3, 0x70, 4, 0x50, 9};
        byte[] original = payload.clone();
        byte[] header = PacketHead.newBuilder().setClientSequenceId(314).build().toByteArray();
        var packet = new PacketGetBeyondPlayerInfoRsp(header, payload, states, uid -> uid == 1 || uid == 2);
        assertNotNull(packet.getData(), "player-info response must contain requested presence");
        var response = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(314, PacketHead.parseFrom(packet.getHeader()).getClientSequenceId());
        assertEquals(9L, response.getField(3).getVarintList().getFirst());
        var players = response.getField(2).getLengthDelimitedList();
        assertEquals(3, players.size(), "unknown UID 3 must not acquire fabricated presence");
        var teyvat = _BeyondPlayerInfo.parseFrom(players.get(0));
        assertEquals(1, teyvat.getUid());
        assertEquals(1, teyvat.getOnlineStateValue());
        assertEquals(0, teyvat.getWorldTypeValue());
        var editor = _BeyondPlayerInfo.parseFrom(players.get(1));
        assertEquals(2, editor.getUid());
        assertEquals(3, editor.getBydPlayerDetailOnlineInfo().getJCEIDIDBFHMValue());
        assertTrue(editor.getBydPlayerDetailOnlineInfo().getIsInTeam());
        var offline = _BeyondPlayerInfo.parseFrom(players.get(2));
        assertEquals(4, offline.getUid());
        assertEquals(0, offline.getOnlineStateValue());
        assertEquals(BeyondPlayerState.OnlineState.ONLINE, states.find(4).orElseThrow().onlineState(),
                "query must not mutate registered state");
        assertArrayEquals(original, payload);
    }

    @Test
    void keepsValidBodyWhenHeaderIsMalformedAndSupportsEmptyQuery() throws Exception {
        var states = new BeyondPlayerStateService();
        var packet = new PacketGetBeyondPlayerInfoRsp(new byte[] {(byte) 0x80},
                new byte[] {0x70, 1}, states, uid -> uid == 1);
        assertEquals(0, PacketHead.parseFrom(packet.getHeader()).getClientSequenceId());
        assertEquals(1, UnknownFieldSet.parseFrom(packet.getData()).getField(2).getLengthDelimitedList().size());
        var empty = new PacketGetBeyondPlayerInfoRsp(null, new byte[0], states, uid -> false);
        assertArrayEquals(new byte[0], empty.getData());
        assertThrows(com.google.protobuf.InvalidProtocolBufferException.class,
                () -> new PacketGetBeyondPlayerInfoRsp(null, new byte[] {(byte) 0x80}, states, uid -> false));
    }
}
