package dev.kizuna.meridian.humanoid.fabric;

import com.google.gson.JsonObject;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ActiveCast;
import dev.kizuna.meridian.technique.server.TechniqueScheduler;
import java.lang.reflect.Field;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.illager.Pillager;

/** 只读身体、决策与原版弩手状态；反射限定在测量夹具。 */
final class FistXbowFacts {
    private static final Field GOALS = field(Mob.class, "goalSelector");
    private static final Field CROSSBOW_STATE = field(RangedCrossbowAttackGoal.class, "crossbowState");
    private static final Field ATTACK_DELAY = field(RangedCrossbowAttackGoal.class, "attackDelay");
    private static final Field NAV_SPEED = field(PathNavigation.class, "speedModifier");

    private FistXbowFacts() { }

    static WrappedGoal crossbowGoal(Pillager pillager) {
        GoalSelector selector = (GoalSelector) read(GOALS, pillager);
        return selector.getAvailableGoals().stream()
                .filter(goal -> goal.getGoal() instanceof RangedCrossbowAttackGoal<?>)
                .findFirst().orElseThrow();
    }

    static void crossbow(JsonObject row, WrappedGoal goal, Pillager pillager) {
        var crossbowGoal = goal.getGoal();
        row.addProperty("xbow_state", read(CROSSBOW_STATE, crossbowGoal).toString());
        row.addProperty("xbow_delay", (Integer) read(ATTACK_DELAY, crossbowGoal));
        row.addProperty("xbow_goal_running", goal.isRunning());
        row.addProperty("xbow_using_ticks", pillager.getTicksUsingItem());
        row.addProperty("xbow_charging", pillager.isChargingCrossbow());
        row.addProperty("xbow_blocked", HumanoidBrain.blocksRanged(pillager));
    }

    static void entity(JsonObject row, String prefix, Mob mob) {
        boolean alive = mob.isAlive();
        var navigation = mob.getNavigation();
        var uuid = mob.getUUID();
        row.addProperty(prefix + "x", mob.getX());
        row.addProperty(prefix + "y", mob.getY());
        row.addProperty(prefix + "z", mob.getZ());
        row.addProperty(prefix + "alive", alive);
        row.addProperty(prefix + "mainhand", BuiltInRegistries.ITEM.getKey(mob.getMainHandItem().getItem()).toString());
        row.addProperty(prefix + "hp", mob.getHealth());
        row.addProperty(prefix + "opponent", HumanoidBrain.opponent(mob)
                .map(target -> target.getStringUUID()).orElse(""));
        row.addProperty(prefix + "on_ground", mob.onGround());
        row.addProperty(prefix + "horizontal_collision", mob.horizontalCollision);
        row.addProperty(prefix + "speed_attribute", mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        row.addProperty(prefix + "nav_done", navigation.isDone());
        row.addProperty(prefix + "nav_speed", (Double) read(NAV_SPEED, navigation));
        row.addProperty(prefix + "hurt_time", mob.hurtTime);
        row.addProperty(prefix + "stagger_ms", TechniqueFabric.service().staggerRemainingMillis(uuid));
        row.addProperty(prefix + "stance", HumanoidBrain.stance(uuid).map(Enum::name).orElse("none"));
        HumanoidBrain.directive(uuid).ifPresent(directive ->
                row.addProperty(prefix + "directive", directive.movement().name()));
        var decisions = HumanoidBrain.decisions(uuid);
        if (!decisions.isEmpty()) {
            var last = decisions.getLast();
            row.addProperty(prefix + "decision_tick", last.tick());
            row.addProperty(prefix + "action", last.decision().action().name());
            row.addProperty(prefix + "reason", last.decision().reason());
        }
        if (alive) {
            body(row, prefix, mob);
        }
    }

    private static void body(JsonObject row, String prefix, Mob mob) {
        var state = ((HumanoidEntityAccess) mob).meridian$humanoidBody();
        var body = state.currentBody();
        var progress = state.techniqueProgress();
        long now = HumanoidTechniqueHost.clockMillis(mob).orElse(body.settledAt().elapsedMillis());
        row.addProperty(prefix + "body_ms", now);
        row.addProperty(prefix + "body_hp_ratio", body.partHpRatio());
        row.addProperty(prefix + "weakest_part_ratio", HumanoidFighters.weakestPartHpRatio(body));
        row.addProperty(prefix + "arm_ratio", HumanoidFighters.strengthRatio(body, HumanoidFighters.ARM_MUSCLE));
        row.addProperty(prefix + "phase", "IDLE");
        row.addProperty(prefix + "last_failure", HumanoidBrain.lastFailure(mob.getUUID()));
        row.addProperty(prefix + "known", progress.getKnownList().stream()
                .map(known -> known.getTechnique().getId()).toList().toString());
        row.addProperty(prefix + "cooldowns", progress.getCooldownsMap().toString());
        if (progress.hasLastCast()) {
            var lastCast = progress.getLastCast();
            row.addProperty(prefix + "last_cast_id", lastCast.getCastId());
            row.addProperty(prefix + "last_ending", lastCast.getEnding().name());
        }
        if (progress.hasActiveCast()) {
            activeCast(row, prefix, progress.getActiveCast(), now);
        }
        row.addProperty(prefix + "hold_for_strike", HumanoidTechniqueHost.castingCloseStrike(mob)
                && HumanoidTechniqueHost.beforeRecovery(mob));
    }

    private static void activeCast(JsonObject row, String prefix, ActiveCast active, long now) {
        var request = active.getRequest();
        row.addProperty(prefix + "cast_id", request.getCastId());
        row.addProperty(prefix + "technique", request.getTechnique().getId());
        row.addProperty(prefix + "phase", TechniqueScheduler.phase(active, now).name());
        row.addProperty(prefix + "started_ms", active.getStartedMillis());
        row.addProperty(prefix + "warmup_end_ms", active.getWarmupEndMillis());
        row.addProperty(prefix + "active_end_ms", active.getActiveEndMillis());
        row.addProperty(prefix + "recovery_end_ms", active.getRecoveryEndMillis());
        row.addProperty(prefix + "tempo", active.getTempoMultiplier());
        row.addProperty(prefix + "clip_ms", active.getClipStartMillis());
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true /* accessible */);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
