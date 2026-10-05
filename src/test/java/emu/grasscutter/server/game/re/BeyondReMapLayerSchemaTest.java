package emu.grasscutter.server.game.re;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.net.proto.MapLayerInfo;
import emu.grasscutter.net.proto.SceneDataNotifyOuterClass.SceneDataNotify;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BeyondReMapLayerSchemaTest {
    @TempDir Path tempDir;

    @Test
    void logsCorrectedLayerMeaningByWireNumberInsteadOfReversedGeneratedName() throws Exception {
        var wire = UnknownFieldSet.newBuilder()
                .addField(2, UnknownFieldSet.Field.newBuilder().addVarint(77001).build())
                .addField(7, UnknownFieldSet.Field.newBuilder().addVarint(3).build()).build();
        var payload = SceneDataNotify.newBuilder().setSceneId(3)
                .setMapLayerInfo(MapLayerInfo._MapLayerInfo.parseFrom(wire.toByteArray()))
                .build().toByteArray();
        var options = new ConfigContainer.BeyondReRecorderOptions();
        options.enabled = true;
        options.captureAllPackets = true;
        BeyondReRecorder.record(options, tempDir, new BeyondReRecorder.SessionInfo("map-layer", 1, null),
                BeyondReRecorder.Direction.S2C, 29381, "SceneDataNotify", new byte[0], payload);
        var directory = tempDir.resolve("session-map-layer");
        var record = JsonParser.parseString(Files.readString(directory.resolve("packets.jsonl"))).getAsJsonObject();
        var mapLayer = record.getAsJsonObject("decodedFields").getAsJsonObject("_map_layer_info");
        assertEquals(77001, mapLayer.getAsJsonArray("_unlock_map_layer_list").get(0).getAsInt());
        assertEquals(3, mapLayer.getAsJsonArray("_unlock_map_layer_group_list").get(0).getAsInt());
        assertArrayEquals(payload, Files.readAllBytes(directory.resolve(record.get("rawPath").getAsString())));
    }
}
