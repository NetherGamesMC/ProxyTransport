package org.nethergames.proxytransport.compression;

import dev.waterdog.waterdogpe.network.connection.codec.compression.ProxiedCompressionCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;

import java.util.List;

public class ProxyTransportCompressionCodec extends ProxiedCompressionCodec {
    private final boolean prefixed;
    private final ZstdCompression zstdCompression = new ZstdCompression();

    public ProxyTransportCompressionCodec(CompressionStrategy strategy, boolean prefixed) {
        super(strategy, prefixed);
        this.prefixed = prefixed;
    }

    protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) throws Exception {
        if (msg.getCompressed() == null && msg.getUncompressed() == null) {
            throw new IllegalStateException("Batch was not encoded before");
        } else if (msg.getCompressed() != null && !msg.isModified()) { // already compressed
            if (this.prefixed) {
                this.onPassedThrough(ctx, msg);
                out.add(msg.retain());
            } else { // we need to prefix the compressed data
                CompositeByteBuf buf = ctx.alloc().compositeDirectBuffer(2);
                buf.addComponent(true, ctx.alloc().ioBuffer(1).writeByte(getCompressionHeader(msg.getAlgorithm())));
                buf.addComponent(true, msg.getCompressed().retainedSlice());

                this.onPassedThrough(ctx, msg);
                out.add(buf.retain());
            }
        } else {
            ByteBuf compressed = this.zstdCompression.encode(ctx, msg.getUncompressed());

            try {
                ByteBuf outBuf;

                outBuf = ctx.alloc().ioBuffer(1 + compressed.readableBytes());
                outBuf.writeByte(this.getCompressionHeader(this.zstdCompression.getAlgorithm()));
                outBuf.writeBytes(compressed);

                msg.setCompressed(outBuf, this.zstdCompression.getAlgorithm());
            } finally {
                compressed.release();
            }

            this.onCompressed(ctx, msg);
            out.add(msg.retain());
        }
    }

    protected byte getCompressionHeader0(CompressionAlgorithm algorithm) {
        if (algorithm instanceof ProxyTransportAlgorithm) {
            return -2;
        }

        return -1;
    }

    protected CompressionAlgorithm getCompressionAlgorithm0(byte header) {
        if (header == -2) {
            return ProxyTransportAlgorithm.ZSTD;
        }

        return null;
    }
}
