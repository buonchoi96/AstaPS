package emu.grasscutter.scripts.data;

import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.scripts.*;
import emu.grasscutter.utils.FileUtils;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import javax.script.*;
import lombok.*;

@ToString
@Setter
public class SceneMeta {

    public SceneConfig config;
    public Map<Integer, SceneBlock> blocks;

    public Bindings context;

    public RTree<SceneBlock, Geometry> sceneBlockIndex;

    public static SceneMeta of(int sceneId) {
        return new SceneMeta().load(sceneId);
    }

    public SceneMeta load(int sceneId) {
        // Get compiled script if cached
        CompiledScript cs = ScriptLoader.getScript("Scene/" + sceneId + "/scene" + sceneId + ".lua");

        if (cs == null) {
            Grasscutter.getLogger().warn("No script found for scene " + sceneId);
            return null;
        }

        // Create bindings
        this.context = ScriptLoader.getEngine().createBindings();

        // Eval script
        try {
            ScriptLoader.eval(cs, this.context);

            this.config =
                    ScriptLoader.getSerializer()
                            .toObject(SceneConfig.class, this.context.get("scene_config"));

            // TODO optimize later
            // Create blocks
            List<Integer> blockIds =
                    ScriptLoader.getSerializer().toList(Integer.class, this.context.get("blocks"));
            List<SceneBlock> blocks =
                    ScriptLoader.getSerializer().toList(SceneBlock.class, this.context.get("block_rects"));

            // Some 7.1 resource dumps advertise block ids in sceneX.lua without shipping
            // the corresponding sceneX_block<ID>.lua. LunaGC's known-good 7.1 resources are
            // internally consistent (scene 3: 64 declared, 0 missing), while the AstaPS 7.1
            // resource set currently declares several missing scene-3 blocks. Keeping those
            // phantom blocks in the spatial index makes the streamer repeatedly walk/load entries
            // that can never produce groups while the client is still entering the scene.
            //
            // Filter declared-but-missing blocks in lockstep with block_rects. This cannot remove
            // playable content: a block with no script cannot spawn anything in the first place.
            List<SceneBlock> usableBlocks = new ArrayList<>();
            List<Integer> missingBlocks = new ArrayList<>();

            int count = Math.min(blocks.size(), blockIds.size());
            for (int i = 0; i < count; i++) {
                SceneBlock block = blocks.get(i);
                int blockId = blockIds.get(i);
                block.id = blockId;

                var scriptPath =
                        FileUtils.getScriptPath(
                                "Scene/" + sceneId + "/scene" + sceneId + "_block" + blockId + ".lua");
                if (!Files.isRegularFile(scriptPath)) {
                    missingBlocks.add(blockId);
                    continue;
                }

                usableBlocks.add(block);
            }

            if (!missingBlocks.isEmpty()) {
                Grasscutter.getLogger()
                        .warn(
                                "Scene {} metadata declared {} block(s) with no script; excluding them from the streaming index: {}",
                                sceneId,
                                missingBlocks.size(),
                                missingBlocks);
            }

            this.blocks =
                    usableBlocks.stream()
                            .collect(Collectors.toMap(b -> b.id, b -> b, (a, b) -> a));
            this.sceneBlockIndex =
                    SceneIndexManager.buildIndex(2, usableBlocks, SceneBlock::toRectangle);

        } catch (ScriptException exception) {
            Grasscutter.getLogger().error("An error occurred while running a script.", exception);
            return null;
        }
        Grasscutter.getLogger().debug("Successfully loaded metadata in scene {}.", sceneId);
        return this;
    }
}
