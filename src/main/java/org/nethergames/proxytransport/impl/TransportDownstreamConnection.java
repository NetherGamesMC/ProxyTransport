package org.nethergames.proxytransport.impl;

import dev.waterdog.waterdogpe.network.protocol.ProtocolVersion;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.network.session.DownstreamClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import net.jodah.expiringmap.internal.NamedThreadFactory;
import org.nethergames.proxytransport.ProxyTransport;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class TransportDownstreamConnection implements DownstreamClient {

    public static final int availableCPU = Runtime.getRuntime().availableProcessors();
    public static final ThreadFactory downstreamThreadFactory = new NamedThreadFactory("TCP-Downstream %s");
    public static final EventLoopGroup downstreamLoopGroup = Epoll.isAvailable() ? new EpollEventLoopGroup(availableCPU, downstreamThreadFactory) : new NioEventLoopGroup(availableCPU, downstreamThreadFactory);
    private final ServerInfo serverInfo;
    private Bootstrap channelBootstrap;
    private TransportDownstreamSession session;

    public TransportDownstreamConnection(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    public Class<? extends SocketChannel> getProperSocketChannel() {
        return Epoll.isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class;
    }

    @Override
    public CompletableFuture<DownstreamClient> bindDownstream(ProtocolVersion protocolVersion) {
        this.channelBootstrap = new Bootstrap()
                .group(downstreamLoopGroup)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {

                    }
                })
                .localAddress(new InetSocketAddress("0.0.0.0", 0))
                .channel(getProperSocketChannel())
                .remoteAddress(this.getServerInfo().getAddress());

        CompletableFuture<Void> fut = new CompletableFuture<>();
        fut.complete(null);
        return fut.thenApply(i -> this);
    }

    @Override
    public CompletableFuture<dev.waterdog.waterdogpe.network.session.DownstreamSession> connect(InetSocketAddress inetSocketAddress, long l, TimeUnit timeUnit) {
        ProxyTransport.getEventAdapter().connectionInitialized(inetSocketAddress, this.serverInfo);
        /*ITransaction transaction = Sentry.startTransaction("downstream-connect", "setup", true);
        transaction.setData("targetAddress", inetSocketAddress.toString());
        transaction.setData("targetServer", this.serverInfo.getServerName());
        transaction.setData("targetServerType", this.serverInfo.getServerType());
        ISpan connectSpan = transaction.startChild("establish-connection");*/
        CompletableFuture<dev.waterdog.waterdogpe.network.session.DownstreamSession> future = new CompletableFuture<>();
        this.channelBootstrap.connect().addListener(f -> {
            ChannelFuture cF = (ChannelFuture) f;
            if (cF.isSuccess()) {
                this.session = new TransportDownstreamSession(cF.channel(), this);
                future.complete(this.session);
            } else {
                future.completeExceptionally(f.cause());
            }

            ProxyTransport.getEventAdapter().connectionComplete(this.session, f.cause());
        });

        return future;
    }

    @Override
    public InetSocketAddress getBindAddress() {
        return (InetSocketAddress) this.session.getChannel().localAddress();
    }

    @Override
    public void close(boolean b) {
        if (this.session == null) return;
        this.session.disconnect();
    }

    @Override
    public boolean isConnected() {
        return this.session != null && !this.session.isClosed();
    }

    @Override
    public ServerInfo getServerInfo() {
        return this.serverInfo;
    }

    @Override
    public TransportDownstreamSession getSession() {
        return this.session;
    }

}
