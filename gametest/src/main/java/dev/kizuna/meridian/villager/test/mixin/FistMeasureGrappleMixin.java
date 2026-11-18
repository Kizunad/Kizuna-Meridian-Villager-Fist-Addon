package dev.kizuna.meridian.villager.test.mixin;

import dev.kizuna.meridian.humanoid.fabric.FistXbowMeasureGameTest;
import dev.kizuna.meridian.technique.server.EffectHandling;
import dev.kizuna.meridian.technique.server.TechniqueEffectEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 仅在测量测试模组中观察擒摔处理器执行前的落点。 */
@Mixin(targets = "dev.kizuna.meridian.villager.fabric.Grapples", remap = false)
public abstract class FistMeasureGrappleMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private static void observeStrike(TechniqueEffectEvent event, CallbackInfoReturnable<EffectHandling> callback) {
        var effect = event.intent();
        if (effect.hasGrapple() || effect.hasThrow()) {
            FistXbowMeasureGameTest.strike(event.entityUuid(), effect);
        }
    }
}
