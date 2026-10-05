package emu.grasscutter.game.beyond;

import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.net.proto.BeyondPlayerInfo;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Recovered 7.1 presence wrappers, using the existing generated presence descriptor.
 * Source: kitkat-multiverse/genshin-protocol d9d67f94da0402f53acefbd59944aa052ee78ca6,
 * 7.1.0/Deobfuscated.proto. Dynamic descriptors avoid replacing the unrelated generated dump.
 */
public final class BeyondPresenceProtocol71 {
    public static final String SOURCE_REVISION = "d9d67f94da0402f53acefbd59944aa052ee78ca6";
    private static final Descriptors.FileDescriptor FILE = descriptor();
    private static final Descriptors.Descriptor REQUEST = FILE.findMessageTypeByName("_GetBeyondPlayerInfoReq");
    private static final Descriptors.Descriptor RESPONSE = FILE.findMessageTypeByName("_GetBeyondPlayerInfoRsp");

    private BeyondPresenceProtocol71() {}

    public static DynamicMessage parse(String name, byte[] payload) throws InvalidProtocolBufferException {
        var type = switch (name) {
            case "_GetBeyondPlayerInfoReq" -> REQUEST;
            case "_GetBeyondPlayerInfoRsp" -> RESPONSE;
            default -> null;
        };
        return type == null ? null : DynamicMessage.parseFrom(type, payload);
    }

    public static byte[] response(byte[] payload, BeyondPlayerStateService states, IntPredicate online)
            throws InvalidProtocolBufferException {
        var request = DynamicMessage.parseFrom(REQUEST, payload);
        var result = DynamicMessage.newBuilder(RESPONSE);
        result.setField(RESPONSE.findFieldByNumber(3), request.getField(REQUEST.findFieldByNumber(10)));
        @SuppressWarnings("unchecked")
        var uids = (List<Integer>) request.getField(REQUEST.findFieldByNumber(14));
        for (int uid : uids) {
            var state = online.test(uid)
                    ? java.util.Optional.of(states.resolveForWorldPlayer(uid))
                    : states.find(uid).map(value -> value.withOnlineState(BeyondPlayerState.OnlineState.OFFLINE));
            state.ifPresent(value -> result.addRepeatedField(RESPONSE.findFieldByNumber(2), value.toProto()));
        }
        return result.build().toByteArray();
    }

    private static FieldDescriptorProto field(String name, int number, FieldDescriptorProto.Type type,
                                              boolean repeated, String messageType) {
        var field = FieldDescriptorProto.newBuilder().setName(name).setNumber(number).setType(type)
                .setLabel(repeated ? FieldDescriptorProto.Label.LABEL_REPEATED
                        : FieldDescriptorProto.Label.LABEL_OPTIONAL);
        if (messageType != null) field.setTypeName(messageType);
        return field.build();
    }

    private static Descriptors.FileDescriptor descriptor() {
        var presence = BeyondPlayerInfo.getDescriptor();
        var request = DescriptorProto.newBuilder().setName("_GetBeyondPlayerInfoReq")
                .addField(field("player_uid_list", 14, FieldDescriptorProto.Type.TYPE_UINT32, true, null))
                .addField(field("reason", 10, FieldDescriptorProto.Type.TYPE_UINT32, false, null));
        var response = DescriptorProto.newBuilder().setName("_GetBeyondPlayerInfoRsp")
                .addField(field("_beyond_player_info", 2, FieldDescriptorProto.Type.TYPE_MESSAGE, true,
                        "." + presence.findMessageTypeByName("_BeyondPlayerInfo").getFullName()))
                .addField(field("reason", 3, FieldDescriptorProto.Type.TYPE_UINT32, false, null))
                .addField(field("retcode", 15, FieldDescriptorProto.Type.TYPE_INT32, false, null));
        var file = FileDescriptorProto.newBuilder().setName("recovered_beyond_presence_7_1.proto")
                .setSyntax("proto3").addDependency(presence.getName())
                .addMessageType(request).addMessageType(response).build();
        try {
            return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[] {presence});
        } catch (Descriptors.DescriptorValidationException invalidSchema) {
            throw new ExceptionInInitializerError(invalidSchema);
        }
    }
}
