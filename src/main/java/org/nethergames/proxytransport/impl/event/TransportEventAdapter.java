package org.nethergames.proxytransport.impl.event;

import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.network.session.DownstreamSession;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

public interface TransportEventAdapter {
    public void connectionInitialized(InetSocketAddress targetAddress, ServerInfo targetServer);
    public void connectionComplete(DownstreamSession session, Throwable t);
    public void downstreamInitialized(DownstreamSession session, ProxiedPlayer player, boolean initial);
    public void initialServerConnected(DownstreamSession session);
    public void transferCompleted(DownstreamSession session);
    public void downstreamException(DownstreamSession session, Throwable t, @Nullable ByteBuf origin);
    public boolean bufferDump(String id, String bufferDump);
}
