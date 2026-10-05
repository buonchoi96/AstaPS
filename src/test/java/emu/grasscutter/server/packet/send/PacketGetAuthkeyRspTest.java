package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetAuthkeyRspOuterClass.GetAuthkeyRsp;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerGetAuthkeyReq;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PacketGetAuthkeyRspTest {
    @BeforeAll
    static void initializeConfiguration() {
        Grasscutter.getLogger();
    }

    @Test
    void errorResponsePreservesRequestSequenceWithoutInventingAKey() throws Exception {
        var session = new CapturingSession();
        byte[] header = PacketHead.newBuilder().setClientSequenceId(456).build().toByteArray();

        new HandlerGetAuthkeyReq().handle(session, header, new byte[0]);

        assertNotNull(session.sent);
        assertEquals(PacketOpcodes.GetAuthkeyRsp, session.sent.getOpcode());
        assertNotNull(session.sent.getHeader(), "response must carry the request correlation sequence");
        assertEquals(456, PacketHead.parseFrom(session.sent.getHeader()).getClientSequenceId());
        var response = GetAuthkeyRsp.parseFrom(session.sent.getData());
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, response.getRetcode());
        assertEquals("", response.getAuthkey());
        assertArrayEquals(new byte[] {0x30, 0x01}, session.sent.getData());
    }

    private static final class CapturingSession extends GameSession {
        private BasePacket sent;

        CapturingSession() { super(null); }

        @Override
        public void send(BasePacket packet) { sent = packet; }
    }
}
