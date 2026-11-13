package dev.kizuna.meridian.villager.fabric.mixin;

import dev.kizuna.meridian.villager.fabric.FistMasterDrill;
import dev.kizuna.meridian.villager.fabric.FistMasterProfession;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 拳师的工作包里，原版的「到工作站点干活」换成在木人桩前打桩；别的职业不变。 */
@Mixin(VillagerGoalPackages.class)
abstract class VillagerGoalPackagesMixin {
    @ModifyVariable(method = "getWorkPackage", at = @At("LOAD"), name = "workAtPoi")
    private static WorkAtPoi meridianVillager$fistMasterDrill(WorkAtPoi original,
                                                             Holder<VillagerProfession> profession,
                                                             float speedModifier) {
        // 职业变化时原版会重建大脑并重新取工作包，所以按当时的职业换。
        if (profession.is(FistMasterProfession.KEY)) {
            return new FistMasterDrill();
        }
        return original;
    }
}
