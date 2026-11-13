package dev.kizuna.meridian.villager.fabric;

import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.EffectIntent;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Grapple;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Throw;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ThrowLean;
import dev.kizuna.meridian.technique.server.EffectHandling;
import dev.kizuna.meridian.technique.server.TechniqueCastEnded;
import dev.kizuna.meridian.technique.server.TechniqueEffectEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 擒、摔处理器 {@code meridian_villager:grapple}，经功法库 {@code EFFECT_HANDLER} 认领。
 *
 * <p>擒：摔得动的目标每 tick 拉到施放者身前并锁住，到时或施放结束时放手；摔不动的排一靠。
 * 摔：把这个施放者正擒着的目标沿弧线抛过头顶，落在身后，落地打一下躯干再倒地。</p>
 */
public final class Grapples {
    public static final String PROCESSOR_ID = "meridian_villager:grapple";
    static final String CASTER_MISSING = "擒人的人不在当前服务器";
    private static final long MILLIS_PER_TICK = 50;
    private static final Map<UUID, Hold> holds = new HashMap<>();
    private static final Map<UUID, Flight> flights = new HashMap<>();
    private static final List<Lean> leans = new ArrayList<>();
    private static final Map<UUID, Long> immuneUntil = new HashMap<>();
    private static final Map<CastKey, Set<String>> handled = new HashMap<>();

    private Grapples() {
        // 状态按施放者、被擒者保存，随放手、落地和服务器停止释放。
    }

