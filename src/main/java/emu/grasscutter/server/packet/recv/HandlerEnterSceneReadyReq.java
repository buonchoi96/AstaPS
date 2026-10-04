package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EnterSceneReadyReqOuterClass.EnterSceneReadyReq;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.EnterSceneReadyReq)
public class HandlerEnterSceneReadyReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        var player = session.getPlayer();

        // Fresh 7.1 can ask for scene-ready while the cold Player.onLogin tail is still running on
        // the worker pool. Resume this response as soon as login initialization completes.
        if (BornIntroGate.deferSceneReadyUntilLoginComplete(session)) {
            return;
        }

        int requestToken = 0;
        try {
            requestToken = EnterSceneReadyReq.parseFrom(payload).getEnterSceneToken();
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn(
                            "EnterSceneReadyReq decode failed uid={} payloadLen={}",
                            player.getUid(),
                            payload == null ? 0 : payload.length,
                            t);
        }

        var world = player.getWorld();
        var host = world == null ? null : world.getHost();
        Grasscutter.getLogger()
                .debug(
                        "EnterSceneReady handshake uid={} requestToken={} playerToken={} scene={} peer={} hostPeer={} pos={} loadState={} payloadLen={}",
                        player.getUid(),
                        requestToken,
                        player.getEnterSceneToken(),
                        player.getSceneId(),
                        player.getPeerId(),
                        host == null ? -1 : host.getPeerId(),
                        player.getPosition(),
                        player.getSceneLoadState(),
                        payload == null ? 0 : payload.length);

        if (requestToken != 0 && requestToken != player.getEnterSceneToken()) {
            Grasscutter.getLogger()
                    .warn(
                            "EnterSceneReady token mismatch uid={} requestToken={} playerToken={}; keeping server token until 7.1 request transform is verified",
                            player.getUid(),
                            requestToken,
                            player.getEnterSceneToken());
        }

        session.send(new PacketEnterScenePeerNotify(player));
        session.send(new PacketEnterSceneReadyRsp(player));
    }
}
