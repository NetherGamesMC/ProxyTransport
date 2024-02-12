package org.nethergames.proxytransport.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;

import java.util.List;

public class FrameIdCodec extends MessageToMessageCodec<ByteBuf, BedrockBatchWrapper> {
    public static final String NAME = "frame-id-codec";

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) throws Exception {
        out.add(msg.getCompressed().retain());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        out.add(BedrockBatchWrapper.newInstance(msg.readRetainedSlice(msg.readableBytes()), null));
    }
}
