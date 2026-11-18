package dev.kizuna.meridian.villager.test.mixin;

import dev.kizuna.meridian.humanoid.fabric.FistDecisionTrace;
import dev.kizuna.meridian.humanoid.fabric.FistDecisionTrace.Gate;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.KnownTechnique;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.server.TechniqueSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TechniqueSelector.class, remap = false)
public abstract class FistMeasureSelectorMixin {
    @Inject(method = "castable", at = @At("RETURN"))
    private static void castable(TechniqueSelector.Context context, KnownTechnique known,
                                 TechniqueDefinition definition, CallbackInfoReturnable<Boolean> result) {
        Gate gate = result.getReturnValueZ() ? Gate.PASSED : Gate.REJECTED;
        FistDecisionTrace.castable(context, definition, gate);
    }
}
