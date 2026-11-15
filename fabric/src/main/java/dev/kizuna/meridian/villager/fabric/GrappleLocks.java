package dev.kizuna.meridian.villager.fabric;

import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.server.TechniqueService;
import dev.kizuna.meridian.technique.server.TechniqueService.Stagger;
import dev.kizuna.meridian.technique.server.TechniqueService.StaggerKind;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 被擒、倒地的锁。有功法宿主的（玩家、类人）写进功法库僵直，由各宿主锁；
 * 没有宿主的原版生物（僵尸这类）由本仓记一份，mixin 停掉它的大脑，再用移速修正压住移动。
 */
public final class GrappleLocks {
    /** 倒地还能爬：移速只剩 0.2。 */
    static final double DOWN_MOVEMENT = 0.2;
    private static final double HELD_MOVEMENT = 0;
    private static final long FOREVER = Long.MAX_VALUE;
    private static final Identifier HELD_SPEED = VillagerFistFabric.id("held");
    private static final Identifier DOWN_SPEED = VillagerFistFabric.id("downed");
    private static final Map<UUID, BodilessLock> bodiless = new HashMap<>();

    private GrappleLocks() {
        // 静态状态随服务器停止清空。
    }

    static void hold(LivingEntity target, long millis) {
        // 被擒：先写功法库僵直；写不进去说明没有宿主，改由本仓锁住，期限由放手决定。
        if (!stagger(target, new Stagger(millis, HELD_MOVEMENT, StaggerKind.HOLD)) && target instanceof Mob mob) {
            lockBodiless(mob, new BodilessLock(StaggerKind.HOLD, FOREVER));
        }
    }

    static void releaseHold(LivingEntity target) {
        // 放手：功法库只撤被擒这一种并发剩余 0；本仓锁的同样撤掉。
        TechniqueFabric.service().releaseStagger(target.getUUID(), StaggerKind.HOLD);
        BodilessLock lock = bodiless.get(target.getUUID());
        if (lock != null && lock.kind() == StaggerKind.HOLD && target instanceof Mob mob) {
            unlockBodiless(mob);
        }
    }

    static void down(LivingEntity target, long millis, long untilTick) {
        // 倒地：有宿主的照功法库僵直走，移速 ×0.2 由功法库执行；没有宿主的本仓锁到 untilTick。
        if (!stagger(target, new Stagger(millis, DOWN_MOVEMENT, StaggerKind.DOWN)) && target instanceof Mob mob) {
            lockBodiless(mob, new BodilessLock(StaggerKind.DOWN, untilTick));
        }
    }

    /** 本仓锁着的无宿主生物；mixin 据此跳过整个 serverAiStep。 */
    public static boolean lockedBodiless(Mob mob) {
        // 只看本仓的集合，宿主锁由各宿主自己管。
        return bodiless.containsKey(mob.getUUID());
    }

    /** 这个实体是不是被本仓锁成了倒地（没有功法宿主的原版生物被摔倒）；登记给 Threatening，生人的大脑据此认「对方倒地」。 */
    public static boolean downedBodiless(LivingEntity target) {
        BodilessLock lock = bodiless.get(target.getUUID());
        return lock != null && lock.kind() == StaggerKind.DOWN;
    }

    static void expire(long nowTick) {
        // 倒地到点起来；被擒没有期限，只随放手解除。
        bodiless.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().untilTick() <= nowTick;
            if (expired && ServerEntities.living(entry.getKey()) instanceof Mob mob) {
                removeSpeed(mob);
            }
            return expired;
        });
    }

    static void clear() {
        // 停服时丢掉本仓的锁；实体的临时修正不存档，不用另外撤。
        bodiless.clear();
    }

    private static boolean stagger(LivingEntity target, Stagger stagger) {
        // 功法库只给有宿主的实体记僵直；记上了就按种类查得到剩余时长。
        TechniqueService service = TechniqueFabric.service();
        service.stagger(target.getUUID(), stagger);
        return service.staggerRemainingMillis(target.getUUID(), stagger.kind()) > 0;
    }

    private static void lockBodiless(Mob mob, BodilessLock lock) {
        // 记下这把锁，停导航，再按种类加移速修正。
        bodiless.put(mob.getUUID(), lock);
        mob.getNavigation().stop();
        removeSpeed(mob);
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (lock.kind() == StaggerKind.HOLD) {
            speed.addTransientModifier(slow(HELD_SPEED, HELD_MOVEMENT));
        } else {
            speed.addTransientModifier(slow(DOWN_SPEED, DOWN_MOVEMENT));
        }
    }

    private static void unlockBodiless(Mob mob) {
        // 撤锁和移速修正，大脑下个 tick 恢复。
        bodiless.remove(mob.getUUID());
        removeSpeed(mob);
    }

    private static void removeSpeed(Mob mob) {
        // 两种修正都撤，换种类时不叠加。
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(HELD_SPEED);
            speed.removeModifier(DOWN_SPEED);
        }
    }

    private static AttributeModifier slow(Identifier id, double remaining) {
        // 乘总值：剩下 remaining 倍。
        return new AttributeModifier(id, remaining - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** 本仓锁的一种状态和到期 tick；被擒不到期。 */
    private record BodilessLock(StaggerKind kind, long untilTick) {
    }
}
