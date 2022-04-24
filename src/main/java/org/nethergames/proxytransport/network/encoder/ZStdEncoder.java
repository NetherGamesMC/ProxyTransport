package org.nethergames.proxytransport.network.encoder;

import com.github.luben.zstd.Zstd;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.nio.ByteBuffer;
import java.util.List;

@ChannelHandler.Sharable
public class ZStdEncoder extends MessageToMessageEncoder<ByteBuf> {

    public static ByteBuf compress(ByteBuf buf) {
        int decompressedSize = buf.readableBytes();
        int compressedSize = (int) Zstd.compressBound(decompressedSize);

        ByteBuf compressed = buf.alloc().directBuffer(compressedSize);

        if (buf.hasMemoryAddress()) {
            compressedSize = (int) Zstd.compressUnsafe(compressed.memoryAddress(), compressedSize, buf.memoryAddress() + buf.readerIndex(), decompressedSize, 3);
        } else {
            ByteBuffer compressedNio = compressed.nioBuffer(0, compressedSize);
            ByteBuffer decompressedNio = buf.nioBuffer(buf.readerIndex(), buf.readableBytes());
            compressedSize = Zstd.compress(compressedNio, decompressedNio, 3);
        }

        compressed.writerIndex(compressedSize);
        return compressed;
    }

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        list.add(compress(byteBuf));
    }
}