    static void register() {
        // 处理器只认本仓的处理器 ID；每 tick 推进拉人、飞行、一靠和倒地到期。
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> clear());
        ServerTickEvents.END_SERVER_TICK.register(Grapples::tick);
        TechniqueFabric.EFFECT_HANDLER.register(Grapples::handle);
        TechniqueFabric.CAST_ENDED.register(Grapples::release);
    }

    /** 这个实体是否正被擒着或在空中；测试和调试读。 */
    public static boolean held(UUID targetUuid) {
        // 擒住和抛出都算。
        return flights.containsKey(targetUuid)
                || holds.values().stream().anyMatch(hold -> hold.target().equals(targetUuid));
    }

    private static EffectHandling handle(TechniqueEffectEvent event) {
        // 只认本仓处理器的擒和摔；重投的同一事件只算一次。
        EffectIntent intent = event.intent();
        boolean grapple = intent.hasGrapple() && ours(intent.getGrapple().getGrapple().getProcessorId());
        boolean toss = intent.hasThrow() && ours(intent.getThrow().getThrow().getProcessorId());
        if (!grapple && !toss) {
            return EffectHandling.notApplicable();
        }
        CastKey key = new CastKey(event.entityUuid(), intent.getCastId());
        if (!handled.computeIfAbsent(key, ignored -> new HashSet<>()).add(intent.getEffectId())) {
            return EffectHandling.handled();
        }
        LivingEntity caster = ServerEntities.living(event.entityUuid());
        if (caster == null) {
            return EffectHandling.rejected(CASTER_MISSING);
        }
        if (grapple) {
            grab(caster, intent);
        } else {
            toss(caster, intent);
        }
        return EffectHandling.handled();
    }

    private static void grab(LivingEntity caster, EffectIntent intent) {
        // 擒空了这一式照常走完；摔不动的不擒，到点一靠；摔得动的锁住并当场拉到身前。
        Grapple grapple = intent.getGrapple().getGrapple();
        Optional<LivingEntity> found = GrappleTargets.find(caster, intent.getTargetId(),
                grapple.getMaximumDistance().getValue());
        if (found.isEmpty()) {
            return;
        }
        LivingEntity target = found.get();
        long now = now(caster);
        double base = intent.getGrapple().getArmRules().getBaseDamage().getValue();
        if (!Throwables.throwable(target)) {
            ThrowLean lean = grapple.getLean();
            GrappleStrike strike = GrappleStrike.of(intent, lean.getDamageTypeId(),
                    base * lean.getMultiplier().getValue());
            long at = now + ticks(lean.getDelayMillis());
            leans.add(new Lean(caster.getUUID(), target.getUUID(), at, strike, lean.getPush().getValue()));
            return;
        }
        long until = now + ticks(grapple.getDurationMillis());
        double distance = grapple.getHoldDistance().getValue();
        holds.put(caster.getUUID(), new Hold(intent.getCastId(), target.getUUID(), distance, until));
        GrappleLocks.hold(target, grapple.getDurationMillis());
        GrappleMotion.pull(caster, target, distance);
    }

    private static void toss(LivingEntity caster, EffectIntent intent) {
        // 只摔这个施放者正擒着的（缠腕接背摔不松手）；没擒住就什么也不做。擒的僵直到抛这一刻正好用完，
        // 空中这段重新锁上，落地时放开。
        Hold hold = holds.remove(caster.getUUID());
        LivingEntity target = hold == null ? null : ServerEntities.living(hold.target());
        if (target == null || target.level() != caster.level()) {
            return;
        }
        Throw toss = intent.getThrow().getThrow();
        double base = intent.getThrow().getArmRules().getBaseDamage().getValue();
        GrappleStrike strike = GrappleStrike.of(intent, toss.getDamageTypeId(),
                base * toss.getMultiplier().getValue());
        Vec3 end = GrappleMotion.landing(caster, target, toss.getLandingDistance().getValue());
        double apex = caster.getY() + caster.getBbHeight() + toss.getApexHeight().getValue();
        GrappleMotion.Arc arc = new GrappleMotion.Arc(target.position(), end, caster.position(), apex);
        long now = now(caster);
        Landing landing = new Landing(toss.getDownMillis(), toss.getDownImmunityMillis());
        flights.put(target.getUUID(), new Flight(caster.getUUID(), arc, now,
                now + ticks(toss.getFlightMillis()), strike, landing));
        GrappleLocks.hold(target, toss.getFlightMillis());
    }

    private static void tick(MinecraftServer server) {
        // 顺序：先推进飞行（落地会放开被擒），再拉住仍被擒的，再打到点的一靠，最后让倒地到期的起来。
        long now = server.getTickCount();
        flights.entrySet().removeIf(entry -> fly(entry.getKey(), entry.getValue(), now));
        holds.entrySet().removeIf(entry -> !keepHolding(entry.getKey(), entry.getValue(), now));
        leans.removeIf(lean -> lean.atTick() <= now && lean(lean));
        GrappleLocks.expire(now);
    }

    private static boolean fly(UUID targetUuid, Flight flight, long now) {
        // 返回 true 表示这段飞行已经结束：落地，或者人没了。
        LivingEntity target = ServerEntities.living(targetUuid);
        LivingEntity caster = ServerEntities.living(flight.caster());
        if (target == null || !target.isAlive()) {
            return true;
        }
        double span = Math.max(1, flight.endTick() - flight.startTick());
        double progress = (now - flight.startTick()) / span;
        GrappleMotion.place(target, GrappleMotion.arc(flight.arc(), progress), target.getYRot());
        if (now < flight.endTick()) {
            return false;
        }
        land(target, caster, flight, now);
        return true;
    }

    private static void land(LivingEntity target, LivingEntity caster, Flight flight, long now) {
        // 落地：放开被擒，打一下躯干，播摔落声；免摔期内只挨打不倒地，倒地后另起 3 s 免摔。
        GrappleLocks.releaseHold(target);
        if (caster != null) {
            flight.strike().hit(caster, target);
        }
        GrappleSounds.landing(target);
        Landing landing = flight.landing();
        if (immuneUntil.getOrDefault(target.getUUID(), Long.MIN_VALUE) > now) {
            return;
        }
        long downUntil = now + ticks(landing.downMillis());
        GrappleLocks.down(target, landing.downMillis(), downUntil);
        immuneUntil.put(target.getUUID(), downUntil + ticks(landing.immunityMillis()));
    }

    private static boolean keepHolding(UUID casterUuid, Hold hold, long now) {
        // 过了到点、任一方不在、跨了维度就放手；否则拉到身前。到点那一 tick 还算擒着：背摔的抛正好排在擒的末尾。
        LivingEntity caster = ServerEntities.living(casterUuid);
        LivingEntity target = ServerEntities.living(hold.target());
        boolean present = caster != null && target != null && caster.isAlive() && target.isAlive()
                && caster.level() == target.level();
        if (now > hold.untilTick() || !present) {
            if (target != null) {
                GrappleLocks.releaseHold(target);
            }
            return false;
        }
        GrappleMotion.pull(caster, target, hold.distance());
        return true;
    }

    private static boolean lean(Lean lean) {
        // 一靠：打一下、推开，施放者是玩家时动作栏提示摔不动；任一方不在就作罢。总是结束这条。
        LivingEntity caster = ServerEntities.living(lean.caster());
        LivingEntity target = ServerEntities.living(lean.target());
        if (caster == null || target == null || !target.isAlive()) {
            return true;
        }
        lean.strike().hit(caster, target);
        lean.strike().push(caster, target, lean.push());
        if (caster instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable("message.meridian_villager.grapple.too_heavy"));
        }
        return true;
    }

    private static void release(TechniqueCastEnded event) {
        // 施放结束（打完、被打断、被拒绝）：放开这一施放擒着的，撤掉还没到点的一靠；已经抛出去的照常落地。
        CastKey key = new CastKey(event.entityUuid(), event.castId());
        handled.remove(key);
        Hold hold = holds.get(event.entityUuid());
        if (hold != null && hold.castId().equals(event.castId())) {
            holds.remove(event.entityUuid());
            LivingEntity target = ServerEntities.living(hold.target());
            if (target != null) {
                GrappleLocks.releaseHold(target);
            }
        }
        leans.removeIf(lean -> lean.caster().equals(event.entityUuid()));
    }

    private static boolean ours(String processorId) {
        // 处理器 ID 精确匹配。
        return PROCESSOR_ID.equals(processorId);
    }

    private static long now(LivingEntity entity) {
        // 统一用服务器 tick 计时，各维度一致。
        return entity.level().getServer().getTickCount();
    }

    private static long ticks(long millis) {
        // 毫秒换 tick，向上取整，至少一个 tick。
        return Math.max(1, (millis + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK);
    }

    private static void clear() {
        // 停服时丢掉全部状态。
        holds.clear();
        flights.clear();
        leans.clear();
        immuneUntil.clear();
        handled.clear();
        GrappleLocks.clear();
    }

    /** 一个施放者擒着的人：哪次施放、拉到身前多远、到哪个 tick 放手。 */
    private record Hold(String castId, UUID target, double distance, long untilTick) {
    }

    /** 被抛在空中的人：谁抛的、弧线、起止 tick、落地的打击和倒地。 */
    private record Flight(UUID caster, GrappleMotion.Arc arc, long startTick, long endTick,
                          GrappleStrike strike, Landing landing) {
    }

    /** 落地后倒地多久、起来以后多久不能再摔倒。 */
    private record Landing(long downMillis, long immunityMillis) {
    }

    /** 摔不动时排好的一靠。 */
    private record Lean(UUID caster, UUID target, long atTick, GrappleStrike strike, double push) {
    }

    /** 一次施放：施放者 UUID 加施放 ID。 */
    private record CastKey(UUID entityUuid, String castId) {
    }
}
