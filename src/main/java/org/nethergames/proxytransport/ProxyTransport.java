package org.nethergames.proxytransport;

import dev.waterdog.waterdogpe.plugin.Plugin;
import org.nethergames.proxytransport.network.integration.CustomTransportServerInfo;

public class ProxyTransport extends Plugin {

    @Override
    public void onEnable() {
        getProxy().getServerInfoMap().removeServerInfoType(CustomTransportServerInfo.TYPE);
        getProxy().getServerInfoMap().registerServerInfoFactory(CustomTransportServerInfo.TYPE, CustomTransportServerInfo::new);
    }
}
