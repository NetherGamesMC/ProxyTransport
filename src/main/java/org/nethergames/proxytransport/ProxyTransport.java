package org.nethergames.proxytransport;

import dev.waterdog.waterdogpe.network.protocol.ProtocolCodecs;
import dev.waterdog.waterdogpe.plugin.Plugin;
import org.nethergames.proxytransport.integration.CustomTransportServerInfo;
import org.nethergames.proxytransport.utils.CodecUpdater;

public class ProxyTransport extends Plugin {

    @Override
    public void onStartup() {
        ProtocolCodecs.addUpdater(new CodecUpdater());

        getLogger().info("ProxyTransport was started.");
        getLogger().info("Registered type with name {}", CustomTransportServerInfo.TYPE.getIdentifier());
    }
    
    @Override
    public void onEnable() {
        getLogger().info("ProxyTransport was enabled.");
    }
}
