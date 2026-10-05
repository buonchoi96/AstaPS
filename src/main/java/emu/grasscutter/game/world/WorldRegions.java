package emu.grasscutter.game.world;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.scene.LimitRegionData;
import emu.grasscutter.net.proto.LimitedRegionInfo._LimitedRegionInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Helpers for the 7.1 limited-region scene payloads. */
public final class WorldRegions {
    private WorldRegions() {}

    private static Set<Integer> areaIds;
    private static Int2ObjectMap<Set<Integer>> areaIdsByScene;
    private static Int2ObjectMap<List<Integer>> regionsByScene;

    public static synchronized Set<Integer> allAreaIds() {
        if (areaIds != null) return areaIds;

        var ids = new TreeSet<Integer>();
        GameData.getWorldAreaDataMap().values().forEach(area -> ids.add(area.getParentArea()));
        ids.remove(0);
        areaIds = ids;
        return ids;
    }

    /** Wire area ids that actually exist in WorldAreaConfigData for one scene. */
    public static synchronized Set<Integer> validAreaIds(int sceneId) {
        if (areaIdsByScene == null) {
            areaIdsByScene = new Int2ObjectOpenHashMap<>();
            GameData.getWorldAreaDataMap().values().forEach(
                    area -> {
                        var ids =
                                areaIdsByScene.computeIfAbsent(
                                        area.getSceneId(), ignored -> new TreeSet<Integer>());
                        if (area.getParentArea() > 0) ids.add(area.getParentArea());
                        if (area.getChildArea() > 0) ids.add(area.getChildArea());
                    });
        }

        return areaIdsByScene.getOrDefault(sceneId, Set.of());
    }

    public static synchronized List<Integer> openRegionIds(int sceneId) {
        if (regionsByScene == null) {
            regionsByScene = new Int2ObjectOpenHashMap<>();
            GameData.getLimitRegionDataMap().values().stream()
                    .filter(LimitRegionData::isBigWorld)
                    .filter(LimitRegionData::isProgressionGated)
                    .sorted(
                            Comparator.comparingInt(LimitRegionData::getOrder)
                                    .thenComparingInt(LimitRegionData::getId))
                    .forEach(
                            region ->
                                    regionsByScene
                                            .computeIfAbsent(
                                                    region.getSceneId(),
                                                    ignored -> new ArrayList<Integer>())
                                            .add(region.getId()));
        }

        return regionsByScene.getOrDefault(sceneId, List.of());
    }

    public static _LimitedRegionInfo openRegions(int sceneId) {
        return _LimitedRegionInfo.newBuilder()
                .addAllLimitedRegionList(openRegionIds(sceneId))
                .build();
    }

    public static _LimitedRegionInfo unrestricted() {
        return _LimitedRegionInfo.newBuilder()
                .addAllLimitedRegionList(allAreaIds())
                .build();
    }
}
