package tempeststudios.hcautopsy.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tempeststudios.hcautopsy.HCAutopsy;

/**
 * Mixin to intercept player deaths on the server.
 * 
 * This hooks into die() to detect when a player dies,
 * which triggers the wipe detection in HC Autopsy.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    /**
     * Inject at the head of die to capture death details before any processing.
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void hcautopsy$die(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        // Only process if HC Autopsy is initialized
        if (HCAutopsy.getRunManager() == null) {
            return;
        }

        // Get the death message
        Component deathMessage = damageSource.getLocalizedDeathMessage(player);

        // Extract damage source info
        String damageSourceType = damageSource.getMsgId();

        // Extract attacker info if present
        String attackerType = null;
        String attackerName = null;

        Entity attacker = damageSource.getEntity();
        if (attacker != null) {
            attackerType = attacker.getType().toString();
            attackerName = attacker.getName().getString();
        }

        // Notify the run manager
        boolean causedWipe = HCAutopsy.getRunManager().onPlayerDeath(
                player,
                deathMessage,
                damageSourceType,
                attackerType,
                attackerName
        );

        if (causedWipe) {
            HCAutopsy.LOGGER.info("Death of {} triggered world wipe", player.getName().getString());
        }
    }
}
