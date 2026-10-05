package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.game.ability.EscoffierSkillCookHelper;
import emu.grasscutter.game.player.EntryNotice;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PostEnterSceneReqOuterClass.PostEnterSceneReq;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketCutsceneBeginNotify;
import emu.grasscutter.server.packet.send.PacketGetPlayerFriendListRsp;
import emu.grasscutter.server.packet.send.PacketPostEnterSceneRsp;

@Opcodes(PacketOpcodes.PostEnterSceneReq)
public class HandlerPostEnterSceneReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PostEnterSceneReq req = PostEnterSceneReq.parseFrom(payload);

        var player = session.getPlayer();
        var scene = player.getScene();
        var questManager = player.getQuestManager();
        boolean freshPlayerBootstrap = BornIntroGate.isFreshPlayerBootstrap(session);

        // Native-selection and automatic births converge here. Let PostEnterSceneRsp reach the
        // client before Quest 351 starts so its actors see a ready playable scene.
        if (freshPlayerBootstrap) {
            session.send(new PacketPostEnterSceneRsp(player));
            BornIntroGate.finishOnSceneReady(session);
        }

        switch (scene.getSceneType()) {
            case SCENE_ROOM ->
                    questManager.queueEvent(QuestContent.QUEST_CONTENT_ENTER_ROOM, scene.getId(), 0);
            case SCENE_WORLD -> {
                questManager.queueEvent(QuestContent.QUEST_CONTENT_ENTER_MY_WORLD, scene.getId());
                questManager.queueEvent(QuestContent.QUEST_CONTENT_ENTER_MY_WORLD_SCENE, scene.getId());
            }
            case SCENE_DUNGEON -> {
                var dungeonManager = scene.getDungeonManager();
                if (dungeonManager != null) dungeonManager.startDungeon();
            }
        }
        questManager.queueEvent(QuestContent.QUEST_CONTENT_LEAVE_SCENE, scene.getPrevScene());

        if (!freshPlayerBootstrap) session.send(new PacketPostEnterSceneRsp(player));

        // At this point the client has explicitly completed the 7.1 scene-entry handshake.
        // AstaPS-only world/progression conveniences that previously ran before EnterSceneDoneRsp
        // are safe to resume here.
        runDeferredAstaSceneSync(player);

        EscoffierSkillCookHelper.syncToClient(player);
        EntryNotice.sendOnce(player);
        session.send(new PacketGetPlayerFriendListRsp(player));
        session.getServer().getChatManager().ensureServerConversation(player);

        // Fresh 7.1 starts the opening from AQ351/35104. Do not add the independent legacy
        // first-login cutscene on top of that bootstrap.
        if (!freshPlayerBootstrap) this.playOpeningCutscene(player);
    }

    private void runDeferredAstaSceneSync(emu.grasscutter.game.player.Player player) {
        // Daily commissions.
        try {
            var dailyTaskManager = player.getDailyTaskManager();
            if (dailyTaskManager != null) {
                dailyTaskManager.onPlayerLogin();
                dailyTaskManager.loadActiveGroups(player.getScene());
                dailyTaskManager.syncAll();
            }
        } catch (Throwable t) {
            emu.grasscutter.Grasscutter.getLogger()
                    .warn("Deferred daily-task scene sync failed uid={}: {}", player.getUid(), t.toString());
        }

        // Combine convenience unlocks.
        try {
            player.getServer().getCombineSystem().onPlayerLogin(player);
        } catch (Throwable t) {
            emu.grasscutter.Grasscutter.getLogger()
                    .warn("Deferred combine login sync failed uid={}: {}", player.getUid(), t.toString());
        }

        // Domain handbook/map convenience sync.
        try {
            emu.grasscutter.game.player.DomainHandbookHelper.onEnterScene(
                    player, player.getSceneId());
        } catch (Throwable t) {
            emu.grasscutter.Grasscutter.getLogger()
                    .warn("Deferred domain handbook sync failed uid={}: {}", player.getUid(), t.toString());
        }

        // Artifact transmuter offer.
        try {
            emu.grasscutter.game.systems.ArtifactTransmuterSystem.sendLoginNotifyOnce(player);
        } catch (Throwable t) {
            emu.grasscutter.Grasscutter.getLogger()
                    .warn("Deferred ArtifactTransmuter sync failed uid={}: {}", player.getUid(), t.toString());
        }

        // Refresh only an already-unlocked nearest statue talk gate.
        try {
            int sceneId = player.getSceneId();
            var me = player.getPosition();
            Object[] nearest = null;
            for (var e : emu.grasscutter.data.GameData.getScenePointEntryMap().values()) {
                if (e == null || e.getPointData() == null || e.getSceneId() != sceneId) continue;
                if (!emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(e.getPointData())) continue;
                int pointId = e.getPointData().getId();
                if (!player.getUnlockedScenePoints(sceneId).contains(pointId)) continue;

                var pd = e.getPointData();
                var pos = pd.getTranPos() != null ? pd.getTranPos() : pd.getPos();
                double distance = 999999.0;
                if (me != null && pos != null) distance = me.computeDistance(pos);

                if (nearest == null || distance < (Double) nearest[1]) {
                    nearest = new Object[] {pointId, distance};
                }
            }
            if (nearest != null) {
                player.getProgressManager()
                        .refreshStatueTalkGate(sceneId, (Integer) nearest[0]);
            }
        } catch (Throwable t) {
            emu.grasscutter.Grasscutter.getLogger()
                    .warn("Deferred statue scene sync failed uid={}: {}", player.getUid(), t.toString());
        }

        // TPS ammunition reserve packet(s), if relevant.
        try {
            emu.grasscutter.game.tps.TpsWeaponSystem.sendSceneAmmunition(player);
        } catch (Throwable t) {
            emu.grasscutter.Grasscutter.getLogger()
                    .warn("Deferred TPS ammunition sync failed uid={}: {}", player.getUid(), t.toString());
        }
    }

    /** Fired here rather than at login: a cutscene sent before the scene is up is discarded. */
    private void playOpeningCutscene(emu.grasscutter.game.player.Player player) {
        int cutscene = GAME_OPTIONS.firstLoginCutscene;
        if (GAME_OPTIONS.disableCutscenes || cutscene <= 0 || player.isPlayedFirstLoginCutscene()) return;

        player.setPlayedFirstLoginCutscene(true);
        player.save();
        player.sendPacket(new PacketCutsceneBeginNotify(cutscene));
    }
}
