package org.nethergames.proxytransport.integration;

import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class CustomClientEventHandler extends ChannelInboundHandlerAdapter {
    public static final String NAME = "tcp-client-event-handler";

    private final ProxiedPlayer player;
    private final ClientConnection connection;

    public CustomClientEventHandler(ProxiedPlayer player, ClientConnection connection) {
        this.player = player;
        this.connection = connection;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.player.onDownstreamDisconnected(this.connection);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!ctx.channel().isActive()) {
            return;
        }

        this.player.getLogger().warning("[" + connection.getSocketAddress() + "|" + this.player.getName() + "] - Exception in TCP connection caught", cause);
        this.player.onDownstreamTimeout(this.connection.getServerInfo());

        this.connection.disconnect();
    }
}