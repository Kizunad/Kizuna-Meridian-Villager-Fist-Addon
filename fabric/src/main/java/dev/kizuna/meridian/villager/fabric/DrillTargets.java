package dev.kizuna.meridian.villager.fabric;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
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

/**
 * 按视线找桩：从眼睛沿视线最多走目录声明的距离，先撞到的那样东西是桩才算。
 * 方块桩认目录的 {@code block_tags}，实体桩认实体标签 {@code #meridian_villager:drill_targets}。
 */
final class DrillTargets {
    static final TagKey<EntityType<?>> ENTITY_TARGETS =
            TagKey.create(Registries.ENTITY_TYPE, VillagerFistFabric.id("drill_targets"));
    // 实体框沿视线外扩一点，免得视线擦着盔甲架边缘时漏判。
    private static final double ENTITY_PICK_MARGIN = 0.3;

    private DrillTargets() {
        // 只有静态查找。
    }

    /** 施放者正对着的桩；视线先撞到墙或别的东西就没有。 */
    static Optional<DrillTarget> find(LivingEntity caster, double reach, List<TagKey<Block>> blockTags) {
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
