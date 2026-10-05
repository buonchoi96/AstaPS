package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.world.WorldAreaData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GetSceneAreaRspOuterClass.GetSceneAreaRsp;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

final class PacketGetSceneAreaRspCompatibilityTest {
    @BeforeAll
    static void initializeConfigurationBeforeConstructingPlayerManagers() {
        // Configuration reads Grasscutter.config; entering through a manager creates a static
        // initialization cycle. This loads config only, without starting a server or database.
        Grasscutter.getLogger();
    }

    @Test
    void filtersWireAreasWithoutChangingOrSavingPlayerUnlocks() throws Exception {
        var area = new Gson().fromJson(
                "{\"sceneID\":991,\"areaID1\":71,\"areaID2\":72}", WorldAreaData.class);
        var previous = GameData.getWorldAreaDataMap().put(area.getId(), area);
        try {
            var player = new NoSavePlayer();
            player.getUnlockedSceneAreas(991).addAll(Set.of(71, 72, 999));
            var packet = new PacketGetSceneAreaRsp(player, 991);
            var response = GetSceneAreaRsp.parseFrom(packet.getData());

            assertEquals(991, response.getSceneId());
            assertEquals(Set.of(71, 72), Set.copyOf(response.getAreaIdListList()));
            assertEquals(Set.of(71, 72, 999), player.getUnlockedSceneAreas(991));
            assertEquals(List.of(1, 2, 3, 4, 5), response.getCityInfoListList().stream()
                    .map(city -> city.getCityId()).toList());
        } finally {
            if (previous == null) GameData.getWorldAreaDataMap().remove(area.getId());
            else GameData.getWorldAreaDataMap().put(area.getId(), previous);
        }
    }

    @Test
    void keepsExistingAreasWhenNoSceneTableEvidenceIsAvailable() throws Exception {
        var player = new NoSavePlayer();
        player.getUnlockedSceneAreas(992).add(73);
        var response = GetSceneAreaRsp.parseFrom(new PacketGetSceneAreaRsp(player, 992).getData());

        assertEquals(List.of(73), response.getAreaIdListList());
        assertEquals(Set.of(73), player.getUnlockedSceneAreas(992));
    }

    @SuppressWarnings("deprecation")
    private static final class NoSavePlayer extends Player {
        @Override
        public void save() {
            fail("Building a scene-area response must not persist player data");
        }
    }
}
