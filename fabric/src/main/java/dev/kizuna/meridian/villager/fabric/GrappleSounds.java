package dev.kizuna.meridian.villager.fabric;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/** 背摔落地的声音：落地时刻由处理器决定，目录声音排不进去，所以在这里播。 */
final class GrappleSounds {
    private static final float VOLUME = 1.0F;
    private static final float PITCH = 1.0F;

    private GrappleSounds() {
        // 纯播放。
    }

    static void landing(LivingEntity target) {
        // 在落地的人身上播原版的重摔声。
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, VOLUME, PITCH);
    }
}
