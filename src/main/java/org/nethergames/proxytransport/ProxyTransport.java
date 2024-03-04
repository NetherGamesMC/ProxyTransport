package org.nethergames.proxytransport;

import dev.waterdog.waterdogpe.network.protocol.ProtocolCodecs;
import dev.waterdog.waterdogpe.plugin.Plugin;
import io.netty.incubator.codec.quic.Quic;
import org.nethergames.proxytransport.integration.QuicTransportServerInfo;
import org.nethergames.proxytransport.integration.TcpTransportServerInfo;
import org.nethergames.proxytransport.utils.CodecUpdater;

public class ProxyTransport extends Plugin {

    @Override
    public void onStartup() {
        ProtocolCodecs.addUpdater(new CodecUpdater());
        getLogger().info("Registered architecture: {} | {}", System.getProperty("os.name"), System.getProperty("os.arch"));

        getLogger().info("System Properties: {}", System.getProperties().toString());
        try {
            Class.forName("io.netty.incubator.codec.quic.QuicheNativeStaticallyReferencedJniMethods");
            Quic.ensureAvailability();
            getLogger().info("QUIC is supported");
        } catch (Exception e) {
            getLogger().info("QUIC is not supported / shading failed");
            getLogger().error(e);
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
