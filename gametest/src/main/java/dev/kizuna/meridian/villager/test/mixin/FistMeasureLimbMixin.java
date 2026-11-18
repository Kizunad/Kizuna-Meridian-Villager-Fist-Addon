package dev.kizuna.meridian.villager.test.mixin;

import dev.kizuna.meridian.humanoid.fabric.FistXbowMeasureGameTest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.EffectIntent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 仅在测量测试模组中观察实际拳脚执行。 */
@Mixin(targets = "dev.kizuna.meridian.humanoid.fabric.HumanoidTechniqueEffects", remap = false)
public abstract class FistMeasureLimbMixin {
    @Shadow @Final private LivingEntity entity;

    @Inject(method = "execute", at = @At("HEAD"))
    private void observeStrike(EffectIntent effect, CallbackInfo callback) {
        if (effect.hasLimbStrike()) {
            FistXbowMeasureGameTest.strike(entity.getUUID(), effect);
        }
    }
}
