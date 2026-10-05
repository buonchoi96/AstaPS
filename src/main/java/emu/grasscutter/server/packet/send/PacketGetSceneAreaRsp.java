package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.WorldRegions;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetSceneAreaRspOuterClass.GetSceneAreaRsp;
import java.util.TreeSet;

public class PacketGetSceneAreaRsp extends BasePacket {
    public PacketGetSceneAreaRsp(Player player, int sceneId) {
        super(PacketOpcodes.GetSceneAreaRsp);

        this.buildHeader(0);

        // Older local builds added synthetic ids 1..1000. Filter only a wire copy against this
        // scene's table; serializing a response must not migrate or save player unlock state.
        // Without scene-table evidence, preserve the existing response values.
        var unlockedAreas = new TreeSet<>(player.getUnlockedSceneAreas(sceneId));
        var validAreaIds = WorldRegions.validAreaIds(sceneId);
        if (!validAreaIds.isEmpty()) unlockedAreas.retainAll(validAreaIds);

        GetSceneAreaRsp.Builder b =
                GetSceneAreaRsp.newBuilder()
                        .setSceneId(sceneId)
                        .addAllAreaIdList(unlockedAreas);

        // Match the known-working LunaGC 7.1 response shape.
        for (int cityId = 1; cityId <= 5; cityId++) {
            b.addCityInfoList(player.getSotsManager().getCityInfo(cityId).toProto());
        }

        this.setData(b.build());
    }
}
