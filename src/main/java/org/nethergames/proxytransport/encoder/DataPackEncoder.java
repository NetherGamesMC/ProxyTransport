package org.nethergames.proxytransport.encoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.nethergames.proxytransport.wrapper.DataPack;

public class DataPackEncoder extends MessageToByteEncoder<DataPack> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, DataPack prefixedDataPack, ByteBuf byteBuf) {
        try {
            byteBuf.writeByte(prefixedDataPack.getCompressionType().ordinal());
            byteBuf.writeBytes(prefixedDataPack.getContainingBuffer());
        } finally {
            prefixedDataPack.getContainingBuffer().release();
        }

    }
}
