package org.nethergames.proxytransport.decoder;

import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.network.connection.codec.BedrockBatchWrapper;
import dev.waterdog.waterdogpe.network.connection.codec.compression.SnappyCompressionCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.common.util.Zlib;

import java.util.List;

@RequiredArgsConstructor
public class PartialDecompressor extends MessageToMessageDecoder<ByteBuf> {
    public static final String NAME = "decompress";
    private final ClientConnection connection;
    private static final SnappyCompressionCodec snappyCompressionCodec = new SnappyCompressionCodec();


    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf compressed, List<Object> list) throws Exception {
        ByteBuf decompressed = null;

        compressed.markReaderIndex();
        switch (connection.getPlayer().getCompression().getBedrockAlgorithm()){
            case ZLIB -> decompressed = Zlib.RAW.inflate(compressed, 4 * 1024 * 1024);
            case SNAPPY -> decompressed = snappyCompressionCodec.decode0(channelHandlerContext, compressed);
        }

        if(decompressed == null){
            throw new UnsupportedOperationException("The given compression algorithm is not supported by ProxyTransport");
        }

        compressed.resetReaderIndex();
        list.add(BedrockBatchWrapper.newInstance(compressed.retain(), decompressed));
    }
}
