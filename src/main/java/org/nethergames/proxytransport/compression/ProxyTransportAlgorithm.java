package org.nethergames.proxytransport.compression;

import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;

public enum ProxyTransportAlgorithm implements CompressionAlgorithm {
    ZSTD;

    private ProxyTransportAlgorithm() {

    }
}
