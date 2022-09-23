package org.nethergames.proxytransport.integration;

import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockSession;
import com.nukkitx.protocol.bedrock.handler.BatchHandler;
import dev.waterdog.waterdogpe.network.bridge.DownstreamBridge;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.buffer.ByteBuf;
import org.nethergames.proxytransport.impl.TransportDownstreamSession;

import java.util.Collection;

public class TransportDownstreamBridge extends DownstreamBridge implements BatchHandler {

    private final TransportDownstreamSession session;

    public TransportDownstreamBridge(ProxiedPlayer player, BedrockSession upstreamSession, TransportDownstreamSession downstreamSession){
        super(player, upstreamSession);
        this.session = downstreamSession;
    }

    @Override
    public void handle(BedrockSession bedrockSession, ByteBuf byteBuf, Collection<BedrockPacket> collection) {
        super.handle(this.session.getPacketHandler(), byteBuf, collection, this.session.getCompression());
    }
}
