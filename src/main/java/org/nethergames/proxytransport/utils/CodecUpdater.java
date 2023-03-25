package org.nethergames.proxytransport.utils;

import dev.waterdog.waterdogpe.network.protocol.updaters.ProtocolCodecUpdater;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v388.serializer.TickSyncSerializer_v388;
import org.cloudburstmc.protocol.bedrock.packet.TickSyncPacket;

public class CodecUpdater implements ProtocolCodecUpdater {

    @Override
    public BedrockCodec.Builder updateCodec(BedrockCodec.Builder builder, BedrockCodec baseCodec) {
        builder.registerPacket(TickSyncPacket::new, TickSyncSerializer_v388.INSTANCE, 23);
        return builder;
    }

    @Override
    public int getRequiredVersion() {
        return -1;
    }
}