package org.nethergames.proxytransport.network.utils;

import com.nukkitx.network.VarInts;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.exception.PacketSerializeException;
import dev.waterdog.waterdogpe.ProxyServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.nethergames.proxytransport.network.TransportDownstreamSession;

import java.util.Collection;
import java.util.Iterator;

public class BedrockBatch {

    public static ByteBuf encodePackets(Collection<BedrockPacket> packets, TransportDownstreamSession session) {
        ByteBuf uncompressed = ByteBufAllocator.DEFAULT.ioBuffer(packets.size() << 3);
        try {
            Iterator<BedrockPacket> packetIterator = packets.iterator();

            while (packetIterator.hasNext()) {
                BedrockPacket packet = packetIterator.next();
                encode0(packet, session, uncompressed);
            }
        } catch (Throwable t) {
            ProxyServer.getInstance().getLogger().logException(t);
        }
        return uncompressed;
    }

    public static ByteBuf encodeSingle(BedrockPacket packet, TransportDownstreamSession session) {
        ByteBuf uncompressed = ByteBufAllocator.DEFAULT.ioBuffer(1 << 3);

        try {
            encode0(packet, session, uncompressed);
        } catch (Throwable t) {
            ProxyServer.getInstance().getLogger().logException(t);
        }
        return uncompressed;
    }

    private static void encode0(BedrockPacket packet, TransportDownstreamSession session, ByteBuf targetBuffer) {
        BedrockPacketCodec codec = session.getPlayer().getUpstream().getPacketCodec();
        ByteBuf packetBuffer = ByteBufAllocator.DEFAULT.ioBuffer();
        try {
            int id = codec.getId(packet);
            int header = 0;
            header = header | id & 1023;
            header |= (packet.getSenderId() & 3) << 10;
            header |= (packet.getClientId() & 3) << 12;

            VarInts.writeUnsignedInt(packetBuffer, header);
            codec.tryEncode(packetBuffer, packet, session.getPlayer().getUpstream());

            VarInts.writeUnsignedInt(targetBuffer, packetBuffer.readableBytes());
            targetBuffer.writeBytes(packetBuffer);
        } catch (PacketSerializeException var22) {
            session.getPlayer().getLogger().warning("Error occurred whilst encoding " + packet.getClass().getSimpleName(), var22);
        } finally {
            packetBuffer.release();
        }
    }

    /*
    private static void preallocatedWrite(ByteBuf buf, int value) {
        int previousBufferPosition = buf.writerIndex(); // Get the position where it starts the write

        VarInts.writeUnsignedInt(buf, value); // write the varint

        int written = (buf.writerIndex() - previousBufferPosition); // see how many bytes the VarInt took
        buf.readerIndex(previousBufferPosition); // reset the reader index to the beginning of the write
        for (int i = 0; i < written; i++) {
            if (buf.readByte() == 0) { // replace every 0 byte with 0x80, indicating that there is more to be read
                buf.writeByte(0x80); // Padding byte, indicates that there is more data to be read
            }
        }
    }*/
}
