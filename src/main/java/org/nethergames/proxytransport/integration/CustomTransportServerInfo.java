package org.nethergames.proxytransport.integration;

import dev.waterdog.waterdogpe.network.protocol.ProtocolVersion;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfoType;
import dev.waterdog.waterdogpe.network.session.DownstreamClient;
import org.nethergames.proxytransport.impl.TransportDownstreamConnection;

import java.net.InetSocketAddress;

public class CustomTransportServerInfo extends ServerInfo {

    public static final String TYPE_IDENT = "CUSTOM_TCP";
    public static final ServerInfoType TYPE = ServerInfoType.fromString(TYPE_IDENT);

    public CustomTransportServerInfo(String serverName, InetSocketAddress address, InetSocketAddress publicAddress) {
        super(serverName, address, publicAddress);
    }

    @Override
    public DownstreamClient createNewConnection(ProtocolVersion protocolVersion) {
        return new TransportDownstreamConnection(this);
    }

    @Override
    public ServerInfoType getServerType() {
        return TYPE;
    }
}
