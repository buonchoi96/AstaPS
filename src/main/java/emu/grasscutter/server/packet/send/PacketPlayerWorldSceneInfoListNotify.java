package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MapLayerInfo;
import emu.grasscutter.net.proto.PlayerWorldSceneInfoListNotifyOuterClass.PlayerWorldSceneInfoListNotify;
import emu.grasscutter.net.proto.PlayerWorldSceneInfoOuterClass.PlayerWorldSceneInfo;
import java.util.Map;

public class PacketPlayerWorldSceneInfoListNotify extends BasePacket {

    public PacketPlayerWorldSceneInfoListNotify(Player player) {
        super(PacketOpcodes.PlayerWorldSceneInfoListNotify); // Rename opcode later

        var sceneTags = player.getSceneTags();

        PlayerWorldSceneInfoListNotify.Builder proto =
                PlayerWorldSceneInfoListNotify.newBuilder()
                        .addInfoList(
                                PlayerWorldSceneInfo.newBuilder().setSceneId(1).setIsLocked(false).build());

        // Iterate over all scenes
        for (int scene : GameData.getSceneDataMap().keySet()) {
            var worldInfoBuilder =
                    PlayerWorldSceneInfo.newBuilder()
                            .setSceneId(scene)
                            .setIsLocked(false)
                            .setLimitedRegionInfo(
                                    emu.grasscutter.game.world.WorldRegions.openRegions(scene));

            /** Add scene-specific data */

            // Scenetags
            if (sceneTags.keySet().contains(scene)) {
                worldInfoBuilder.addAllSceneTagIdList(
                        sceneTags.entrySet().stream()
                                .filter(e -> e.getKey().equals(scene))
                                .map(Map.Entry::getValue)
                                .toList()
                                .get(0));
            }

            // Map layer information (Big world)
            if (scene == 3) {
                worldInfoBuilder.setMapLayerInfo(
                        MapLayerInfo._MapLayerInfo.newBuilder()
                                // The generated 7.1 Java names are reversed. The corrected 7.1
                                // source (kitkat-multiverse d9d67f9) puts layer IDs in field 2
                                // and group IDs in field 7. Keep the generated sources intact.
                                .addAllUnlockMapLayerGroupList(
                                        GameData.getMapLayerDataMap().keySet())
                                .addAllUnlockMapLayerList(
                                        GameData.getMapLayerGroupDataMap()
                                                .keySet())
                                .build());
            }

            proto.addInfoList(worldInfoBuilder.build());
        }

        // unlocked_area_id_list has never been populated, in 6.7 either - the field existed and was
        // simply left empty. It is the world-area counterpart to SceneAreaUnlockNotify, and an empty
        // list is what walls a region off: teleporting in gets you turned straight back around.
        //
        // The ids are the small parent-area numbers (WorldAreaConfigData.areaID1, 1..1000), the same
        // space scene points use. NOT WorldAreaData.getId(), which is a Grasscutter-internal
        // composite of (areaID2 << 16) + areaID1 and runs past 2^32, so it cannot be a wire value.
        proto.addAllIIFJABJJJBF(emu.grasscutter.game.world.WorldRegions.allAreaIds());

        this.setData(proto);
    }
}
