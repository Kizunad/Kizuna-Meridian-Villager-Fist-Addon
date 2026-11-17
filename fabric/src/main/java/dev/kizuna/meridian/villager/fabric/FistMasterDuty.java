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
 * 拳师的上任和撤任：村民成了拳师，就学上卷和中卷、保底开一条手经、把两臂练到位、挂上「不退」和「守村」；
 * 不再是拳师就撤掉「不退」和「守村」，开的脉和练出来的两臂留着。只撤本仓上任时挂的：别处给村民挂的职守不归这里管。
 *
 * <p>职守挂在 Threatening 的公开入口上，本仓只决定什么时候挂、数值是多少。凡人拳师不任职，只做买卖。</p>
 */
public final class FistMasterDuty {
    /** 上任时两臂肌肉的训练成长：游炁、真炁各一个数，和卫道士出生时的两臂成长相同。 */
    static final double WANDERING_QI_ARM_GROWTH = 0.20;
    static final double TRUE_QI_ARM_GROWTH = 0.35;
    private static final String OUTER_VOLUME = VillagerFistFabric.MOD_ID + ":outer";
    private static final String INNER_VOLUME = VillagerFistFabric.MOD_ID + ":inner";
    private static final String HAND_MERIDIAN = "human:hand_meridian";
    /** 本仓上任过的记号，随实体存档；撤任只认带着它的村民。 */
    static final String APPOINTED_TAG = VillagerFistFabric.MOD_ID + ".fist_master_appointed";
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
            dismiss(villager);
            return true;
        }
        Optional<HumanoidTier> tier = HumanoidTiers.of(villager);
        if (tier.isEmpty()) {
            return false;
        }
        if (tier.get() == HumanoidTier.MORTAL) {
            dismiss(villager);
            return true;
        }
        return appointed(villager);
    }

    private static void dismiss(Villager villager) {
        // 没被本仓上任过的不动：别的附属、命令给村民挂的「不退」「守村」照旧留着。
        if (villager.removeTag(APPOINTED_TAG)) {
            HumanoidDuties.standGround(villager, false /* enabled */);
            HumanoidDuties.guardVillage(villager, false /* enabled */);
            HumanoidDuties.dismissMeleeGuard(villager);
        }
    }

    private static boolean appointed(Villager villager) {
        // 学卷表平时已经发过上卷，这里再学一遍不变；运营方把学卷表里那一行删了，拳师也照样会。
        // 拳师传到中卷，接着学中卷（前置是上卷，所以排在后面）。
        TechniqueFabric.service().learn(villager.getUUID(), OUTER_VOLUME);
        TechniqueFabric.service().learn(villager.getUUID(), INNER_VOLUME);
        // 中卷是内家，要开着一条手经才使得出（不花真元）。游炁村民开脉少，有的一条手经都没开：
        // 当了拳师就保底开一条，不然他教的中卷自己使不出。上卷不靠它。
        if (!HumanoidDuties.ensureOpened(villager, HAND_MERIDIAN)
                || !HumanoidDuties.trainArms(villager, WANDERING_QI_ARM_GROWTH, TRUE_QI_ARM_GROWTH)) {
            return false;
        }
        HumanoidDuties.standGround(villager, true /* enabled */);
        HumanoidDuties.guardVillage(villager, true /* enabled */);
        HumanoidDuties.appointMeleeGuard(villager);
        villager.addTag(APPOINTED_TAG);
        return true;
    }
}
