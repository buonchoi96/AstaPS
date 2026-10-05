package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import org.junit.jupiter.api.Test;

class PacketEmptyRspTest {
    @Test
    void preservesRequestClientSequenceForBeyondPlayerInfoRsp() throws Exception {
        byte[] requestHeader =
                PacketHead.newBuilder()
                        .setClientSequenceId(314)
                        .setSentMs(1_234_567_890L)
                        .build()
                        .toByteArray();

        var rsp = new PacketEmptyRsp(PacketOpcodes._GetBeyondPlayerInfoRsp, requestHeader);

        assertEquals(PacketOpcodes._GetBeyondPlayerInfoRsp, rsp.getOpcode());
        assertEquals(314, PacketHead.parseFrom(rsp.getHeader()).getClientSequenceId());
        assertEquals(0, rsp.getData() == null ? 0 : rsp.getData().length);
    }
}
