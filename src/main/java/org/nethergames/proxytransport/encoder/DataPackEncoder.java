package org.nethergames.proxytransport.encoder;

import dev.waterdog.waterdogpe.ProxyServer;
import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.network.connection.codec.BedrockBatchWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import lombok.RequiredArgsConstructor;
import org.nethergames.proxytransport.utils.CompressionType;

import java.util.List;

@RequiredArgsConstructor
public class DataPackEncoder extends MessageToMessageEncoder<BedrockBatchWrapper> {
    public static final String NAME = "data-pack-encoder";
    private final ClientConnection clientConnection;

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, BedrockBatchWrapper wrapper, List<Object> out) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.ioBuffer();
        try {
            // The batch was modified or the compression types mismatch
            if (wrapper.isModified() && wrapper.getUncompressed() != null || (wrapper.getAlgorithm() != clientConnection.getPlayer().getCompression())) {

                buf.writeByte(CompressionType.METHOD_ZSTD.ordinal());
                ByteBuf source = wrapper.getUncompressed();

                if (!source.isDirect() || source instanceof CompositeByteBuf) {
                    // ZStd-jni needs direct buffers to function properly
                    // Composite Buffers or indirect buffers will not generate valid NIO ByteBuffers

                    source = channelHandlerContext.alloc().ioBuffer(source.readableBytes());
                    source.writeBytes(source);
                }

                ByteBuf compressed = ZStdEncoder.compress(source);
                buf.writeBytes(compressed);
            } else if (!wrapper.isModified() && wrapper.getCompressed() != null) { // The batch is already compressed correctly and we can yeet the buffer straight to the server
                buf.writeByte(CompressionType.METHOD_ZLIB.ordinal());
                buf.writeBytes(wrapper.getCompressed());
            }
        } catch (Throwable t) {
            ProxyServer.getInstance().getLogger().error("Error in DataPack Encoding", t);
        }

        out.add(buf);

    }
}
