package org.nethergames.proxytransport.integration;

import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfoType;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import net.jodah.expiringmap.internal.NamedThreadFactory;
import org.nethergames.proxytransport.impl.TransportChannelInitializer;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class QuicTransportServerInfo extends ServerInfo {
    public static final int availableCPU = Runtime.getRuntime().availableProcessors();
    public static final ThreadFactory downstreamThreadFactory = new NamedThreadFactory("QUIC-Downstream %s");
    public static final EventLoopGroup downstreamLoopGroup = Epoll.isAvailable() ? new EpollEventLoopGroup(availableCPU, downstreamThreadFactory) : new NioEventLoopGroup(availableCPU, downstreamThreadFactory);

    public static final String TYPE_IDENT = "quic";
    public static final ServerInfoType TYPE = ServerInfoType.builder()
            .identifier(TYPE_IDENT)
            .serverInfoFactory(QuicTransportServerInfo::new)
            .register();

    private final HashMap<InetSocketAddress, Future<QuicChannel>> serverConnections = new HashMap<>();

    public QuicTransportServerInfo(String serverName, InetSocketAddress address, InetSocketAddress publicAddress) {
        super(serverName, address, publicAddress);
    }

    @Override
    public ServerInfoType getServerType() {
        return TYPE;
    }

    @Override
    public Future<ClientConnection> createConnection(ProxiedPlayer proxiedPlayer) {
        EventLoop eventLoop = proxiedPlayer.getProxy().getWorkerEventLoopGroup().next();
        Promise<ClientConnection> promise = eventLoop.newPromise();

        this.createServerConnection(eventLoop, this.getAddress()).addListener((Future<QuicChannel> future) -> {
            if (future.isSuccess()) {
                QuicChannel quicChannel = future.getNow();

                quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new TransportChannelInitializer(proxiedPlayer, this, promise)).addListener((Future<QuicStreamChannel> streamFuture) -> {
                    if (!streamFuture.isSuccess()) {
                        promise.tryFailure(streamFuture.cause());
                        quicChannel.close();
                    }
                });
            } else {
                promise.tryFailure(future.cause());
            }
        });

        return promise;
    }

    private Future<QuicChannel> createServerConnection(EventLoopGroup eventLoopGroup, InetSocketAddress address) {
        EventLoop eventLoop = eventLoopGroup.next();

        if (this.serverConnections.containsKey(address)) {
            return this.serverConnections.get(address);
        }

        Promise<QuicChannel> promise = eventLoop.newPromise();
        this.serverConnections.put(address, promise);

        QuicSslContext sslContext = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocols("ng").build();
        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .build();

        new Bootstrap()
                .group(downstreamLoopGroup)
                .handler(codec)
                .channel(getProperSocketChannel())
                .bind(0).addListener((ChannelFuture channelFuture) -> {
                    if (channelFuture.isSuccess()) {
                        QuicChannel.newBootstrap(channelFuture.channel())
                                .streamHandler(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        ctx.close();
                                    }
                                })
                                .remoteAddress(address)
                                .connect().addListener((Future<QuicChannel> quicChannelFuture) -> {
                                    if (quicChannelFuture.isSuccess()) {
                                        QuicChannel quicChannel = quicChannelFuture.getNow();
                                        quicChannel.closeFuture().addListener(f -> serverConnections.remove(address));

                                        promise.trySuccess(quicChannel);
                                    } else {
                                        promise.tryFailure(quicChannelFuture.cause());
                                        channelFuture.channel().close();
                                    }
                                });
                    } else {
                        promise.tryFailure(channelFuture.cause());
                        channelFuture.channel().close();
                    }
                });

        return promise;
    }

    public Class<? extends DatagramChannel> getProperSocketChannel() {
        return Epoll.isAvailable() ? EpollDatagramChannel.class : NioDatagramChannel.class;
    }
}
