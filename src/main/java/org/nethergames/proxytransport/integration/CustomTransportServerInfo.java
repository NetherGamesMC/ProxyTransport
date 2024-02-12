package org.nethergames.proxytransport.integration;

import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfoType;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import net.jodah.expiringmap.internal.NamedThreadFactory;
import org.nethergames.proxytransport.impl.TransportChannelInitializer;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadFactory;

public class CustomTransportServerInfo extends ServerInfo {
    public static final int availableCPU = Runtime.getRuntime().availableProcessors();
    public static final ThreadFactory downstreamThreadFactory = new NamedThreadFactory("TCP-Downstream %s");
    public static final EventLoopGroup downstreamLoopGroup = Epoll.isAvailable() ? new EpollEventLoopGroup(availableCPU, downstreamThreadFactory) : new NioEventLoopGroup(availableCPU, downstreamThreadFactory);

    public static final String TYPE_IDENT = "tcp";
    public static final ServerInfoType TYPE = ServerInfoType.builder()
            .identifier(TYPE_IDENT)
            .serverInfoFactory(CustomTransportServerInfo::new)
            .register();

    public CustomTransportServerInfo(String serverName, InetSocketAddress address, InetSocketAddress publicAddress) {
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

        new Bootstrap()
                .group(downstreamLoopGroup)
                .handler(new TransportChannelInitializer(proxiedPlayer, this, promise))
                .localAddress(new InetSocketAddress("0.0.0.0", 0))
                .channel(getProperSocketChannel())
                .remoteAddress(this.getAddress())
                .connect().addListener((ChannelFuture future) -> {
                    if (!future.isSuccess()) {
                        promise.tryFailure(future.cause());
                        future.channel().close();
                    }
                });

        return promise;
    }

    public Class<? extends SocketChannel> getProperSocketChannel() {
        return Epoll.isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class;
    }
}
