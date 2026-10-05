package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.game.beyond.BeyondPlayerStateService;
import emu.grasscutter.server.packet.send.PacketGetBeyondPlayerInfoRsp;

/**
 * Answers requested Beyond presence using the recovered 7.1 request/response schema.
 *
 * <p>See BeyondPresenceProtocol71 for pinned schema evidence. Online membership comes from this
 * server; unknown offline UIDs are omitted rather than assigned fabricated presence.
 */
@Opcodes(PacketOpcodes._GetBeyondPlayerInfoReq)
public class HandlerGetBeyondPlayerInfoReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send(new PacketGetBeyondPlayerInfoRsp(header, payload,
                BeyondPlayerStateService.shared(), uid -> session.getServer().getPlayerByUid(uid) != null));
    }
}
