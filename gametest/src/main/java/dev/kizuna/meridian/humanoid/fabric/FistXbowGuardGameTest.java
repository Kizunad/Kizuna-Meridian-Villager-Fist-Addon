package dev.kizuna.meridian.humanoid.fabric;

import dev.kizuna.meridian.humanoid.server.HumanoidAssessment.Stance;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** 近身守卫的真实导航、首击与伤势撤离接线。 */
public final class FistXbowGuardGameTest {
    private static final int TIMEOUT = 300;
    private static final int DECISION_TICKS = 10;
    private static final int OBSERVE_TICKS = 12;
    private static final int EFFECTIVE_OBSERVE_TICKS = 10;
    private static final int HOLD_TICKS = 60;
    private static final int HIT_TIMEOUT = 200;
    private static final int CHASE_TIMEOUT_TICKS = 100;
    private static final int RESIDUAL_VELOCITY_SCENE = 21;
    private static final double NEAR_DISTANCE = 1.9;
    private static final double FOLLOW_DISTANCE = 2.8;
    private static final double APPROACH_DISTANCE = 6;
    private static final double ESCAPE_BLOCKS_PER_TICK = 0.3;
    private static final double GROUND_GRAVITY_BLOCKS_PER_TICK = -0.0784;
    private static final double MINIMUM_FOLLOW_BLOCKS = 0.1;
    private static final double MINIMUM_APPROACH_BLOCKS = 0.5;
    private static final double INJURED_PART = 0.2;
    private static final double OVERALL_HP = 0.94;
    private static final double TOLERANCE = 0.001;
    private static final double OUTSIDE_POST = 17;

