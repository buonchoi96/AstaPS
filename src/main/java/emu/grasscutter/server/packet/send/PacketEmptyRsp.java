package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;

/**
 * Empty proto3 response that preserves the request client sequence.
 *
 * <p>This mirrors the 7.1 LunaGC response primitive used for protocol paths whose verified response
 * body is empty. It does not infer any protobuf field numbers.
 */
public class PacketEmptyRsp extends BasePacket {
    public PacketEmptyRsp(int opcode, byte[] requestHeader) {
        super(opcode, clientSequence(requestHeader));
    }

    private static int clientSequence(byte[] requestHeader) {
        if (requestHeader == null || requestHeader.length == 0) return 0;
        try {
            return PacketHead.parseFrom(requestHeader).getClientSequenceId();
        } catch (Exception ignored) {
            return 0;
        }
    }
}
