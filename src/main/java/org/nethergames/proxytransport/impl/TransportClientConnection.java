package org.nethergames.proxytransport.impl;

import dev.waterdog.waterdogpe.network.connection.client.BedrockClientConnection;
import dev.waterdog.waterdogpe.network.connection.codec.BedrockBatchWrapper;
import dev.waterdog.waterdogpe.network.connection.codec.compression.CompressionAlgorithm;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.channel.Channel;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import javax.crypto.SecretKey;

public class TransportClientConnection extends BedrockClientConnection {

    public TransportClientConnection(ProxiedPlayer player, ServerInfo serverInfo, Channel channel) {
        super(player, serverInfo, channel);
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
}
