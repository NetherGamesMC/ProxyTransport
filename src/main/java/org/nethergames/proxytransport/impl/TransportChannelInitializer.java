package org.nethergames.proxytransport.impl;

import dev.waterdog.waterdogpe.network.NetworkMetrics;
import dev.waterdog.waterdogpe.network.PacketDirection;
import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.network.connection.codec.batch.BedrockBatchDecoder;
import dev.waterdog.waterdogpe.network.connection.codec.batch.BedrockBatchEncoder;
import dev.waterdog.waterdogpe.network.connection.codec.client.ClientEventHandler;
import dev.waterdog.waterdogpe.network.connection.codec.compression.CompressionAlgorithm;
import dev.waterdog.waterdogpe.network.connection.codec.packet.BedrockPacketCodec;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.channel.*;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.concurrent.Promise;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.netty.channel.raknet.RakChannel;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.channel.raknet.config.RakMetrics;
import org.nethergames.proxytransport.decoder.PartialDecompressor;
import org.nethergames.proxytransport.encoder.DataPackEncoder;

import static dev.waterdog.waterdogpe.network.connection.codec.initializer.ProxiedSessionInitializer.BATCH_DECODER;
import static dev.waterdog.waterdogpe.network.connection.codec.initializer.ProxiedSessionInitializer.getPacketCodec;

public class TransportChannelInitializer extends ChannelInitializer<Channel> {
    private final ProxiedPlayer player;
    private final ServerInfo serverInfo;
    private final Promise<ClientConnection> promise;
    private static final String FRAME_DECODER = "frame-decoder";
    private static final String FRAME_ENCODER = "frame-encoder";

    public TransportChannelInitializer(ProxiedPlayer player, ServerInfo serverInfo, Promise<ClientConnection> promise) {
        this.player = player;
        this.serverInfo = serverInfo;
        this.promise = promise;
    }

    @Override
    protected void initChannel(Channel channel) {
        int rakVersion = this.player.getProtocol().getRaknetVersion();

        channel.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.FROM_SERVER);

        NetworkMetrics metrics = this.player.getProxy().getNetworkMetrics();
        if (metrics != null) {
            channel.attr(NetworkMetrics.ATTRIBUTE).set(metrics);
        }

        if (metrics instanceof RakMetrics rakMetrics && channel instanceof RakChannel) {
            channel.config().setOption(RakChannelOption.RAK_METRICS, rakMetrics);
        }

        channel.pipeline()
                .addLast(FRAME_DECODER, new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4))
                .addLast(FRAME_ENCODER, new LengthFieldPrepender(4));


        ClientConnection connection = this.createConnection(channel);
        channel.pipeline()
                .addLast(DataPackEncoder.NAME, new DataPackEncoder(connection))
                .addLast(PartialDecompressor.NAME, new PartialDecompressor(connection))
                .addLast(BedrockBatchDecoder.NAME, BATCH_DECODER)
                .addLast(BedrockBatchEncoder.NAME, new BedrockBatchEncoder())
                .addLast(BedrockPacketCodec.NAME, getPacketCodec(rakVersion));

        if (connection instanceof ChannelHandler handler) { // For reference: This will take care of the packets received being handled.
            channel.pipeline().addLast(ClientConnection.NAME, handler);
        }

        channel.pipeline()
                .addLast(ClientEventHandler.NAME, new ClientEventHandler(this.player, connection))
                .addLast(new TransportChannelInitializer.ChannelActiveHandler(connection, this.promise));
    }

    protected ClientConnection createConnection(Channel channel) {
        return new TransportClientConnection(player, serverInfo, channel);
    }

    @RequiredArgsConstructor
    private static class ChannelActiveHandler extends ChannelInboundHandlerAdapter {
        private final ClientConnection connection;
        private final Promise<ClientConnection> promise;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            this.promise.trySuccess(this.connection);
            ctx.channel().pipeline().remove(this);
        }
    }
}
