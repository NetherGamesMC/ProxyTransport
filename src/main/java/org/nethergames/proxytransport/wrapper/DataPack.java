package org.nethergames.proxytransport.wrapper;

import dev.waterdog.waterdogpe.network.session.CompressionAlgorithm;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

@Getter
/**
 * Data Packs are sent as frame contents from the proxy to downstream. They are essentially just the buffer + the desired compression method.
 */
public class DataPack {

    private final CompressionType compressionType;
    private final ByteBuf containingBuffer;

    public DataPack(CompressionType prefix, ByteBuf holder) {
        this.compressionType = prefix;
        this.containingBuffer = holder;
    }

    public enum CompressionType {
        METHOD_ZLIB,
        METHOD_ZSTD,
        METHOD_SNAPPY;

        public static CompressionType fromInternal(CompressionAlgorithm type) {
            switch (type.getBedrockCompression()) {
                case SNAPPY:
                    return CompressionType.METHOD_SNAPPY;
                case ZLIB:
                default:
                    return CompressionType.METHOD_ZLIB;
            }
        }
    }
}
