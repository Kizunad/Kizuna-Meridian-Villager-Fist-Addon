package dev.kizuna.meridian.villager.fabric;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/** 擒的选人：先取施放声明的目标，再取正前方 60° 内最近的一个；不查盾，举着盾也抓得住。 */
final class GrappleTargets {
    private static final double HALF_CONE_DEGREES = 30;

    private GrappleTargets() {
        // 纯查询。
    }

    static Optional<LivingEntity> find(LivingEntity caster, String declaredId, double reach) {
        // 声明目标在够得着的距离内就用它，否则在前方扇形里找最近的。
        Optional<LivingEntity> declared = declared(caster, declaredId)
                .filter(target -> eligible(caster, target, reach));
        if (declared.isPresent()) {
            return declared;
        }
        return caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(reach),
                        target -> eligible(caster, target, reach) && inFront(caster, target))
                .stream()
                .min(Comparator.<LivingEntity>comparingDouble(caster::distanceToSqr)
                        .thenComparing(LivingEntity::getStringUUID));
    }

    private static Optional<LivingEntity> declared(LivingEntity caster, String declaredId) {
        // 声明目标是实体 UUID；空或解析不了就当没有。
        if (declaredId.isEmpty()) {
            return Optional.empty();
        }
        try {
            Entity entity = ((ServerLevel) caster.level()).getEntity(UUID.fromString(declaredId));
            return entity instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static boolean eligible(LivingEntity caster, LivingEntity target, double reach) {
        // 活着、不是自己、不是旁观者和盔甲架，而且在够得着的距离内。
        return target != caster
                && target.isAlive()
                && !target.isSpectator()
                && !(target instanceof ArmorStand)
                && caster.distanceTo(target) <= reach;
    }

    private static boolean inFront(LivingEntity caster, LivingEntity target) {
        // 水平朝向和指向目标的方向夹角不超过 30°。
        Vec3 toTarget = target.position().subtract(caster.position());
        double targetYaw = Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        return Math.abs(Mth.wrapDegrees(targetYaw - caster.getYRot())) <= HALF_CONE_DEGREES;
    }
}
