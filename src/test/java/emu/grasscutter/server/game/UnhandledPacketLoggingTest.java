package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.net.packet.PacketHandler;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UnhandledPacketLoggingTest {
    @BeforeAll
    static void initializeConfiguration() {
        Grasscutter.getLogger();
    }

    @Test
    void debugMetadataDoesNotDumpPayloadUnlessExplicitlySelected() {
        var handler = new GameServerPacketHandler(PacketHandler.class);
        var session = new GameSession(null);
        byte[] payload = "private-unhandled-content".getBytes(StandardCharsets.UTF_8);
        var logger = Grasscutter.getLogger();
        Level previousLevel = logger.getLevel();
        boolean previousPayloadOption = Configuration.GAME_INFO.isShowPacketPayload;
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        logger.setLevel(Level.DEBUG);
        try {
            Configuration.GAME_INFO.isShowPacketPayload = false;
            handler.handle(session, 65000, new byte[0], payload);
            String safe = unhandledLine(capture);
            assertTrue(safe.contains("opcode 65000"));
            assertTrue(safe.contains("len=" + payload.length));
            assertFalse(safe.contains(HexFormat.of().formatHex(payload)),
                    "normal DEBUG metadata must not expose raw payload contents");

            capture.list.clear();
            Configuration.GAME_INFO.isShowPacketPayload = true;
            handler.handle(session, 65000, new byte[0], payload);
            assertTrue(unhandledLine(capture).contains(HexFormat.of().formatHex(payload)));
        } finally {
            Configuration.GAME_INFO.isShowPacketPayload = previousPayloadOption;
            logger.setLevel(previousLevel);
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    private String unhandledLine(ListAppender<ILoggingEvent> capture) {
        return capture.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.startsWith("Unhandled packet opcode 65000 "))
                .findFirst().orElseThrow();
    }
}
