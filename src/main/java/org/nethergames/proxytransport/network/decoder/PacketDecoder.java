package org.nethergames.proxytransport.network.decoder;

import com.nukkitx.network.VarInts;
import com.nukkitx.network.util.DisconnectReason;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.exception.PacketSerializeException;
import com.nukkitx.protocol.bedrock.packet.DisconnectPacket;
import com.nukkitx.protocol.bedrock.packet.NetworkStackLatencyPacket;
import com.nukkitx.protocol.util.Zlib;
import dev.waterdog.waterdogpe.ProxyServer;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import org.nethergames.proxytransport.network.TransportDownstreamSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.DataFormatException;

/**
 * This decoder handles the logic of receiving packets from the downstream server and passing them to the upstream client
 */
public class PacketDecoder extends SimpleChannelInboundHandler<ByteBuf> {

    private final static int MAX_BUFFER_SIZE = 4 * 1024 * 1024;

    private final TransportDownstreamSession session;

    public PacketDecoder(TransportDownstreamSession session) {
        this.session = session;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf compressed) {
        Collection<BedrockPacket> packets = new ArrayList<>();
        BedrockPacketCodec codec = this.session.getPlayer().getProtocol().getCodec();
        ByteBuf decompressed = null;
        try {
            compressed.markReaderIndex();
            decompressed = channelHandlerContext.alloc().buffer();
            Zlib.RAW.inflate(compressed, decompressed, MAX_BUFFER_SIZE);
            decompressed.markReaderIndex();

            if(skimByteBuf(codec, decompressed)){
                // What this does: "Skimming" the packet ids for rewritable ids makes it possible for us to
                // detect whether rewrites are necessary without decoding the entire thing first.
                this.session.getPlayer().getUpstream().sendWrapped(compressed, false);
                return;
            }


            while (decompressed.isReadable()) {
                int length = VarInts.readUnsignedInt(decompressed);
                ByteBuf packetBuffer = decompressed.readSlice(length);
                if (!packetBuffer.isReadable()) {
                    throw new DataFormatException("Packet cannot be empty");
                }

                try {
                    int header = VarInts.readUnsignedInt(packetBuffer);
                    int packetId = header & 1023;
                    BedrockPacket packet = codec.tryDecode(packetBuffer, packetId, this.session.getPlayer().getUpstream());
                    packet.setPacketId(packetId);
                    packet.setSenderId(header >>> 10 & 3);
                    packet.setClientId(header >>> 12 & 3);

                    if (packet instanceof NetworkStackLatencyPacket) {
                        if (((NetworkStackLatencyPacket) packet).getTimestamp() == 0) {
                            this.session.handleNetworkStackPacket();
                        }
                    }
                    packets.add(packet);
                } catch (PacketSerializeException serializeException) {
                    captureException(serializeException, this.session.getPlayer(), packetBuffer);

                    this.session.getPlayer().getLogger().error("Error while decoding a packet for " + this.session.getPlayer().getName(), serializeException);
                    this.session.getPlayer().getLogger().warning("Error occurred whilst decoding packet", serializeException);
                }
            }
            compressed.resetReaderIndex();

            this.session.getBatchHandler().handle(this.session.getPacketHandler(), compressed.retain(), packets);
        } catch (Throwable t) {

            captureException(t, session.getPlayer(), null);
            this.session.getPlayer().getLogger().error("Error while decoding a packet for " + this.session.getPlayer().getName(), t);

            throw new RuntimeException("Unable to inflate buffer data", t);
        } finally {
            ReferenceCountUtil.safeRelease(compressed);
            ReferenceCountUtil.safeRelease(decompressed);
        }
    }

    public boolean skimByteBuf(BedrockPacketCodec codec, ByteBuf buf){
        int readerIndex = buf.readerIndex();
        while(buf.isReadable()){
            int length = VarInts.readUnsignedInt(buf); // length
            int currentReaderIndex = buf.readerIndex();
            int packetId = VarInts.readUnsignedInt(buf) & 1023; // packet id
            int nextReaderIndex = buf.readerIndex();
            if(codec.getPacketDefinition(packetId) != null){
                buf.readerIndex(readerIndex);
                return true;
            }

            buf.skipBytes(length - (nextReaderIndex - currentReaderIndex)); // skip all the remaining bytes of this packet
        }

        return false;
    }

    private void captureException(Throwable t, ProxiedPlayer p, ByteBuf causingBuffer) {
        ProxiedPlayer player = this.session.getPlayer();
        SentryEvent event = new SentryEvent();
        this.session.attachCurrentTransaction(event);
        event.setThrowable(t);
        event.setExtra("dimensionSwitchState", player.getDimensionChangeState());
        event.setExtra("latency", player.getPing());
        if (causingBuffer != null) {
            event.setExtra("packetBuffer", ByteBufUtil.prettyHexDump(causingBuffer.readerIndex(0)));
        }


        Sentry.captureEvent(event);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        this.session.disconnect(DisconnectReason.CLOSED_BY_REMOTE_PEER);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        SentryEvent event = new SentryEvent();
        Message msg = new Message();
        msg.setMessage("Pipeline exception for player " + this.session.getPlayer().getName());
        event.setMessage(msg);
        event.setThrowable(cause);

        Sentry.captureEvent(event);

        this.session.getPlayer().getLogger().error("Pipeline threw exception for player " + this.session.getPlayer().getName(), cause);
        this.session.disconnect(DisconnectReason.BAD_PACKET);

        ProxyServer.getInstance().getLogger().logException(cause);
    }
}
