package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EnterSceneDoneReqOuterClass.EnterSceneDoneReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.EnterSceneDoneReq)
public class HandlerEnterSceneDoneReq extends PacketHandler {
    /** 本次运行内已经给客户端同步过解锁通知的点（防止重复弹提示）。 */
    private static final java.util.Set<String> MIAO_UNLOCK_SYNCED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();


    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        EnterSceneDoneReq req = EnterSceneDoneReq.parseFrom(payload);

        var player = session.getPlayer();

        // Keep the critical 7.1 scene-entry completion path aligned with the known-working
        // LunaGC server. Do not inject AstaPS-only handbook/offering/statue/TPS packets before
        // EnterSceneDoneRsp; the client has not yet sent PostEnterSceneReq at this point.
        player.setSceneLoadState(SceneLoadState.LOADED);

        session.send(new PacketPlayerTimeNotify(player));

        player.getScene().spawnPlayer(player);
        player.getScene().showOtherEntities(player);

        session.send(new PacketWorldPlayerLocationNotify(player.getWorld()));
        session.send(new PacketScenePlayerLocationNotify(player.getScene()));
        session.send(new PacketWorldPlayerRTTNotify(player.getWorld()));

        var avatarEntity = player.getTeamManager().getCurrentAvatarEntity();
        float currentHpDebts =
                avatarEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (currentHpDebts > 0.0f) {
            avatarEntity
                    .getWorld()
                    .broadcastPacket(
                            new PacketEntityFightPropChangeReasonNotify(
                                    avatarEntity,
                                    FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                                    currentHpDebts,
                                    PropChangeReasonOuterClass.PropChangeReason
                                            .PropChangeReason_PROP_CHANGE_NONE,
                                    ChangeHpDebtsReason
                                            ._ChangeHpDebtsReason
                                            ._ChangeHpDebtsReason_CHANGE_HP_DEBTS_NONE));
        }

        player.getScene().loadNpcForPlayerEnter(player);

        var questGroupSuites =
                player.getQuestManager().getSceneGroupSuite(player.getSceneId());
        player.getScene().loadGroupForQuest(questGroupSuites);
        Grasscutter.getLogger()
                .trace(
                        "Loaded Scene {} Quest(s) Groupsuite(s): {}",
                        player.getSceneId(),
                        questGroupSuites);
        session.send(new PacketGroupSuiteNotify(questGroupSuites));

        // Do not run DailyTask/Combine/DomainHandbook/ArtifactTransmuter/Statue/TPS extensions
        // until PostEnterSceneReq. They are not part of LunaGC's critical EnterSceneDone path.
        player.resetSendPlayerLocTime();

        Grasscutter.getLogger()
                .debug(
                        "Luna-compatible EnterSceneDone uid={} scene={} entities={} -> sending Rsp before AstaPS extensions",
                        player.getUid(),
                        player.getSceneId(),
                        player.getScene().getEntities().size());

        session.send(new PacketEnterSceneDoneRsp(player));
    }

}