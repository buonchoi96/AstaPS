package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PacketPlayerWorldSceneInfoListNotifyCompatibilityTest {
    @Test
    void usesSceneSpecificLimitedRegionsLikeLunaGc71() throws IOException {
        var source =
                Files.readString(
                        Path.of(
                                "src",
                                "main",
                                "java",
                                "emu",
                                "grasscutter",
                                "server",
                                "packet",
                                "send",
                                "PacketPlayerWorldSceneInfoListNotify.java"));

        assertTrue(
                source.contains("WorldRegions.openRegions(scene)"),
                "7.1 world-scene info must use scene-specific limited-region IDs");
        assertFalse(
                source.contains("WorldRegions.unrestricted()"),
                "world-area IDs must not be written into LimitedRegionInfo");
    }
}
