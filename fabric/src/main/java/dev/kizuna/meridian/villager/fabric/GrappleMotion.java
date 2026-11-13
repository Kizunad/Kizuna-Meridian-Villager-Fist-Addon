package dev.kizuna.meridian.villager.fabric;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 擒和摔的位置计算：拉到身前、身后落点、过头弧线。只算位置和移动，不管锁和伤害。 */
final class GrappleMotion {
    private static final float HALF_TURN = 180;
    private static final double ARC_CONTROL_WEIGHT = 2;
    /** 二次贝塞尔中间项的系数。 */
    private static final double BEZIER_MIDDLE = 2;
    private static final double LANDING_STEP = 1;

    private GrappleMotion() {
        // 纯计算加一次瞬移。
    }

    static void pull(LivingEntity caster, LivingEntity target, double distance) {
        // 目标放到施放者正前方 distance 处，面朝施放者。
        Vec3 spot = caster.position().add(horizontalForward(caster).scale(distance));
        place(target, spot, caster.getYRot() + HALF_TURN);
    }

    static Vec3 landing(LivingEntity caster, LivingEntity target, double distance) {
        // 从施放者脚下朝身后一格一格找，被方块挡住就停在挡住之前的最后一格；第一格就挡住则落在脚边。
        Vec3 back = horizontalForward(caster).scale(-1);
        Vec3 last = caster.position();
        double step = LANDING_STEP;
        while (step <= distance) {
            Vec3 candidate = caster.position().add(back.scale(step));
            if (!fits(target, candidate)) {
                return last;
            }
            last = candidate;
            step += LANDING_STEP;
        }
        Vec3 full = caster.position().add(back.scale(distance));
        return fits(target, full) ? full : last;
    }

    static Vec3 arc(Arc arc, double progress) {
        // 二次贝塞尔：起点、施放者头顶上方的控制点、落点；控制点取 2×最高点减去两端平均高度，正中正好在最高点。
        double ease = Mth.clamp(progress, 0, 1);
        double ends = (arc.start().y + arc.end().y) / 2;
        Vec3 control = new Vec3(arc.over().x, ARC_CONTROL_WEIGHT * arc.apexY() - ends, arc.over().z);
        double rest = 1 - ease;
        Vec3 start = arc.start().scale(rest * rest);
        Vec3 middle = control.scale(BEZIER_MIDDLE * rest * ease);
        Vec3 end = arc.end().scale(ease * ease);
        return start.add(middle).add(end);
    }

    static void place(LivingEntity target, Vec3 spot, float yaw) {
        // 瞬移并清掉速度和摔落距离：玩家走服务端瞬移同步客户端，生物直接改位置。
        ServerLevel level = (ServerLevel) target.level();
        target.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, target.getXRot(), false /* setCamera */);
        target.setYHeadRot(yaw);
        target.setYBodyRot(yaw);
        target.setDeltaMovement(Vec3.ZERO);
        target.resetFallDistance();
    }

    private static boolean fits(LivingEntity target, Vec3 spot) {
        // 目标的碰撞箱放在这里不和方块重叠。
        return target.level().noCollision(target, target.getDimensions(target.getPose()).makeBoundingBox(spot));
    }

    private static Vec3 horizontalForward(LivingEntity caster) {
        // 施放者水平朝向，忽略俯仰。
        float yaw = caster.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** 一段抛物：起点、落点、经过的施放者位置和最高点高度。 */
    record Arc(Vec3 start, Vec3 end, Vec3 over, double apexY) {
    }
}
