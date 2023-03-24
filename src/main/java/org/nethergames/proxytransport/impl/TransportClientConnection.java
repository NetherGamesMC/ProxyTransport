package org.nethergames.proxytransport.impl;

import dev.waterdog.waterdogpe.network.connection.client.BedrockClientConnection;
import dev.waterdog.waterdogpe.network.connection.codec.BedrockBatchWrapper;
import dev.waterdog.waterdogpe.network.connection.codec.compression.CompressionAlgorithm;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.channel.Channel;
import io.netty.channel.epoll.EpollSocketChannel;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import javax.crypto.SecretKey;

public class TransportClientConnection extends BedrockClientConnection {

    private final Channel channel;

    public TransportClientConnection(ProxiedPlayer player, ServerInfo serverInfo, Channel channel) {
        super(player, serverInfo, channel);

        this.channel = channel;
    }

    @Override
    public void sendPacket(BedrockPacket packet) {
        super.sendPacket(BedrockBatchWrapper.create(getSubClientId(), packet));
    }

    @Override
    public void setCompression(CompressionAlgorithm compression) {
        // We do not want to change compression because we have our own logic
    }

    @Override
    public void enableEncryption(@NonNull SecretKey secretKey) {
        // Encryption is generally not good in server-to-server scenarios
    }

    @Override
    public long getPing() {
        // TCP_INFO is only supported in linux-family operating system as for now.
        // So far this is the best option to calculate the player latency
        if (channel instanceof EpollSocketChannel epollChannel) {
            var tcpInfo = epollChannel.tcpInfo();

            return tcpInfo.lastAckRecv() - tcpInfo.lastAckSent();
        } else {
            return 0L;
        }
    }
}
