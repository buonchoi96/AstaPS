package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.beyond.BeyondPlayerStateService;
import emu.grasscutter.game.beyond.BeyondPresenceProtocol71;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.util.function.IntPredicate;

public final class PacketGetBeyondPlayerInfoRsp extends BasePacket {
    public PacketGetBeyondPlayerInfoRsp(
            byte[] header, byte[] payload, BeyondPlayerStateService states, IntPredicate online)
            throws Exception {
        super(PacketOpcodes._GetBeyondPlayerInfoRsp, PacketEmptyRsp.clientSequence(header));
        setData(BeyondPresenceProtocol71.response(payload, states, online));
    }
}
