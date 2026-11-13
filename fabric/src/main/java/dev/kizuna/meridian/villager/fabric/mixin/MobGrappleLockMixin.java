package dev.kizuna.meridian.villager.fabric.mixin;

import dev.kizuna.meridian.villager.fabric.GrappleLocks;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 没有功法宿主的原版生物被擒、倒地时停掉整个大脑：不走、不追、不打。 */
@Mixin(Mob.class)
abstract class MobGrappleLockMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void meridianVillager$locked(CallbackInfo callback) {
        // 本仓锁着时跳过目标、导航和移动控制，并清掉上一 tick 留下的移动输入。
        Mob mob = (Mob) (Object) this;
        if (!GrappleLocks.lockedBodiless(mob)) {
            return;
        }
        mob.xxa = 0;
        mob.yya = 0;
        mob.zza = 0;
        mob.setJumping(false /* jumping */);
        callback.cancel();
    }
}
