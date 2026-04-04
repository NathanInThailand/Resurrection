package event_horizon.resurrection;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record S2CResurrectionAnimationPacket(ItemStack stack) implements CustomPacketPayload {
    public static final Type<S2CResurrectionAnimationPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("resurrection", "animation"));

    // Change ByteBuf to RegistryFriendlyByteBuf here
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CResurrectionAnimationPacket> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, S2CResurrectionAnimationPacket::stack,
            S2CResurrectionAnimationPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
