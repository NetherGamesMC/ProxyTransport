package org.nethergames.proxytransport.compression;

import com.github.luben.zstd.Zstd;
import dev.waterdog.waterdogpe.ProxyServer;
import dev.waterdog.waterdogpe.network.NetworkMetrics;
import dev.waterdog.waterdogpe.network.PacketDirection;
import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.network.connection.codec.BedrockBatchWrapper;
import dev.waterdog.waterdogpe.network.connection.codec.compression.SnappyCompressionCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.ReferenceCountUtil;
import lombok.AllArgsConstructor;
import org.cloudburstmc.protocol.common.util.Zlib;
import org.nethergames.proxytransport.utils.CompressionType;

import java.nio.ByteBuffer;
import java.util.List;

@AllArgsConstructor
public class ZstdCompressionCodec extends MessageToMessageCodec<ByteBuf, BedrockBatchWrapper> {
    public static final String NAME = "compression-codec";

    private static final SnappyCompressionCodec snappyCompressionCodec = new SnappyCompressionCodec();

    private final int compressionLevel;
    private final ClientConnection connection;

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) {
        CompositeByteBuf buf = ctx.alloc().compositeDirectBuffer(2);

        try {
            NetworkMetrics metrics = ctx.channel().attr(NetworkMetrics.ATTRIBUTE).get();
            PacketDirection direction = ctx.channel().attr(PacketDirection.ATTRIBUTE).get();

            // The batch is already compressed correctly, we can send the buffer straight to the server
            if (!msg.isModified() && msg.getCompressed() != null) {
                buf.addComponent(true, ctx.alloc().ioBuffer(1).writeByte(msg.getAlgorithm().getBedrockAlgorithm().ordinal()));
                buf.addComponent(true, msg.getCompressed().retainedSlice());

                if (metrics != null) {
                    metrics.passedThroughBytes(msg.getCompressed().readableBytes(), direction);
                }
            } else {
                // The batch was modified or the wrapper has no compressed data while still retaining
                // the uncompressed data.
                if ((msg.isModified() || msg.getCompressed() == null) && msg.getUncompressed() == null) {
                    throw new IllegalArgumentException("BedrockPacket is not encoded.");
                }

                msg.setCompressed(encode0(ctx, msg.getUncompressed()));

                buf.addComponent(true, ctx.alloc().ioBuffer(1).writeByte(CompressionType.METHOD_ZSTD.ordinal()));
                buf.addComponent(true, msg.getCompressed().retainedSlice());

                if (metrics != null) {
                    metrics.compressedBytes(msg.getCompressed().readableBytes(), direction);
                }
            }

            out.add(buf.retain());
        } catch (Throwable err) {
            ProxyServer.getInstance().getLogger().error("An exception were thrown while encoding packet", err);
        } finally {
            ReferenceCountUtil.release(buf);
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf compressed, List<Object> out) {
        BedrockBatchWrapper msg = BedrockBatchWrapper.newInstance(compressed.retain(), null);

        try {
            msg.setAlgorithm(connection.getPlayer().getCompression());
            switch (connection.getPlayer().getCompression().getBedrockAlgorithm()) {
                case ZLIB -> msg.setUncompressed(Zlib.RAW.inflate(msg.getCompressed().slice(), 1024 * 1024 * 12));
                case SNAPPY -> msg.setUncompressed(snappyCompressionCodec.decode0(ctx, msg.getCompressed().slice()));
            }

            if (msg.getUncompressed() == null) {
                throw new UnsupportedOperationException("The given compression algorithm is not supported by ProxyTransport");
            }

            NetworkMetrics metrics = ctx.channel().attr(NetworkMetrics.ATTRIBUTE).get();
            if (metrics != null) {
                PacketDirection direction = ctx.channel().attr(PacketDirection.ATTRIBUTE).get();
                metrics.decompressedBytes(msg.getUncompressed().readableBytes(), direction);
            }

            out.add(msg.retain());
        } catch (Throwable err) {
            ProxyServer.getInstance().getLogger().error("An exception were thrown while decoding packet", err);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private ByteBuf encode0(ChannelHandlerContext ctx, ByteBuf source) {
        ByteBuf direct;
        if (!source.isDirect() || source instanceof CompositeByteBuf) {
            direct = ctx.alloc().ioBuffer(source.readableBytes());
            direct.writeBytes(source);
        } else {
            direct = source;
        }

        ByteBuf output = ctx.alloc().directBuffer();
        try {
            int uncompressedLength = direct.readableBytes();
            int maxLength = (int) Zstd.compressBound(uncompressedLength);

            output.ensureWritable(maxLength);

            int compressedLength;
            if (direct.hasMemoryAddress()) {
                compressedLength = (int) Zstd.compressUnsafe(output.memoryAddress(), maxLength, direct.memoryAddress() + direct.readerIndex(), uncompressedLength, compressionLevel);
            } else {
                ByteBuffer sourceNio = direct.nioBuffer(direct.readerIndex(), direct.readableBytes());
                ByteBuffer targetNio = output.nioBuffer(0, maxLength);

                compressedLength = Zstd.compress(targetNio, sourceNio, compressionLevel);
            }

            output.writerIndex(compressedLength);
            return output.retain();
        } finally {
            ReferenceCountUtil.release(output);
            if (direct != source) {
                ReferenceCountUtil.release(direct);
            }
        }
    }
}
