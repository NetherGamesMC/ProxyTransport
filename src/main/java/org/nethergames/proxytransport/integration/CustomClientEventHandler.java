package org.nethergames.proxytransport.integration;

import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.network.connection.handler.ReconnectReason;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import dev.waterdog.waterdogpe.utils.types.TranslationContainer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class CustomClientEventHandler extends ChannelInboundHandlerAdapter {
    public static final String NAME = "client-event-handler";

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

        this.player.getLogger().warning("[" + connection.getSocketAddress() + "|" + this.player.getName() + "] - exception caught", cause);
        this.connection.disconnect();

        TranslationContainer msg = new TranslationContainer("waterdog.downstream.down", this.connection.getServerInfo().getServerName(), cause.getMessage());
        if (this.player.sendToFallback(this.connection.getServerInfo(), ReconnectReason.EXCEPTION, cause.getMessage())) {
            this.player.sendMessage(msg);
        } else {
            this.player.disconnect(msg);
        }
    }
}