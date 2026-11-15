package dev.kizuna.meridian.villager.fabric.mixin;

import dev.kizuna.meridian.villager.fabric.FistMasterDuty;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 村民的职业、等级一变就让拳师的职守重新对一遍：成了拳师就上任，不再是拳师就撤任。 */
@Mixin(Villager.class)
abstract class VillagerProfessionMixin {
    @Inject(method = "setVillagerData", at = @At("TAIL"))
    private void meridianVillager$dutyFollowsProfession(VillagerData data, CallbackInfo callback) {
        // 这里只登记，下一个服务器 tick 再处理：读档时实体还没进世界，身体也可能还没就绪。
        FistMasterDuty.professionChanged((Villager) (Object) this);
    }
}
