package net.bigyous.gptgod.client.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The plugin-message channels. Payloads are the raw bytes of the message (UTF-8 JSON), exactly what the
 * Paper plugin sends with Player#sendPluginMessage, so there is no length prefix.
 */
public final class Payloads {
    private Payloads() {
    }

    public static final Identifier MAIN_ID = Identifier.fromNamespaceAndPath("gptgod", "main");
    public static final Identifier HELLO_ID = Identifier.fromNamespaceAndPath("gptgod", "hello");

    public record Main(byte[] data) implements CustomPacketPayload {
        public static final Type<Main> TYPE = new Type<>(MAIN_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, Main> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBytes(p.data()), buf -> new Main(rest(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Hello(byte[] data) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(HELLO_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBytes(p.data()), buf -> new Hello(rest(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static byte[] rest(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readableBytes(), Protocol.MAX_BYTES + 1);
        byte[] b = new byte[n];
        buf.readBytes(b);
        buf.skipBytes(buf.readableBytes());
        return b;
    }
}
