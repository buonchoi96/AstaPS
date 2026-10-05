package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetAuthkeyRsp;

@Opcodes(PacketOpcodes.GetAuthkeyReq)
public class HandlerGetAuthkeyReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var response = new PacketGetAuthkeyRsp();
        if (header != null) {
            try {
                response.buildHeader(PacketHead.parseFrom(header).getClientSequenceId());
            } catch (InvalidProtocolBufferException ignored) {
                // Preserve the existing error response even if correlation metadata is malformed.
            }
        }
        session.send(response);
    }
}
