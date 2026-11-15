package dev.kizuna.meridian.villager.fabric;

import dev.kizuna.meridian.humanoid.fabric.HumanoidDuties;
import dev.kizuna.meridian.humanoid.fabric.HumanoidTiers;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * 拳师的上任和撤任：村民成了拳师，就学上卷、保底开一条手经、把两臂练到位、挂上「不退」；
 * 不再是拳师就撤掉「不退」，开的脉和练出来的两臂留着。
 *
 * <p>职守挂在 Threatening 的公开入口上，本仓只决定什么时候挂、数值是多少。凡人拳师不任职，只做买卖。</p>
 */
public final class FistMasterDuty {
    /** 上任时两臂肌肉的训练成长：游炁、真炁各一个数，和卫道士出生时的两臂成长相同。 */
    static final double WANDERING_QI_ARM_GROWTH = 0.20;
    static final double TRUE_QI_ARM_GROWTH = 0.35;
    private static final String OUTER_VOLUME = VillagerFistFabric.MOD_ID + ":outer";
    private static final String HAND_MERIDIAN = "human:hand_meridian";
    /** 职业变过、或刚进世界，还没对过职守的村民；不持有实体，实体没了自然消失。 */
    private static final Set<Villager> UNSETTLED = Collections.newSetFromMap(new WeakHashMap<>());

    private FistMasterDuty() {
        // 只在模组初始化时接线一次。
    }

    static void register() {
        // 进世界的村民也对一遍：这样读档回来的、命令放出来的拳师都认。
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Villager villager) {
                UNSETTLED.add(villager);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> settle());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> UNSETTLED.clear());
    }

    /** 村民的职业或等级变了；下一个服务器 tick 重新对职守。 */
    public static void professionChanged(Villager villager) {
        if (!villager.level().isClientSide()) {
            UNSETTLED.add(villager);
        }
    }

    private static void settle() {
        // 逐个处理，没处理成的留到下一 tick；已经移除的丢掉。
        for (Villager villager : List.copyOf(UNSETTLED)) {
            if (villager.isRemoved() || settled(villager)) {
                UNSETTLED.remove(villager);
            }
        }
    }

    private static boolean settled(Villager villager) {
        // 还没进世界的先等着；不是拳师就撤任；是拳师就上任，身体这一刻读不到时下一 tick 再来。
        if (!(villager.level() instanceof ServerLevel level) || level.getEntity(villager.getUUID()) != villager) {
            return false;
        }
        if (!FistMasterProfession.is(villager)) {
            HumanoidDuties.standGround(villager, false);
            return true;
        }
        Optional<HumanoidTier> tier = HumanoidTiers.of(villager);
        if (tier.isEmpty()) {
            return false;
        }
        if (tier.get() == HumanoidTier.MORTAL) {
            HumanoidDuties.standGround(villager, false);
            return true;
        }
        return appointed(villager);
    }

    private static boolean appointed(Villager villager) {
        // 学卷表平时已经发过上卷，这里再学一遍不变；运营方把学卷表里那一行删了，拳师也照样会。
        // 中卷做出来以后在这里接着学。
        TechniqueFabric.service().learn(villager.getUUID(), OUTER_VOLUME);
        // 三式都要一条开了的手经；游炁村民开脉少，有的一条手经都没开，当了拳师就保底开一条，算他练过。
        if (!HumanoidDuties.ensureOpened(villager, HAND_MERIDIAN)
                || !HumanoidDuties.trainArms(villager, WANDERING_QI_ARM_GROWTH, TRUE_QI_ARM_GROWTH)) {
            return false;
        }
        HumanoidDuties.standGround(villager, true);
        return true;
    }
}
