package dev.kizuna.meridian.villager.test.mixin;

import dev.kizuna.meridian.humanoid.fabric.FistDecisionTrace;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.kizuna.meridian.humanoid.fabric.HumanoidBrain", remap = false)
public abstract class FistMeasureDecisionMixin {
    @Inject(method = "followUpOpened", at = @At("RETURN"))
    private static void followUpOpenedObserved(Mob mob, @Coerce Object mind, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ()) {
            FistDecisionTrace.begin(mob, "follow_up");
        }
    }

    @Inject(method = "decisionDue", at = @At("RETURN"))
    private static void decisionDueObserved(Mob mob, @Coerce Object mind, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ()) {
            FistDecisionTrace.begin(mob, FistDecisionTrace.scheduled(mob, mind));
        }
    }

    @Inject(method = "opponentInStrikeDistance", at = @At("RETURN"))
    private static void enteredRange(Mob mob, @Coerce Object mind, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ()) {
            FistDecisionTrace.begin(mob, "strike_distance");
        }
    }

    @Inject(method = "decide", at = @At("RETURN"))
    private static void finish(Mob mob, @Coerce Object mind, boolean followUp, CallbackInfo callback) {
        FistDecisionTrace.end(mob);
    }
}
