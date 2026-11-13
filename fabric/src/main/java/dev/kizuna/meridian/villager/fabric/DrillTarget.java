package dev.kizuna.meridian.villager.fabric;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/** 一次打木桩锁定的桩：方块桩（木人桩、原木、干草块）或实体桩（盔甲架、玩家模型、训练假人）。 */
sealed interface DrillTarget permits DrillTarget.BlockPost, DrillTarget.EntityPost {
    /** 击打声比方块被挖时轻一些。 */
    float HIT_VOLUME = 0.6F;
    float ENTITY_HIT_PITCH = 1.0F;

    /** 桩还在原处。 */
    boolean present();

    /** 播这根桩的击打声，不伤桩。 */
    void playHit();

    /** 调试指令里显示的桩名。 */
    Component describe();

    /** 方块桩：位置固定，方块换成不在桩标签里的东西就算没了。 */
    record BlockPost(ServerLevel level, BlockPos pos, List<TagKey<Block>> tags) implements DrillTarget {
        @Override
        public boolean present() {
            // 任一声明的桩标签命中就还在。
            BlockState state = level.getBlockState(pos);
            return tags.stream().anyMatch(tag -> state.is(tag));
        }

        @Override
        public void playHit() {
            // 用方块自己的受击声：木头是 block.wood.hit，干草块是 block.grass.hit。
            SoundType sound = level.getBlockState(pos).getSoundType();
            level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, HIT_VOLUME, sound.getPitch());
        }

        @Override
        public Component describe() {
            // 方块名加坐标。
            Component name = level.getBlockState(pos).getBlock().getName();
            return Component.translatable("commands.meridian_villager.target.block", name,
                    pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /** 实体桩：被打掉、捡走或卸载就算没了。 */
    record EntityPost(Entity entity) implements DrillTarget {
        @Override
        public boolean present() {
            // 已移除或已死亡的实体都不算在。
            return entity.isAlive();
        }

        @Override
        public void playHit() {
            // 盔甲架、玩家模型、训练假人都按木头受击播。
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WOOD_HIT,
                    SoundSource.NEUTRAL, HIT_VOLUME, ENTITY_HIT_PITCH);
        }

        @Override
        public Component describe() {
            // 实体名。
            return Component.translatable("commands.meridian_villager.target.entity", entity.getDisplayName());
        }
    }
}
