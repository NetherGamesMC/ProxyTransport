# Proxy Transport

Proxy Transport is a TCP transport protocol implementation to replace the inefficient RakNet protocol implementation
between proxies and downstream servers.

From the [internal documentation:](https://outline.nethergames.org/share/d44f104f-8ad5-4ca3-a6f7-c8a0863d6b3e)

## Format

Packet frames have the following format:

- frameLength: int
- leadingByte: byte (compression indicator. 1 is Zlib, 2 is ZSTD)
- buffer: ByteBuf (the packets are formatted in the MCPE batch packet format)

## Dynamic Compression

The `leadingByte` (internally referred to as `compressionType`) gives us the ability to use faster compression methods
than zlib if possible.

### When Zstd is being used:

A client-sent packet has been altered by the rewrite protocol and the entire buffer has to be re-compressed. Since Zstd
has faster compression types, we use it here.

### When Zlib is being used:

A client-sent packet has not been altered, the original buffer is directly relayed to the downstream server to save us
the compression.

The downstream-server sends to the client. We use zlib so that we can send the initial buffer directly in case we do not
modify the packets.

