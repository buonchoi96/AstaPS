package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.proto.DEDHCCHNKEOOuterClass.DEDHCCHNKEO;
import emu.grasscutter.net.proto.KFMCDDAIOMLOuterClass.KFMCDDAIOML;
import emu.grasscutter.net.proto.WorldPlayerInfoNotifyOuterClass.WorldPlayerInfoNotify;
import org.junit.jupiter.api.Test;

class BeyondPlayerStateTest {
    @Test
    void serializesVerifiedPresenceAndKeepsUnknownDetailFieldsByWireNumber() {
        var unresolved =
                new BeyondPlayerState.UnresolvedDetailFields(
                        KFMCDDAIOML.newBuilder().build(),
                        44L,
                        true,
                        DEDHCCHNKEO.newBuilder().build(),
                        77L,
                        88L);
        var state =
                BeyondPlayerState.onlineBeyond(
                        123456789,
                        BeyondPlayerState.DetailState.EDIT,
                        true,
                        unresolved);

        var proto = state.toProto();

        assertEquals(123456789, proto.getUid());
        assertEquals(1, proto.getOnlineStateValue());
        assertEquals(1, proto.getWorldTypeValue());
        assertTrue(proto.hasBydPlayerDetailOnlineInfo());

        var detail = proto.getBydPlayerDetailOnlineInfo();
        assertEquals(3, detail.getJCEIDIDBFHMValue());
        assertTrue(detail.getIsInTeam());
        assertTrue(detail.hasJGJNGLBJLJO());
        assertEquals(44L, detail.getLBFGNBPFNIH());
        assertTrue(detail.getLEGKLKCNKJB());
        assertTrue(detail.hasMKODMDPGDHK());
        assertEquals(77L, detail.getJKBOBLJFKBP());
        assertEquals(88L, detail.getHPLGJAAJOGG());
    }

    @Test
    void serializesAllVerifiedStateEnumsWithoutInventingAdditionalSemantics() {
        var offline =
                new BeyondPlayerState(
                        20000,
                        BeyondPlayerState.OnlineState.OFFLINE,
                        BeyondPlayerState.WorldType.TEYVAT,
                        null)
                        .toProto();

        assertEquals(0, offline.getOnlineStateValue());
        assertEquals(0, offline.getWorldTypeValue());
        assertFalse(offline.hasBydPlayerDetailOnlineInfo());

        var detailStates =
                new BeyondPlayerState.DetailState[] {
                    BeyondPlayerState.DetailState.NONE,
                    BeyondPlayerState.DetailState.HALL,
                    BeyondPlayerState.DetailState.DUNGEON,
                    BeyondPlayerState.DetailState.EDIT
                };

        for (int i = 0; i < detailStates.length; i++) {
            var proto =
                    BeyondPlayerState.onlineBeyond(
                                    21000 + i,
                                    detailStates[i],
                                    false,
                                    BeyondPlayerState.UnresolvedDetailFields.EMPTY)
                            .toProto();

            assertEquals(1, proto.getOnlineStateValue());
            assertEquals(1, proto.getWorldTypeValue());
            assertEquals(i, proto.getBydPlayerDetailOnlineInfo().getJCEIDIDBFHMValue());
            assertFalse(proto.getBydPlayerDetailOnlineInfo().getIsInTeam());
        }
    }

    @Test
    void resolvesUntrackedWorldPlayerAsOnlineTeyvatWithoutInventingDetailState() {
        var service = new BeyondPlayerStateService();

        var state = service.resolveForWorldPlayer(10001);

        assertEquals(BeyondPlayerState.OnlineState.ONLINE, state.onlineState());
        assertEquals(BeyondPlayerState.WorldType.TEYVAT, state.worldType());
        assertNull(state.onlineDetail());
    }

    @Test
    void resolvingWorldPlayerPreservesExplicitBeyondDetailButForcesOnlinePresence() {
        var service = new BeyondPlayerStateService();
        service.put(
                new BeyondPlayerState(
                        10002,
                        BeyondPlayerState.OnlineState.OFFLINE,
                        BeyondPlayerState.WorldType.BEYOND,
                        new BeyondPlayerState.OnlineDetail(
                                BeyondPlayerState.DetailState.HALL,
                                false,
                                BeyondPlayerState.UnresolvedDetailFields.EMPTY)));

        var state = service.resolveForWorldPlayer(10002);

        assertEquals(BeyondPlayerState.OnlineState.ONLINE, state.onlineState());
        assertEquals(BeyondPlayerState.WorldType.BEYOND, state.worldType());
        assertNotNull(state.onlineDetail());
        assertEquals(BeyondPlayerState.DetailState.HALL, state.onlineDetail().state());
        assertFalse(state.onlineDetail().inTeam());
    }

    @Test
    void appendsResolvedPresenceToWorldPlayerInfoNotifyBeyondList() {
        var service = new BeyondPlayerStateService();
        service.put(
                BeyondPlayerState.onlineBeyond(
                        10003,
                        BeyondPlayerState.DetailState.DUNGEON,
                        true,
                        BeyondPlayerState.UnresolvedDetailFields.EMPTY));
        var notify = WorldPlayerInfoNotify.newBuilder();

        service.appendWorldPlayerPresence(notify, 10003);

        var built = notify.build();
        assertEquals(1, built.getBeyondPlayerInfoListCount());
        var presence = built.getBeyondPlayerInfoList(0);
        assertEquals(10003, presence.getUid());
        assertEquals(1, presence.getOnlineStateValue());
        assertEquals(1, presence.getWorldTypeValue());
        assertEquals(2, presence.getBydPlayerDetailOnlineInfo().getJCEIDIDBFHMValue());
        assertTrue(presence.getBydPlayerDetailOnlineInfo().getIsInTeam());
    }
}
