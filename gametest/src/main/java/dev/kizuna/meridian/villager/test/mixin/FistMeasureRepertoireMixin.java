package dev.kizuna.meridian.villager.test.mixin;

import dev.kizuna.meridian.humanoid.fabric.FistDecisionTrace;
import dev.kizuna.meridian.humanoid.fabric.FistDecisionTrace.Gate;
import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueBinding;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.kizuna.meridian.humanoid.fabric.HumanoidRepertoires", remap = false)
public abstract class FistMeasureRepertoireMixin {
    @Inject(method = "payable", at = @At("RETURN"))
    private static void paid(BodySnapshot body, TechniqueDefinition definition, TechniqueBinding binding,
                             CallbackInfoReturnable<Boolean> result) {
        Gate gate = result.getReturnValueZ() ? Gate.PASSED : Gate.REJECTED;
        FistDecisionTrace.payable(definition, gate);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void ready(CallbackInfoReturnable<?> result) {
        FistDecisionTrace.readout(result.getReturnValue());
    }
}
