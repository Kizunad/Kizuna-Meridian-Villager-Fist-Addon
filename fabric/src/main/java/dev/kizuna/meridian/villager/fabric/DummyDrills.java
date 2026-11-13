package dev.kizuna.meridian.villager.fabric;

import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.BlockWork;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.EffectIntent;
import dev.kizuna.meridian.technique.server.EffectHandling;
import dev.kizuna.meridian.technique.server.TechniqueCastEnded;
import dev.kizuna.meridian.technique.server.TechniqueEffectEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;

/**
 * 打木桩处理器 {@code meridian_villager:dummy_drill}，经功法库 {@code EFFECT_HANDLER} 认领。
 *
 * <p>一次施放的第一个木桩事件按视线锁桩，找不到就拒绝，功法库以 EFFECT_REJECTED 结束这次施放；
 * 之后每个事件查桩还在、播一下击打声，桩没了同样拒绝，施放就此中断。重投的同一事件只算一次。</p>
 */
public final class DummyDrills {
    public static final String PROCESSOR_ID = "meridian_villager:dummy_drill";
    static final String NO_TARGET = "要对着木桩打";
    static final String TARGET_GONE = "木桩没了";
    static final String CASTER_MISSING = "打木桩的人不在当前服务器";
    private static final Map<CastKey, DrillTarget> locked = new HashMap<>();
    private static final Map<CastKey, Set<String>> handled = new HashMap<>();

    private DummyDrills() {
        // 状态按施放保存，随施放结束和服务器停止释放。
    }

    static void register() {
        // 处理器只认本仓的处理器 ID，其余 block_work 交给别的附属。
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> clear());
        TechniqueFabric.EFFECT_HANDLER.register(DummyDrills::handle);
        TechniqueFabric.CAST_ENDED.register(DummyDrills::release);
    }

    private static EffectHandling handle(TechniqueEffectEvent event) {
        // 第一个事件锁桩，之后每个事件推进同一根桩。
        EffectIntent intent = event.intent();
        if (!intent.hasBlockWork() || !PROCESSOR_ID.equals(intent.getBlockWork().getProcessorId())) {
            return EffectHandling.notApplicable();
        }
        CastKey key = new CastKey(event.entityUuid(), intent.getCastId());
        if (handled.getOrDefault(key, Set.of()).contains(intent.getEffectId())) {
            return EffectHandling.handled();
        }
        LivingEntity caster = ServerEntities.living(event.entityUuid());
        if (caster == null) {
            return EffectHandling.rejected(CASTER_MISSING);
        }
        DrillTarget target = locked.get(key);
        EffectHandling result;
        if (target == null) {
            result = lock(key, caster, intent.getBlockWork());
        } else {
            result = strike(key, target);
        }
        if (result.accepted()) {
            handled.computeIfAbsent(key, ignored -> new HashSet<>()).add(intent.getEffectId());
        }
        return result;
    }

    private static EffectHandling lock(CastKey key, LivingEntity caster, BlockWork work) {
        // 视线里没有桩：拒绝起式，玩家另在动作栏看到提示。
        double reach = work.getMaximumDistance().getValue();
        List<TagKey<Block>> tags = DrillTargets.blockTags(work.getBlockTagsList());
        Optional<DrillTarget> target = DrillTargets.find(caster, reach, tags);
        if (target.isEmpty()) {
            if (caster instanceof ServerPlayer player) {
                player.sendOverlayMessage(Component.translatable("message.meridian_villager.dummy_drill.no_target"));
            }
            return EffectHandling.rejected(NO_TARGET);
        }
        locked.put(key, target.get());
        return EffectHandling.handled();
    }

    private static EffectHandling strike(CastKey key, DrillTarget target) {
        // 桩被挖掉、打掉或换掉就中断；还在就播一下击打声。
        if (!target.present()) {
            locked.remove(key);
            return EffectHandling.rejected(TARGET_GONE);
        }
        target.playHit();
        return EffectHandling.handled();
    }

    private static void release(TechniqueCastEnded event) {
        // 施放结束（打完、被打断、被拒绝）都释放这根桩。
        CastKey key = new CastKey(event.entityUuid(), event.castId());
        locked.remove(key);
        handled.remove(key);
    }

    private static void clear() {
        // 服务器停止时丢掉所有施放状态。
        locked.clear();
        handled.clear();
    }

    /** 一次施放：施放者 UUID 加施放 ID。 */
    private record CastKey(UUID entityUuid, String castId) {
    }
}
