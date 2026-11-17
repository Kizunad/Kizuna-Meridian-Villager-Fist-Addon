package dev.kizuna.meridian.villager.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 找施放者面前的桩。准星对着的优先：从眼睛沿视线最多走目录声明的距离，先撞到的那样东西是桩就用它。
 * 准星没对上桩时（站在木人桩跟前平视，视线会从桩顶上面过去），再找正前方够得着、中间没东西挡着的桩，不要求低头。
 * 方块桩认目录的 {@code block_tags}，实体桩认实体标签 {@code #meridian_villager:drill_targets}。
 */
final class DrillTargets {
    static final TagKey<EntityType<?>> ENTITY_TARGETS =
            TagKey.create(Registries.ENTITY_TYPE, VillagerFistFabric.id("drill_targets"));
    // 实体框沿视线外扩一点，免得视线擦着盔甲架边缘时漏判。
    private static final double ENTITY_PICK_MARGIN = 0.3;
    /** 「正前方」：桩的方向和面朝的方向在水平面上的夹角不超过这么多度。 */
    static final double FRONT_HALF_ANGLE_DEGREES = 30;
    private static final double BLOCK_CENTER = 0.5;
    /** 方块的外形可以高出自己那一格（木人桩高一格半），视线只认格子里的部分。 */
    private static final double CELL_HEIGHT = 1;

    /** 面前的一根候选桩：瞄它的哪一点，偏离正前方多少度，水平离多远。 */
    private record Facing(DrillTarget target, Vec3 aim, double degrees, double distance) { }

    private DrillTargets() {
        // 只有静态查找。
    }

    /** 施放者面前的桩：准星对着的优先，没对上再找正前方够得着的。 */
    static Optional<DrillTarget> find(LivingEntity caster, double reach, List<TagKey<Block>> blockTags) {
        // 两种找法都只在起式时跑一次。
        Optional<DrillTarget> aimed = aimedAt(caster, reach, blockTags);
        if (aimed.isPresent()) {
            return aimed;
        }
        return inFront(caster, reach, blockTags);
    }

    private static Optional<DrillTarget> aimedAt(LivingEntity caster, double reach, List<TagKey<Block>> blockTags) {
        // 先找方块，把实体查找截在方块命中点之前，这样桩后面的实体不会被隔墙认到。
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(reach));
        ServerLevel level = (ServerLevel) caster.level();
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        Vec3 limit = end;
        if (block.getType() == HitResult.Type.BLOCK) {
            limit = block.getLocation();
        }
        Optional<DrillTarget> entity = entityPost(caster, eye, limit);
        if (entity.isPresent() || block.getType() != HitResult.Type.BLOCK) {
            return entity;
        }
        return blockPost(level, block.getBlockPos(), blockTags);
    }

    private static Optional<DrillTarget> inFront(LivingEntity caster, double reach, List<TagKey<Block>> blockTags) {
        // 身体高度以内、水平距离够得着、偏离正前方不超过 30° 的桩里，取最正对的一根；一样正就取近的。
        ServerLevel level = (ServerLevel) caster.level();
        Vec3 eye = caster.getEyePosition();
        AABB body = caster.getBoundingBox();
        AABB search = new AABB(eye.x - reach, body.minY, eye.z - reach, eye.x + reach, eye.y, eye.z + reach);
        List<Facing> candidates = new ArrayList<>();
        BlockPos.betweenClosedStream(search).forEach(pos -> blockFacing(caster, level, pos.immutable(), blockTags)
                .ifPresent(candidates::add));
        for (Entity entity : level.getEntities(caster, search, DrillTargets::isEntityPost)) {
            AABB box = entity.getBoundingBox();
            Vec3 aim = new Vec3(box.getCenter().x, Mth.clamp(eye.y, box.minY, box.maxY), box.getCenter().z);
            candidates.add(facing(caster, new DrillTarget.EntityPost(entity), aim));
        }
        return candidates.stream()
                .filter(candidate -> candidate.degrees() <= FRONT_HALF_ANGLE_DEGREES && candidate.distance() <= reach)
                .filter(candidate -> unobstructed(caster, level, eye, candidate))
                .min(Comparator.comparingDouble(Facing::degrees).thenComparingDouble(Facing::distance))
                .map(Facing::target);
    }

    private static Optional<Facing> blockFacing(LivingEntity caster, ServerLevel level, BlockPos pos,
                                                List<TagKey<Block>> blockTags) {
        // 瞄桩身在自己那一格里的中点：木人桩高出格子的半截视线认不到。
        DrillTarget.BlockPost post = new DrillTarget.BlockPost(level, pos, blockTags);
        if (!post.present()) {
            return Optional.empty();
        }
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return Optional.empty();
        }
        AABB bounds = shape.bounds();
        double middle = (bounds.minY + Math.min(bounds.maxY, CELL_HEIGHT)) / 2;
        Vec3 aim = new Vec3(pos.getX() + BLOCK_CENTER, pos.getY() + middle, pos.getZ() + BLOCK_CENTER);
        return Optional.of(facing(caster, post, aim));
    }

    private static Facing facing(LivingEntity caster, DrillTarget target, Vec3 aim) {
        // 只看水平面：面朝的方向取身体的朝向角，不看抬头低头。
        Vec3 eye = caster.getEyePosition();
        Vec3 toward = new Vec3(aim.x - eye.x, 0, aim.z - eye.z);
        Vec3 forward = Vec3.directionFromRotation(0, caster.getYRot());
        double distance = toward.length();
        double degrees = 0;
        if (distance > 0) {
            degrees = Math.toDegrees(Math.acos(Mth.clamp(toward.normalize().dot(forward), -1, 1)));
        }
        return new Facing(target, aim, degrees, distance);
    }

    private static boolean unobstructed(LivingEntity caster, ServerLevel level, Vec3 eye, Facing candidate) {
        // 从眼睛到瞄点之间不能隔着别的方块：方块桩要先撞到它自己，实体桩要一路没撞到方块。
        BlockHitResult hit = level.clip(new ClipContext(eye, candidate.aim(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        if (candidate.target() instanceof DrillTarget.BlockPost post) {
            return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(post.pos());
        }
        return hit.getType() != HitResult.Type.BLOCK;
    }

    /** 目录里声明的方块标签。 */
    static List<TagKey<Block>> blockTags(List<String> ids) {
        // 标签 ID 由功法库启动时校验格式，这里直接解析。
        return ids.stream().map(id -> TagKey.create(Registries.BLOCK, Identifier.parse(id))).toList();
    }

    private static Optional<DrillTarget> entityPost(LivingEntity caster, Vec3 eye, Vec3 limit) {
        // 只认实体桩标签里的实体，施放者自己不算。
        AABB sweep = caster.getBoundingBox().expandTowards(limit.subtract(eye)).inflate(ENTITY_PICK_MARGIN);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, limit, sweep,
                DrillTargets::isEntityPost, eye.distanceToSqr(limit));
        if (hit == null) {
            return Optional.empty();
        }
        return Optional.of(new DrillTarget.EntityPost(hit.getEntity()));
    }

    private static boolean isEntityPost(Entity entity) {
        // 实体类型在桩标签里，并且还活着。
        return entity.isAlive() && entity.getType().builtInRegistryHolder().is(ENTITY_TARGETS);
    }

    private static Optional<DrillTarget> blockPost(ServerLevel level, BlockPos pos, List<TagKey<Block>> tags) {
        // 视线撞到的方块在任一桩标签里才算。
        DrillTarget.BlockPost post = new DrillTarget.BlockPost(level, pos.immutable(), tags);
        if (!post.present()) {
            return Optional.empty();
        }
        return Optional.of(post);
    }
}
