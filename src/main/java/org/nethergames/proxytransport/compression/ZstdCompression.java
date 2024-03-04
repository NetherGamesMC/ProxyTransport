package org.nethergames.proxytransport.compression;

import com.github.luben.zstd.Zstd;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.BatchCompression;

import java.nio.ByteBuffer;

public class ZstdCompression implements BatchCompression {
    private int level = -1;

    public ByteBuf encode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        ByteBuf direct;
        if (!msg.isDirect() || msg instanceof CompositeByteBuf) {
            direct = ctx.alloc().ioBuffer(msg.readableBytes());
            direct.writeBytes(msg);
        } else {
            direct = msg;
        }

        ByteBuf output = ctx.alloc().directBuffer();
        try {
            int uncompressedLength = direct.readableBytes();
            int maxLength = (int) Zstd.compressBound(uncompressedLength);

            output.ensureWritable(maxLength);

            int compressedLength;
            if (direct.hasMemoryAddress()) {
                compressedLength = (int) Zstd.compressUnsafe(output.memoryAddress(), maxLength, direct.memoryAddress() + direct.readerIndex(), uncompressedLength, this.level);
            } else {
                ByteBuffer sourceNio = direct.nioBuffer(direct.readerIndex(), direct.readableBytes());
                ByteBuffer targetNio = output.nioBuffer(0, maxLength);

                compressedLength = Zstd.compress(targetNio, sourceNio, this.level);
            }

            output.writerIndex(compressedLength);
            return output.retain();
        } finally {
            ReferenceCountUtil.release(output);
            if (direct != msg) {
                ReferenceCountUtil.release(direct);
            }
        }
    }

    public ByteBuf decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
        throw new UnsupportedOperationException("Zstd is not supported");
    }

    public CompressionAlgorithm getAlgorithm() {
        return ProxyTransportAlgorithm.ZSTD;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
