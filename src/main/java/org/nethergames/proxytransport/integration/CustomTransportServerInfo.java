package org.nethergames.proxytransport.integration;

import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfoType;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
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

public class CustomTransportServerInfo extends ServerInfo {
    public static final int availableCPU = Runtime.getRuntime().availableProcessors();
    public static final ThreadFactory downstreamThreadFactory = new NamedThreadFactory("TCP-Downstream %s");
    public static final EventLoopGroup downstreamLoopGroup = Epoll.isAvailable() ? new EpollEventLoopGroup(availableCPU, downstreamThreadFactory) : new NioEventLoopGroup(availableCPU, downstreamThreadFactory);

    public static final String TYPE_IDENT = "quic";
    public static final ServerInfoType TYPE = ServerInfoType.builder()
            .identifier(TYPE_IDENT)
            .serverInfoFactory(CustomTransportServerInfo::new)
            .register();

    private final HashMap<InetSocketAddress, QuicChannel> serverConnections = new HashMap<>();

    public CustomTransportServerInfo(String serverName, InetSocketAddress address, InetSocketAddress publicAddress) {
        super(serverName, address, publicAddress);
    }

    @Override
    public ServerInfoType getServerType() {
        return TYPE;
    }

    @Override
    public Future<ClientConnection> createConnection(ProxiedPlayer proxiedPlayer) {
        System.out.println("Creating connection for " + proxiedPlayer.getName());
        EventLoop eventLoop = proxiedPlayer.getProxy().getWorkerEventLoopGroup().next();
        Promise<ClientConnection> promise = eventLoop.newPromise();

        createServerConnection(eventLoop, this.getAddress()).addListener((Future<QuicChannel> future) -> {
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

    private QuicClientCodecBuilder getQuicCodecBuilder() {
        return new QuicClientCodecBuilder()
                .maxIdleTimeout(2000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .sslEngineProvider(q ->
                        QuicSslContextBuilder.forClient()
                                .applicationProtocols("ng")
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build().newEngine(q.alloc())
                );
    }

    private Future<QuicChannel> createServerConnection(EventLoopGroup eventLoopGroup, InetSocketAddress address) {
        EventLoop eventLoop = eventLoopGroup.next();

        if (serverConnections.containsKey(address)) {
            return eventLoop.newSucceededFuture(serverConnections.get(address));
        }

        Promise<QuicChannel> promise = eventLoop.newPromise();

        Bootstrap b = new Bootstrap()
                .group(downstreamLoopGroup)
                .handler(this.getQuicCodecBuilder().build())
                .localAddress(new InetSocketAddress("0.0.0.0", 0))
                .channel(getProperSocketChannel())
                .remoteAddress(address);

        b.connect().addListener((ChannelFuture channelFuture) -> {
            if (channelFuture.isSuccess()) {
                QuicChannel.newBootstrap(channelFuture.channel())
                        .streamHandler(new ChannelInboundHandlerAdapter())
                        .remoteAddress(address)
                        .connect().addListener((Future<QuicChannel> quicChannelFuture)-> {
                            if (quicChannelFuture.isSuccess()) {
                                QuicChannel quicChannel = quicChannelFuture.getNow();
                                quicChannel.closeFuture().addListener(f -> serverConnections.remove(address));
                                serverConnections.put(address, quicChannel);

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

    public Class<? extends SocketChannel> getProperSocketChannel() {
        return Epoll.isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class;
    }
}
