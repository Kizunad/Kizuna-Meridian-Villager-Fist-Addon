package dev.kizuna.meridian.villager.fabric;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** 摔得动的条件：碰撞箱高不过 2.2、宽不过 1.0，而且不在 {@code #meridian_villager:unthrowable} 里。 */
final class Throwables {
    static final TagKey<EntityType<?>> UNTHROWABLE = TagKey.create(Registries.ENTITY_TYPE,
            VillagerFistFabric.id("unthrowable"));
    private static final double MAX_HEIGHT = 2.2;
    private static final double MAX_WIDTH = 1.0;

    private Throwables() {
        // 纯判断。
    }

    static boolean throwable(LivingEntity target) {
        // 玩家、僵尸、骷髅、村民一类摔得动；铁傀儡、劫掠兽这类在标签里或者太大。
        return target.getBbHeight() <= MAX_HEIGHT
                && target.getBbWidth() <= MAX_WIDTH
                && !target.typeHolder().is(UNTHROWABLE);
    }
}
