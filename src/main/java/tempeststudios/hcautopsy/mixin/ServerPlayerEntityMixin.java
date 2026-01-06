package tempeststudios.hcautopsy.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tempeststudios.hcautopsy.HCAutopsy;

/**
 * Mixin to intercept player deaths on the server.
 * 
 * This hooks into onDeath() to detect when a player dies,
 * which triggers the wipe detection in HC Autopsy.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    /**
     * Inject at the head of onDeath to capture death details before any processing.
     */
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void hcautopsy$onDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Only process if HC Autopsy is initialized
        if (HCAutopsy.getRunManager() == null) {
            return;
        }

        // Get the death message
        Text deathMessage = damageSource.getDeathMessage(player);

        // Extract damage source info
        String damageSourceType = damageSource.getType().msgId();

        // Extract attacker info if present
        String attackerType = null;
        String attackerName = null;

        Entity attacker = damageSource.getAttacker();
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
