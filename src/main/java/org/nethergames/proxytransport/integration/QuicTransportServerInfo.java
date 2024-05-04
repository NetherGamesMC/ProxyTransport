package org.nethergames.proxytransport.integration;

import dev.waterdog.waterdogpe.logger.MainLogger;
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
import java.util.concurrent.ConcurrentHashMap;
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

    private final ConcurrentHashMap<InetSocketAddress, Future<QuicChannel>> serverConnections = new ConcurrentHashMap<>();

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

        this.createServerConnection(eventLoop, proxiedPlayer.getLogger(), this.getAddress()).addListener((Future<QuicChannel> future) -> {
            if (future.isSuccess()) {
                proxiedPlayer.getLogger().debug("Creating stream for " + this.getServerName() + " server");
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

    private Future<QuicChannel> createServerConnection(EventLoopGroup eventLoopGroup, MainLogger logger, InetSocketAddress address) {
        EventLoop eventLoop = eventLoopGroup.next();

        if (this.serverConnections.containsKey(address)) {
            logger.info("Reusing connection to " + address + " for " + this.getServerName() + " server");
            return this.serverConnections.get(address);
        }

        logger.info("Creating connection to " + address + " for " + this.getServerName() + " server");
        Promise<QuicChannel> promise = eventLoop.newPromise();
        this.serverConnections.put(address, promise);

        QuicSslContext sslContext = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocols("ng").build();
        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(2000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .maxRecvUdpPayloadSize(1350)
                .maxSendUdpPayloadSize(1350)
                .activeMigration(false)
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
                                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                        ctx.close();
                                    }
                                })
                                .remoteAddress(address)
                                .connect().addListener((Future<QuicChannel> quicChannelFuture) -> {
                                    if (quicChannelFuture.isSuccess()) {
                                        logger.debug("Connection to " + address + " for " + this.getServerName() + " server established");

                                        QuicChannel quicChannel = quicChannelFuture.getNow();
                                        quicChannel.closeFuture().addListener(f -> {
                                            logger.debug("Connection to " + address + " for " + this.getServerName() + " server closed");
                                            channelFuture.channel().close();
                                            this.serverConnections.remove(address);
                                        });

                                        promise.trySuccess(quicChannel);
                                    } else {
                                        logger.warning("Connection to " + address + " for " + this.getServerName() + " server failed");

                                        promise.tryFailure(quicChannelFuture.cause());
                                        channelFuture.channel().close();
                                        this.serverConnections.remove(address);
                                    }
                                });
                    } else {
                        promise.tryFailure(channelFuture.cause());
                        channelFuture.channel().close();
                        this.serverConnections.remove(address);
                    }
                });

        return promise;
    }

    public Class<? extends DatagramChannel> getProperSocketChannel() {
        return Epoll.isAvailable() ? EpollDatagramChannel.class : NioDatagramChannel.class;
    }
}
