# Proxy Transport

Proxy Transport is a TCP & QUIC transport protocol implementation to replace the inefficient RakNet protocol implementation
between proxies and downstream servers.

## Format

Packet frames have the following format:

- frameLength: int
- buffer: ByteBuf (the packets are formatted in the MCPE batch packet format with compression type byte in front of it)

## Compression

ProxyTransport leverages different compression algorithms to improve bandwidth usage and CPU Usage.
Three compression algorithms are supported: Zlib, Snappy, and Zstd.

The compression byte has been extended with type `254` to support ZSTD as compression algorithm.

#### General rule
Packets are bi-directional. A packet can be sent from the client (or proxy) to the downstream server (Serverbound) or from the downstream server to the client (Clientbound).

Since the 1.19.30 update, the client can use both the Zlib and the Snappy compression, the proxy can dictate which one to use.
For clients < 1.19.30, zlib is the only option.

The following rules apply:

- Serverbound:
  - **Unrewritten** packet batches are unchanged. They use the compression that the proxy dictated to the client and may be recompressed if the compressions differ.
  - **Rewritten** packet batches are re-compressed using Zstd.
- Clientbound:
  - Packets have to be sent in the compression of the client from the downstream server, otherwise every packet batch will have to be recompressed.
    That is possible, however not desired since it will cause notable overhead.

The proxy receives and matches the NetworkSettingsPacket from the Downstream server to decide whether recompression is necessary.

For every session two values are maintained: the clientNativeCompressionAlgo and the serverNativeCompressionAlgo.

The clientNative algorithm is the algorithm that the client uses to send packets to the server. The server native algorithm is what the server uses to send to the client.