package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.UnknownFieldSet;
import java.util.ArrayList;
import java.util.List;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.PlayerWorldSceneInfoListNotifyOuterClass.PlayerWorldSceneInfoListNotify;
import org.junit.jupiter.api.Test;

final class MapLayerWire71Test {
    @Test
    void sendsLayerIdsInField2AndGroupIdsInField7() throws Exception {
        // Independent 7.1 wire contract: kitkat-multiverse d9d67f9, _MapLayerInfo.
        // Group 3 is valid, but is absent from the layer table. Swapping these lists makes
        // the client look up a group ID as a layer ID.
        Grasscutter.getLogger();
        var scenes = GameData.getSceneDataMap();
        var layers = GameData.getMapLayerDataMap();
        var groups = GameData.getMapLayerGroupDataMap();
        var oldScene = scenes.put(3, new Gson().fromJson("{\"id\":3}", SceneData.class));
        var oldLayer = layers.put(77001, null);
        var oldGroup = groups.put(3, null);
        try {
            var response = PlayerWorldSceneInfoListNotify.parseFrom(
                    new PacketPlayerWorldSceneInfoListNotify(new NoSavePlayer()).getData());
            var scene = response.getInfoListList().stream()
                    .filter(info -> info.getSceneId() == 3).findFirst().orElseThrow();
            var wire = UnknownFieldSet.parseFrom(scene.getMapLayerInfo().toByteArray());
            assertTrue(values(wire, 2).contains(77001L),
                    "field 2 must contain map-layer IDs");
            assertFalse(values(wire, 2).contains(3L),
                    "group ID 3 must not be sent as a map-layer ID");
            assertTrue(values(wire, 7).contains(3L),
                    "field 7 must contain map-layer-group IDs");
        } finally {
            if (oldScene == null) scenes.remove(3); else scenes.put(3, oldScene);
            if (oldLayer == null) layers.remove(77001); else layers.put(77001, oldLayer);
            if (oldGroup == null) groups.remove(3); else groups.put(3, oldGroup);
        }
    }

    private static List<Long> values(UnknownFieldSet wire, int field) throws Exception {
        var values = new ArrayList<>(wire.getField(field).getVarintList());
        for (var bytes : wire.getField(field).getLengthDelimitedList()) {
            var input = bytes.newCodedInput();
            while (!input.isAtEnd()) values.add(input.readUInt64());
        }
        return values;
    }

    @SuppressWarnings("deprecation")
    private static final class NoSavePlayer extends Player {
        @Override
        public void save() {
            fail("Constructing scene information must not persist player data");
        }
    }
}
