package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEmptyRsp;

/**
 * Handles the 7.1 Beyond player-info request with the verified empty response.
 *
 * <p>Evidence: LunaGC 7.1 revision 811b224db150982be37c0619e1506d980443cb9b, handler introduced
 * by ac7144a0ce6d8ef855fbe630aa92c07b903f47da. The reference sends an empty response body while
 * preserving the request client sequence. No unknown payload fields are inferred here.
 */
@Opcodes(PacketOpcodes._GetBeyondPlayerInfoReq)
public class HandlerGetBeyondPlayerInfoReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send(new PacketEmptyRsp(PacketOpcodes._GetBeyondPlayerInfoRsp, header));
    }
}
