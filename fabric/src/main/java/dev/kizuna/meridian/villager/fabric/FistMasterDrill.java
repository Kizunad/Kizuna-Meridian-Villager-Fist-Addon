package dev.kizuna.meridian.villager.fabric;

import dev.kizuna.playeranimation.mobs.MobAnimationServer;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * 拳师的原版工作行为：站到木人桩前，照原版先播工作声、补货，然后循环打桩动作，每 0.5 秒一下木头受击声。
 * 只是表演，不训练、不记做功；大脑重建或被接手时原版会停掉它，动作跟着停。
 */
public final class FistMasterDrill extends WorkAtPoi {
    public static final String ANIMATION = "villager_dummy_drill";
    private static final int HIT_INTERVAL_TICKS = 10;
    // 一轮打 10 秒，比原版工作行为的 3 秒长，看得出是在打桩。
    private static final int DRILL_TICKS = 200;
    private static final float HIT_VOLUME = 0.6F;
    private static final float HIT_PITCH = 1.0F;
    private long startedAt;

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        // 原版起手：看向工作站点、播工作声、补货；之后接上循环打桩动作。
        super.start(level, villager, gameTime);
        startedAt = gameTime;
        MobAnimationServer.repeat(villager, ANIMATION);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        // 每 10 tick 在桩上响一下。
        if ((gameTime - startedAt) % HIT_INTERVAL_TICKS != 0) {
            return;
        }
        villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos)
                .ifPresent(pos -> level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.NEUTRAL,
                        HIT_VOLUME, HIT_PITCH));
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        // 打完、走开、大脑重建都会走到这里，停掉循环动作。
        MobAnimationServer.stop(villager, ANIMATION);
    }

    @Override
    protected boolean timedOut(long gameTime) {
        // 按本行为自己的一轮时长收工，不用原版的 3 秒。
        return gameTime - startedAt > DRILL_TICKS;
    }
}
