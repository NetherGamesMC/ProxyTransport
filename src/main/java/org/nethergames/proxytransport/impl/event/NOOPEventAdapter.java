package org.nethergames.proxytransport.impl.event;

import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.network.session.DownstreamSession;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

public class NOOPEventAdapter implements TransportEventAdapter{
    @Override
    public void connectionInitialized(InetSocketAddress targetAddress, ServerInfo targetServer) {

    }

    @Override
    public void connectionComplete(DownstreamSession session, Throwable t) {

    }

    @Override
    public void downstreamInitialized(DownstreamSession session, ProxiedPlayer player, boolean initial) {

    }

    @Override
    public void initialServerConnected(DownstreamSession session) {

    }

    @Override
    public void transferCompleted(DownstreamSession session) {

    }

    @Override
    public void downstreamException(DownstreamSession session, Throwable t, @Nullable ByteBuf origin) {

    }

    @Override
    public boolean bufferDump(String id, String bufferDump) {
        return false;
    }
}
