package dev.kizuna.meridian.villager.fabric;

import dev.kizuna.meridian.humanoid.fabric.HumanoidTargets;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/** 袖手拳 26.2 Fabric 入口：木人桩、拳师职业、打木桩与擒摔处理器、创造栏与调试指令。 */
public final class VillagerFistFabric implements ModInitializer {
    public static final String MOD_ID = "meridian_villager";

    @Override
    public void onInitialize() {
        // 方块先于工作站点和职业注册：工作站点要列出木人桩的全部方块状态。
        VillagerFistBlocks.register();
        FistMasterProfession.register();
        FistMasterDuty.register();
        // 没有功法宿主的原版生物被摔倒是本仓记着的，登记给 Threatening，生人的大脑才看得见「对方倒地」。
        HumanoidTargets.registerDowned(GrappleLocks::downedBodiless);
        VillagerFistCreativeTab.register();
        ServerEntities.register();
        DummyDrills.register();
        Grapples.register();
        VillagerFistCommands.register();
    }

    /** 本仓命名空间下的身份。 */
    public static Identifier id(String path) {
        // 方块、职业、标签和创造栏都在 meridian_villager 命名空间下。
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
