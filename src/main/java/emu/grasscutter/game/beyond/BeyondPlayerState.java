package emu.grasscutter.game.beyond;

import emu.grasscutter.net.proto.BeyondPlayerInfo._BeyondPlayerInfo;
import emu.grasscutter.net.proto.BydPlayerDetailOnlineInfo._BydPlayerDetailOnlineInfo;
import emu.grasscutter.net.proto.DEDHCCHNKEOOuterClass.DEDHCCHNKEO;
import emu.grasscutter.net.proto.KFMCDDAIOMLOuterClass.KFMCDDAIOML;
import java.util.Objects;

/**
 * Version-7.1 Beyond presence state backed only by fields whose wire meaning is directly known.
 *
 * <p>The nested detail message still has six unresolved fields. They stay identified by wire field
 * number and scalar/message type until captures establish their semantics.
 */
public record BeyondPlayerState(
        int uid, OnlineState onlineState, WorldType worldType, OnlineDetail onlineDetail) {
    public BeyondPlayerState {
        Objects.requireNonNull(onlineState);
        Objects.requireNonNull(worldType);
    }

    public enum OnlineState {
        OFFLINE(0),
        ONLINE(1);

        private final int wireValue;

        OnlineState(int wireValue) {
            this.wireValue = wireValue;
        }
    }

    public enum WorldType {
        TEYVAT(0),
        BEYOND(1);

        private final int wireValue;

        WorldType(int wireValue) {
            this.wireValue = wireValue;
        }
    }

    public enum DetailState {
        NONE(0),
        HALL(1),
        DUNGEON(2),
        EDIT(3);

        private final int wireValue;

        DetailState(int wireValue) {
            this.wireValue = wireValue;
        }
    }

    public record OnlineDetail(
            DetailState state, boolean inTeam, UnresolvedDetailFields unresolvedFields) {
        public OnlineDetail {
            Objects.requireNonNull(state);
            unresolvedFields =
                    unresolvedFields == null ? UnresolvedDetailFields.EMPTY : unresolvedFields;
        }
    }

    public record UnresolvedDetailFields(
            KFMCDDAIOML field3Message,
            Long field4UInt64,
            Boolean field5Bool,
            DEDHCCHNKEO field6Message,
            Long field7UInt64,
            Long field8UInt64) {
        public static final UnresolvedDetailFields EMPTY =
                new UnresolvedDetailFields(null, null, null, null, null, null);

        private void applyTo(_BydPlayerDetailOnlineInfo.Builder detail) {
            if (field3Message != null) {
                detail.setJGJNGLBJLJO(field3Message);
            }
            if (field4UInt64 != null) {
                detail.setLBFGNBPFNIH(field4UInt64);
            }
            if (field5Bool != null) {
                detail.setLEGKLKCNKJB(field5Bool);
            }
            if (field6Message != null) {
                detail.setMKODMDPGDHK(field6Message);
            }
            if (field7UInt64 != null) {
                detail.setJKBOBLJFKBP(field7UInt64);
            }
            if (field8UInt64 != null) {
                detail.setHPLGJAAJOGG(field8UInt64);
            }
        }
    }

    public static BeyondPlayerState onlineTeyvat(int uid) {
        return new BeyondPlayerState(uid, OnlineState.ONLINE, WorldType.TEYVAT, null);
    }

    public static BeyondPlayerState onlineBeyond(
            int uid,
            DetailState state,
            boolean inTeam,
            UnresolvedDetailFields unresolvedFields) {
        return new BeyondPlayerState(
                uid,
                OnlineState.ONLINE,
                WorldType.BEYOND,
                new OnlineDetail(state, inTeam, unresolvedFields));
    }

    public BeyondPlayerState withOnlineState(OnlineState state) {
        return new BeyondPlayerState(uid, state, worldType, onlineDetail);
    }

    public _BeyondPlayerInfo toProto() {
        var result =
                _BeyondPlayerInfo.newBuilder()
                        .setUid(uid)
                        .setOnlineStateValue(onlineState.wireValue)
                        .setWorldTypeValue(worldType.wireValue);

        if (onlineDetail != null) {
            var detail =
                    _BydPlayerDetailOnlineInfo.newBuilder()
                            .setJCEIDIDBFHMValue(onlineDetail.state.wireValue)
                            .setIsInTeam(onlineDetail.inTeam);
            onlineDetail.unresolvedFields.applyTo(detail);
            result.setBydPlayerDetailOnlineInfo(detail);
        }

        return result.build();
    }
}
