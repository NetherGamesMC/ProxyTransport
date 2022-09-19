package org.nethergames.proxytransport.integration;

import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockSession;
import com.nukkitx.protocol.bedrock.handler.BatchHandler;
import dev.waterdog.waterdogpe.network.bridge.TransferBatchBridge;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.buffer.ByteBuf;
import org.nethergames.proxytransport.impl.TransportDownstreamSession;

import java.util.Collection;

public class CustomTransportBatchBridge extends TransferBatchBridge implements BatchHandler {

    private final TransportDownstreamSession session;

    public CustomTransportBatchBridge(ProxiedPlayer player, TransportDownstreamSession session, BedrockSession upstreamSession) {
        super(player, upstreamSession);
        this.session = session;
    }

    @Override
    public void flushQueue() {
        if (session.getChannel().eventLoop().inEventLoop()) {
            super.flushQueue();
        } else {
            session.getChannel().eventLoop().execute(super::flushQueue);
        }
    }

    @Override
    public void free() {
        if (session.getChannel().eventLoop().inEventLoop()) {
            super.free();
        } else {
            session.getChannel().eventLoop().execute(super::free);
        }
    }

    @Override
    public void handle(BedrockSession bedrockSession, ByteBuf byteBuf, Collection<BedrockPacket> collection) {
        this.handle(bedrockSession.getPacketHandler(), byteBuf, collection, this.session.getCompression());
    }
}
