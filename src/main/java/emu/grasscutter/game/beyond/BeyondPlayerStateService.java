package emu.grasscutter.game.beyond;

import emu.grasscutter.net.proto.WorldPlayerInfoNotifyOuterClass.WorldPlayerInfoNotify;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory Beyond presence registry.
 *
 * <p>It deliberately owns no Hall or editor lifecycle. Callers record state only when an
 * implemented server action or observed protocol event supplies that transition.
 */
public final class BeyondPlayerStateService {
    private static final BeyondPlayerStateService SHARED = new BeyondPlayerStateService();

    private final ConcurrentMap<Integer, BeyondPlayerState> states = new ConcurrentHashMap<>();

    public static BeyondPlayerStateService shared() {
        return SHARED;
    }

    public void put(BeyondPlayerState state) {
        states.put(state.uid(), state);
    }

    public Optional<BeyondPlayerState> find(int uid) {
        return Optional.ofNullable(states.get(uid));
    }

    /**
     * Resolves the presence emitted for a player already known to be in a live {@code World}.
     *
     * <p>World membership proves online presence. In the absence of any explicit Beyond transition,
     * the current server world is represented as Teyvat and no optional online-detail message is
     * fabricated.
     */
    public BeyondPlayerState resolveForWorldPlayer(int uid) {
        var state = states.get(uid);
        if (state == null) {
            return BeyondPlayerState.onlineTeyvat(uid);
        }
        return state.withOnlineState(BeyondPlayerState.OnlineState.ONLINE);
    }

    public void appendWorldPlayerPresence(WorldPlayerInfoNotify.Builder notify, int uid) {
        notify.addBeyondPlayerInfoList(resolveForWorldPlayer(uid).toProto());
    }
}
