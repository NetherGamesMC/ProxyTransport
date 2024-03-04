package org.nethergames.proxytransport;

import dev.waterdog.waterdogpe.network.protocol.ProtocolCodecs;
import dev.waterdog.waterdogpe.plugin.Plugin;
import org.nethergames.proxytransport.integration.QuicTransportServerInfo;
import org.nethergames.proxytransport.integration.TcpTransportServerInfo;
import org.nethergames.proxytransport.utils.CodecUpdater;

public class ProxyTransport extends Plugin {

    @Override
    public void onStartup() {
        ProtocolCodecs.addUpdater(new CodecUpdater());

        try {
            Class.forName("io.netty.incubator.codec.quic.QuicheNativeStaticallyReferencedJniMethods");
            getLogger().info("QUIC is supported");
        } catch (ClassNotFoundException e) {
            getLogger().info("QUIC is not supported / shading failed");
        }

        getLogger().info("ProxyTransport was started.");
        getLogger().info("Registered type with name {}", QuicTransportServerInfo.TYPE.getIdentifier());
        getLogger().info("Registered type with name {}", TcpTransportServerInfo.TYPE.getIdentifier());
    }
    
    @Override
    public void onEnable() {
        getLogger().info("ProxyTransport was enabled.");
    }
}
