package org.nethergames.proxytransport.impl;

import dev.waterdog.waterdogpe.network.connection.client.BedrockClientConnection;
import dev.waterdog.waterdogpe.network.connection.codec.packet.BedrockPacketCodec;
import dev.waterdog.waterdogpe.network.protocol.ProtocolVersion;
import dev.waterdog.waterdogpe.network.protocol.handler.ProxyBatchBridge;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicConnectionStats;
import io.netty.util.concurrent.Future;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.TickSyncPacket;
import org.nethergames.proxytransport.compression.FrameIdCodec;
import org.nethergames.proxytransport.compression.ProxyTransportCompressionCodec;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2(topic = "ClientConnection")
public class TransportClientConnection extends BedrockClientConnection {

    private static final int PING_CYCLE_TIME = 2; // 2 seconds
    private static final long MAX_UPSTREAM_PACKETS = 750;

    private final AtomicBoolean activeChannelLock = new AtomicBoolean(false);
    private final AtomicInteger packetSendingLimit = new AtomicInteger(0);
    private final AtomicBoolean packetSendingLock = new AtomicBoolean(false); // Lock packets from being sent to downstream servers.

    private final Channel channel;
    private long lastPingTimestamp;
    private long latency;

    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();

    public TransportClientConnection(ProxiedPlayer player, ServerInfo serverInfo, Channel channel) {
        super(player, serverInfo, channel);

        this.channel = channel;
        this.channel.closeFuture().addListener(future -> cleanActiveChannels());

        scheduledTasks.add(channel.eventLoop().scheduleAtFixedRate(this::collectStats, PING_CYCLE_TIME, PING_CYCLE_TIME, TimeUnit.SECONDS));
        scheduledTasks.add(channel.eventLoop().scheduleAtFixedRate(() -> packetSendingLimit.set(0), 1, 1, TimeUnit.SECONDS));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        scheduledTasks.forEach(task -> cleanActiveChannels());

        super.channelInactive(ctx);
    }

    public void cleanActiveChannels() {
        if (!activeChannelLock.compareAndSet(false, true)) {
            return;
        }

        for (Iterator<ScheduledFuture<?>> iterator = scheduledTasks.iterator(); iterator.hasNext(); ) {
            iterator.next().cancel(false);
            iterator.remove();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BedrockBatchWrapper batch) {
        if (getPacketHandler() instanceof ProxyBatchBridge) {
            onBedrockBatch(batch);
        }

        super.channelRead0(ctx, batch);
    }

    private void onBedrockBatch(@NonNull BedrockBatchWrapper batch) {
        ListIterator<BedrockPacketWrapper> iterator = batch.getPackets().listIterator();
        while (iterator.hasNext()) {
            BedrockPacketWrapper wrapper = iterator.next();
            if (wrapper.getPacket() == null) {
                this.decodePacket(wrapper);
            }

            if (wrapper.getPacket() instanceof NetworkStackLatencyPacket packet && packet.getTimestamp() == 0) {
                iterator.remove(); // remove from batch
                wrapper.release(); // release
                batch.modify();

                this.latency = (System.currentTimeMillis() - this.lastPingTimestamp) / 2;
                this.broadcastPing();
            }
        }
    }

    private boolean increaseRateLimit(int value) {
        packetSendingLimit.set(this.packetSendingLimit.get() + value);

        if (packetSendingLimit.get() >= MAX_UPSTREAM_PACKETS) {
            if (packetSendingLock.compareAndSet(false, true)) {
                getPlayer().getLogger().warning(getPlayer().getName() + " sent too many packets (" + packetSendingLimit.get() + "/s), disconnecting.");
                getPlayer().getConnection().disconnect("§cToo many packets!");
            }
        } else return !packetSendingLock.get();

        return false;
    }

    @Override
    public void sendPacket(BedrockPacket packet) {
        if (this.increaseRateLimit(1)) {
            super.sendPacket(packet);
        }
    }

    @Override
    public void sendPacketImmediately(BedrockPacket packet) {
        if (this.increaseRateLimit(1)) {
            super.sendPacketImmediately(packet);
        }
    }

    @Override
    public void sendPacket(BedrockBatchWrapper wrapper) {
        if (this.increaseRateLimit(wrapper.getPackets().size())) {
            super.sendPacket(wrapper);
            return;
        }

        wrapper.release();
    }

    @Override
    public void setCompressionStrategy(CompressionStrategy strategy) {
        super.setCompressionStrategy(strategy);

        boolean needsPrefix = this.getPlayer().getProtocol().isAfterOrEqual(ProtocolVersion.MINECRAFT_PE_1_20_60);
        ChannelHandler handler = this.channel.pipeline().get(CompressionCodec.NAME);
        if (handler == null) {
            this.channel.pipeline().addAfter(FrameIdCodec.NAME, CompressionCodec.NAME, new ProxyTransportCompressionCodec(strategy, needsPrefix));
        } else {
            this.channel.pipeline().replace(CompressionCodec.NAME, CompressionCodec.NAME, new ProxyTransportCompressionCodec(strategy, needsPrefix));
        }
    }

    @Override
    public void enableEncryption(SecretKey secretKey) {
        // Encryption is generally not good in server-to-server scenarios
    }

    @Override
    public long getPing() {
        return latency;
    }

    public void collectStats() {
        var connection = getPlayer().getDownstreamConnection();
        if (connection instanceof TransportClientConnection && connection.getServerInfo().getServerName().equalsIgnoreCase(getServerInfo().getServerName())) {
            if (this.channel instanceof NioSocketChannel) {
                NetworkStackLatencyPacket packet = new NetworkStackLatencyPacket();
                packet.setTimestamp(0L);
                packet.setFromServer(true);

                sendPacket(packet);

                this.lastPingTimestamp = System.currentTimeMillis();
            } else if (this.channel instanceof EpollSocketChannel epollChannel) {
                this.latency = epollChannel.tcpInfo().rtt() / 2;
                this.broadcastPing();
            } else if (this.channel instanceof QuicChannel quicChannel) {
                quicChannel.collectStats().addListener((Future<QuicConnectionStats> future) -> {
                    if (future.isSuccess()) {
                        QuicConnectionStats stats = future.getNow();

                        this.latency = stats.recv();
                        this.broadcastPing();
                    }
                });
            }
        }
    }

    private void broadcastPing() {
        TickSyncPacket latencyPacket = new TickSyncPacket();
        latencyPacket.setRequestTimestamp(getPlayer().getPing());
        latencyPacket.setResponseTimestamp(this.latency);

        sendPacket(latencyPacket);
    }

    private void decodePacket(BedrockPacketWrapper wrapper) {
        BedrockCodec codec = channel.pipeline().get(BedrockPacketCodec.class).getCodec();
        BedrockCodecHelper helper = channel.pipeline().get(BedrockPacketCodec.class).getHelper();

        ByteBuf msg = wrapper.getPacketBuffer().retainedSlice();
        try {
            msg.skipBytes(wrapper.getHeaderLength()); // skip header
            wrapper.setPacket(codec.tryDecode(helper, msg, wrapper.getPacketId()));
        } catch (Throwable t) {
            log.warn("Failed to decode packet", t);
            throw t;
        } finally {
            msg.release();
        }
    }
}
