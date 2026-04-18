package event_horizon.resurrection;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = "resurrection")
public class OnResurrection {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                S2CResurrectionAnimationPacket.TYPE,
                S2CResurrectionAnimationPacket.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        Minecraft.getInstance().gameRenderer.displayItemActivation(payload.stack());
                    });
                }
        );
    }

    private static void sendResurrectionMessage(Player player, ItemStack item) {
//        Component itemName = item.getHoverName();
//        Component message = Component.translatable("message.resurrection.resurrected", itemName)
//                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFD700)).withBold(true));
//        player.displayClientMessage(message, true);
    }

    private static void triggerResurrectionEffects(Player player, ItemStack displayItem, Level level) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY(), player.getZ(), 150, 0.3, 0.8, 0.3, 0.05);
            serverLevel.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.5, player.getZ(), 80, 0.8, 0.5, 0.8, 0.02);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 2.0f, 1.0f);

        sendResurrectionMessage(player, displayItem);

        // Take a snapshot immediately and send without deferral —
        // the item is correct at this point and execute() was zeroing it out
        final ItemStack animationSnapshot = displayItem.copy();
        PacketDistributor.sendToPlayer(serverPlayer, new S2CResurrectionAnimationPacket(animationSnapshot));
    }

    @SubscribeEvent
    public static void onItemDestroyed(PlayerDestroyItemEvent event) {
        ItemStack original = event.getOriginal();
        Player player = event.getEntity();
        Level level = player.level();

        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var resurrectionKey = ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath("resurrection", "resurrection"));
        var resurrectionEnchant = registry.getOrThrow(resurrectionKey);
        int currentLevel = original.getEnchantmentLevel(resurrectionEnchant);

        if (currentLevel > 0) {
            ItemStack rescuedItem = original.copy();
            ItemEnchantments enchants = rescuedItem.get(DataComponents.ENCHANTMENTS);

            if (enchants != null) {
                ItemEnchantments.Mutable mutableEnchants = new ItemEnchantments.Mutable(enchants);
                mutableEnchants.set(resurrectionEnchant, Math.max(0, currentLevel - 1));
                rescuedItem.set(DataComponents.ENCHANTMENTS, mutableEnchants.toImmutable());
            }

            double remainingDurability = switch (currentLevel) {
                case 1 -> 0.05;
                case 2 -> 0.10;
                case 3 -> 0.15;
                default -> 0.05;
            };

            int rescueDamage = (int) (rescuedItem.getMaxDamage() * (1.0 - remainingDurability));
            rescuedItem.setDamageValue(rescueDamage);

            // Trigger effects before inventory add, using original for the
            // display name and animation — it's still valid at this point
            triggerResurrectionEffects(player, original, level);

            if (!player.getInventory().add(rescuedItem)) {
                player.drop(rescuedItem, false);
            }
        }
    }

    @SubscribeEvent
    public static void onArmorHurt(ArmorHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Level level = player.level();
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var resurrectionKey = ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath("resurrection", "resurrection"));
        var resurrectionEnchant = registry.getOrThrow(resurrectionKey);

        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {

            ItemStack armor = event.getArmorItemStack(slot);
            if (armor.isEmpty()) continue;

            int currentLevel = armor.getEnchantmentLevel(resurrectionEnchant);
            if (currentLevel <= 0) continue;

            float newDamage = event.getNewDamage(slot);
            if (armor.getDamageValue() + (int) newDamage < armor.getMaxDamage()) continue;

            event.setNewDamage(slot, 0f);

            ItemStack rescuedItem = armor.copy();
            ItemEnchantments enchants = rescuedItem.get(DataComponents.ENCHANTMENTS);

            if (enchants != null) {
                ItemEnchantments.Mutable mutableEnchants = new ItemEnchantments.Mutable(enchants);
                mutableEnchants.set(resurrectionEnchant, Math.max(0, currentLevel - 1));
                rescuedItem.set(DataComponents.ENCHANTMENTS, mutableEnchants.toImmutable());
            }

            double remainingDurability = switch (currentLevel) {
                case 1 -> 0.05;
                case 2 -> 0.10;
                case 3 -> 0.15;
                default -> 0.05;
            };

            int rescueDamage = (int) (rescuedItem.getMaxDamage() * (1.0 - remainingDurability));
            rescuedItem.setDamageValue(rescueDamage);

            player.setItemSlot(slot, rescuedItem);
            triggerResurrectionEffects(player, rescuedItem, level);
        }
    }
}