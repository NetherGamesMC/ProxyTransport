package org.nethergames.proxytransport;

import dev.waterdog.waterdogpe.plugin.Plugin;
import org.nethergames.proxytransport.impl.event.TransportEventAdapter;
import org.nethergames.proxytransport.integration.CustomTransportServerInfo;

public class ProxyTransport extends Plugin {
    private static TransportEventAdapter eventAdapter;

    @Override
    public void onEnable() {
        getProxy().getServerInfoMap().removeServerInfoType(CustomTransportServerInfo.TYPE);
        getProxy().getServerInfoMap().registerServerInfoFactory(CustomTransportServerInfo.TYPE, CustomTransportServerInfo::new);
    }

    public static TransportEventAdapter getEventAdapter() {
        return eventAdapter;
    }

    public static void setEventAdapter(TransportEventAdapter eventAdapter) {
        ProxyTransport.eventAdapter = eventAdapter;
    }
}
