package dev.kizuna.meridian.villager.test.mixin;

import dev.kizuna.meridian.humanoid.fabric.FistDecisionTrace;
import dev.kizuna.meridian.humanoid.fabric.FistDecisionTrace.Gate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.kizuna.meridian.humanoid.server.MeleeGuardRules;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.kizuna.meridian.humanoid.fabric.HumanoidMeleeGuard", remap = false)
public abstract class FistMeasureReachMixin {
    @WrapOperation(method = "reaches", at = @At(value = "INVOKE",
            target = "Ldev/kizuna/meridian/humanoid/server/MeleeGuardRules;reaches("
                    + "Ldev/kizuna/meridian/humanoid/server/MeleeGuardRules$Motion;"
                    + "Ldev/kizuna/meridian/humanoid/server/MeleeGuardRules$Strike;)Z"))
    private static boolean predict(MeleeGuardRules.Motion motion, MeleeGuardRules.Strike strike,
                                   Operation<Boolean> original,
                                   @Local(argsOnly = true) TechniqueDefinition definition) {
        boolean reached = original.call(motion, strike);
        FistDecisionTrace.prediction(definition.getId(), motion, strike, reached ? Gate.PASSED : Gate.REJECTED);
        return reached;
    }
}