    @GameTest(maxTicks = TIMEOUT)
    public void xbowGuardRejectsEscapingTargetBeforeLongStrike(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 1);
        arena.whenReady(() -> {
            try (arena) {
                arena.target.setNoAi(false /* aiDisabled */);
                arena.targetAt(NEAR_DISTANCE);
                arena.fighter.setDeltaMovement(Vec3.ZERO);
                arena.target.setDeltaMovement(new Vec3(0, 0, ESCAPE_BLOCKS_PER_TICK));
                var body = HumanoidTechniqueHost.prepare(arena.fighter).body().currentBody();
                var opponent = new HumanoidOpponent(arena.target, Optional.empty());
                var readout = HumanoidRepertoires.read(arena.fighter, body, Optional.of(opponent), id -> false);
                context.assertTrue(readout.strikes().stream().noneMatch(known ->
                        known.getTechnique().getId().equals(FistXbowTestArena.LONG_STRIKE)),
                        "持续外移的弩手仍触发长起手候选");
                arena.targetAt(1);
                arena.fighter.setOnGround(false /* onGround */);
                arena.fighter.setDeltaMovement(new Vec3(0, GROUND_GRAVITY_BLOCKS_PER_TICK, 0));
                var stationary = HumanoidRepertoires.read(arena.fighter, body, Optional.of(opponent), id -> false);
                context.assertTrue(stationary.strikes().stream().anyMatch(known ->
                        known.getTechnique().getId().equals(FistXbowTestArena.LONG_STRIKE)),
                        "静止近身目标也被错误过滤");
                context.assertTrue(stationary.strikes().stream().anyMatch(known ->
                        known.getTechnique().getId().equals(FistXbowTestArena.THROW)),
                        "地面重力残余错误过滤了静止目标的背摔候选");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void xbowGuardTreatsNoAiTargetResidualVelocityAsStationary(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, RESIDUAL_VELOCITY_SCENE);
        arena.whenReady(() -> {
            try (arena) {
                arena.target.setNoAi(true /* aiDisabled */);
                arena.targetAt(NEAR_DISTANCE);
                arena.target.setDeltaMovement(new Vec3(0, 0, ESCAPE_BLOCKS_PER_TICK));
                var body = HumanoidTechniqueHost.prepare(arena.fighter).body().currentBody();
                var opponent = new HumanoidOpponent(arena.target, Optional.empty());
                var readout = HumanoidRepertoires.read(arena.fighter, body, Optional.of(opponent), id -> false);
                context.assertTrue(readout.strikes().stream().anyMatch(known ->
                        known.getTechnique().getId().equals(FistXbowTestArena.LONG_STRIKE)),
                        "不会自主移动的弩手残留速度错误过滤长起手");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void xbowGuardFollowsDuringWarmupButHoldsGrapple(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 2);
        arena.whenReady(() -> {
            arena.wake();
            awaitChase(arena, CHASE_TIMEOUT_TICKS, () -> observeWarmup(arena));
        });
    }

    static void awaitChase(FistXbowTestArena arena, int remaining, Runnable action) {
        var context = arena.context;
        context.runAfterDelay(1, () -> {
            boolean following = HumanoidBrain.directive(arena.fighter.getUUID())
                    .filter(directive -> directive.movement() == HumanoidMotion.Movement.FOLLOW).isPresent();
            if (!following || !arena.fighter.onGround() || HumanoidTechniqueHost.hasActiveCast(arena.fighter)) {
                context.assertTrue(remaining > 0, "拳师尚未开始地面追击");
                awaitChase(arena, remaining - 1, action);
                return;
            }
            action.run();
        });
    }

    private static void observeWarmup(FistXbowTestArena arena) {
        var context = arena.context;
        arena.targetAt(FOLLOW_DISTANCE);
        arena.fighter.setDeltaMovement(Vec3.ZERO);
        arena.start(FistXbowTestArena.GRAPPLE);
        Vec3 before = arena.fighter.position();
        context.runAfterDelay(OBSERVE_TICKS, () -> {
            context.assertTrue(arena.fighter.position().distanceTo(before) > MINIMUM_FOLLOW_BLOCKS,
                    "拳师起手仍钉在原地" + FistMasterDutyGameTest.trail(arena.fighter));
            context.assertTrue(HumanoidTechniqueHost.beforeWarmupEnd(arena.fighter), "观测已错过起手");
            context.runAfterDelay(EFFECTIVE_OBSERVE_TICKS, () -> {
                try (arena) {
                    context.assertTrue(!HumanoidTechniqueHost.beforeWarmupEnd(arena.fighter)
                            && HumanoidTechniqueHost.beforeRecovery(arena.fighter), "未到擒持出手段");
                    context.assertTrue(arena.fighter.getNavigation().isDone(), "擒持出手段仍在追击");
                    context.succeed();
                }
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void xbowGuardApproachClosesDistanceAndLandsWithoutReload(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 3);
        arena.whenReady(() -> {
            arena.wake();
            awaitChase(arena, CHASE_TIMEOUT_TICKS, () -> {
                arena.targetAt(APPROACH_DISTANCE);
                double before = arena.fighter.distanceTo(arena.target);
                context.assertTrue(!arena.target.isUsingItem(), "弩手不应在装填");
                context.runAfterDelay(OBSERVE_TICKS, () -> {
                    context.assertTrue(before - arena.fighter.distanceTo(arena.target) > MINIMUM_APPROACH_BLOCKS,
                            "接敌期间距离没有缩短" + FistMasterDutyGameTest.trail(arena.fighter));
                    awaitHit(arena, HIT_TIMEOUT);
                });
            });
        });
    }

    private static void awaitHit(FistXbowTestArena arena, int remaining) {
        arena.context.runAfterDelay(1, () -> {
            if (arena.hits > 0) {
                arena.close();
                arena.context.succeed();
            } else {
                arena.context.assertTrue(remaining > 0,
                        "接敌后没有真实命中" + FistMasterDutyGameTest.trail(arena.fighter));
                awaitHit(arena, remaining - 1);
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void xbowGuardWoundedPartRetreatsDuringCastAndStaysAfterDisarm(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 5);
        arena.whenReady(() -> {
            arena.woundFoot();
            var body = HumanoidTechniqueHost.prepare(arena.fighter).body().currentBody();
            context.assertTrue(Math.abs(body.partHpRatio() - OVERALL_HP) < TOLERANCE, "夹具总体不是九成四");
            context.assertTrue(Math.abs(HumanoidFighters.weakestPartHpRatio(body) - INJURED_PART) < TOLERANCE,
                    "夹具最弱部位不是两成");
            arena.start(FistXbowTestArena.LONG_STRIKE);
            arena.wake();
            context.runAfterDelay(DECISION_TICKS, () -> {
                assertRetreat(arena);
                arena.target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                context.runAfterDelay(HOLD_TICKS, () -> {
                    try (arena) {
                        assertRetreat(arena);
                        context.succeed();
                    }
                });
            });
        });
    }

    private static void assertRetreat(FistXbowTestArena arena) {
        arena.context.assertTrue(HumanoidBrain.stance(arena.fighter.getUUID()).orElse(Stance.ENGAGE) == Stance.RETREAT,
                "重伤拳师没有保持撤离" + FistMasterDutyGameTest.trail(arena.fighter));
        arena.context.assertTrue(HumanoidDuties.guardsVillage(arena.fighter), "撤离摘掉了守村标签");
    }

    @GameTest(maxTicks = TIMEOUT)
    public void xbowGuardStopsAtBellBoundaryAndRetainsDutyOnReload(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 6);
        arena.whenReady(() -> {
            arena.fighter.getBrain().setMemory(MemoryModuleType.MEETING_POINT,
                    GlobalPos.of(context.getLevel().dimension(), arena.fighter.blockPosition()));
            arena.targetAt(OUTSIDE_POST);
            arena.target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            arena.wake();
            context.runAfterDelay(DECISION_TICKS, () -> {
                context.assertTrue(HumanoidBrain.opponent(arena.fighter).isEmpty(), "追出了钟边守区");
                arena.fighter.addTag(HumanoidMeleeGuard.WITHDRAWN_TAG);
                var reloaded = arena.reloadFighter();
                context.runAfterDelay(FistXbowTestArena.READY_TICKS, () -> {
                    context.assertTrue(HumanoidMeleeGuard.enabled(reloaded) && HumanoidMeleeGuard.withdrawn(reloaded),
                            "重载丢失策略或撤离状态");
                    reloaded.discard();
                    arena.close();
                    context.succeed();
                });
            });
        });
    }
}
