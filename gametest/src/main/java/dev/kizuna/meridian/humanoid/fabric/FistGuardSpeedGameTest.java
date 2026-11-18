package dev.kizuna.meridian.humanoid.fabric;

import dev.kizuna.meridian.humanoid.server.HumanoidAssessment.Stance;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** 近身守卫接敌提速的身份、对手、施放和卸任边界。 */
public final class FistGuardSpeedGameTest {
    private static final int TIMEOUT = 200;
    private static final int SETTLE_TICKS = 30;
    private static final int MOVE_TICKS = 8;
    private static final int CHASE_TIMEOUT = 80;
    private static final int STABLE_CHASE_TICKS = 2;
    private static final double TARGET_DISTANCE = 14;
    private static final double MINIMUM_FAST_DISTANCE = 1.8;
    private static final double ORDINARY_APPROACH = 0.5;
    private static final double ORDINARY_FLEE = 0.75;
    private static final double RETURN_SPEED = 0.6;
    private static final double TOLERANCE = 0.0001;
    private static final int RETURN_START_Z = 22;
    private static final int BELL_Z = 2;
    private static final int LANE_X = 3;
    private static final HumanoidMotion.Speeds ORDINARY =
            new HumanoidMotion.Speeds(ORDINARY_APPROACH, ORDINARY_FLEE);
    private static final ResourceKey<VillagerProfession> FIST_MASTER = ResourceKey.create(
            Registries.VILLAGER_PROFESSION, Identifier.parse("meridian_villager:fist_master"));
    private static final Field NAV_SPEED = navigationSpeedField();

    @GameTest(maxTicks = TIMEOUT)
    public void guardSpeedChasesWithoutReloadAndAfterDisarm(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 7);
        arena.whenReady(() -> {
            arena.targetAt(TARGET_DISTANCE);
            arena.wake();
            awaitFastChase(arena, CHASE_TIMEOUT, 0);
        });
    }

    private static void awaitFastChase(FistXbowTestArena arena, int remaining, int stableTicks) {
        arena.context.runAfterDelay(1, () -> {
            int observedTicks = 0;
            if (chasingOnGround(arena)) {
                observedTicks = stableTicks + 1;
            }
            if (observedTicks < STABLE_CHASE_TICKS) {
                arena.context.assertTrue(remaining > 0, "拳师没有开始地面追击");
                awaitFastChase(arena, remaining - 1, observedTicks);
                return;
            }
            observeFastChase(arena);
        });
    }

    private static boolean chasingOnGround(FistXbowTestArena arena) {
        var fighter = arena.fighter;
        var uuid = fighter.getUUID();
        return fighter.onGround() && !fighter.getNavigation().isDone()
                && !HumanoidTechniqueHost.hasActiveCast(fighter)
                && HumanoidBrain.opponent(fighter).filter(target -> target == arena.target).isPresent()
                && HumanoidBrain.stance(uuid).filter(stance -> stance == Stance.ENGAGE).isPresent()
                && HumanoidBrain.directive(uuid)
                        .filter(directive -> directive.movement() == HumanoidMotion.Movement.FOLLOW).isPresent();
    }

    private static void observeFastChase(FistXbowTestArena arena) {
        assertSpeed(arena, navigationSpeed(arena.fighter.getNavigation()), configuredSpeed(), "接敌导航未提速");
        arena.target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        Vec3 start = arena.fighter.position();
        arena.context.runAfterDelay(MOVE_TICKS, () -> {
            try (arena) {
                arena.context.assertTrue(arena.fighter.position().distanceTo(start) > MINIMUM_FAST_DISTANCE,
                        "缴械后的追击实际位移仍是普通步速");
                assertSpeed(arena, navigationSpeed(arena.fighter.getNavigation()), configuredSpeed(),
                        "缴械后降回了普通追击速度");
                arena.context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void guardSpeedWithoutOpponentKeepsOrdinaryPace(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 8);
        arena.whenReady(() -> {
            try (arena) {
                var mind = chasing(arena);
                assertSpeed(arena, selected(arena, mind), configuredSpeed(), "有对手的夹具未提速");
                mind.disengage();
                assertSpeed(arena, selected(arena, mind), ORDINARY_APPROACH, "没有对手也在走快");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void guardSpeedReturningToBellKeepsOrdinaryPace(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 9);
        arena.whenReady(() -> {
            var fighter = arena.fighter;
            var brain = fighter.getBrain();
            var bell = arena.base.offset(LANE_X, 0, BELL_Z);
            brain.setMemory(MemoryModuleType.MEETING_POINT,
                    GlobalPos.of(context.getLevel().dimension(), bell));
            Vec3 position = Vec3.atBottomCenterOf(arena.base.offset(LANE_X, 0, RETURN_START_Z));
            fighter.snapTo(position.x, position.y, position.z, 0, 0);
            context.runAfterDelay(SETTLE_TICKS, () -> {
                try (arena) {
                    var mind = new HumanoidMind();
                    var directive = HumanoidMotion.Directive.moveTo(Vec3.atBottomCenterOf(bell),
                            HumanoidMotion.Pace.APPROACH);
                    mind.direct(directive);
                    assertSpeed(arena, selected(arena, mind), ORDINARY_APPROACH, "回防套用了接敌速度");
                    fighter.setOnGround(true /* onGround */);
                    HumanoidGuards.hold(brain, context.getLevel(), fighter);
                    var navigation = fighter.getNavigation();
                    context.assertTrue(!navigation.isDone(),
                            "回钟导航没有启动：位置=" + fighter.position() + "，钟=" + bell);
                    assertSpeed(arena, navigationSpeed(navigation), RETURN_SPEED, "回钟原步速变了");
                    context.succeed();
                }
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void guardSpeedMortalFistMasterKeepsOrdinaryPace(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 10, HumanoidTier.MORTAL, FIST_MASTER);
        context.runAfterDelay(SETTLE_TICKS, () -> {
            try (arena) {
                // 即使外部误挂策略，凡人档位仍必须拒绝提速。
                HumanoidDuties.guardVillage(arena.fighter, true /* enabled */);
                HumanoidDuties.appointMeleeGuard(arena.fighter);
                assertSpeed(arena, selected(arena, chasing(arena)), ORDINARY_APPROACH, "凡人拳师被提速");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void guardSpeedUnappointedVillagerKeepsOrdinaryPace(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 11, HumanoidTier.WANDERING_QI, VillagerProfession.NONE);
        context.runAfterDelay(SETTLE_TICKS, () -> {
            try (arena) {
                HumanoidDuties.guardVillage(arena.fighter, true /* enabled */);
                context.assertTrue(!HumanoidMeleeGuard.enabled(arena.fighter), "普通村民误登记成了近身守卫");
                assertSpeed(arena, selected(arena, chasing(arena)), ORDINARY_APPROACH, "未任职村民被提速");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void guardSpeedDismissalRestoresOrdinaryPace(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 12);
        arena.whenReady(() -> {
            var mind = chasing(arena);
            assertSpeed(arena, selected(arena, mind), configuredSpeed(), "卸任前未提速");
            arena.fighter.setVillagerData(arena.fighter.getVillagerData()
                    .withProfession(context.getLevel().registryAccess(), VillagerProfession.NONE));
            context.runAfterDelay(SETTLE_TICKS, () -> {
                try (arena) {
                    context.assertTrue(!HumanoidMeleeGuard.enabled(arena.fighter), "卸任未撤销近身守卫");
                    assertSpeed(arena, selected(arena, mind), ORDINARY_APPROACH, "卸任后仍在走快");
                    context.succeed();
                }
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void guardSpeedCastingAndWithdrawalKeepOrdinaryPace(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 13);
        arena.whenReady(() -> {
            try (arena) {
                var mind = chasing(arena);
                arena.fighter.addTag(HumanoidMeleeGuard.WITHDRAWN_TAG);
                assertSpeed(arena, selected(arena, mind), ORDINARY_APPROACH, "重伤撤离被接敌提速覆盖");
                arena.fighter.removeTag(HumanoidMeleeGuard.WITHDRAWN_TAG);
                arena.start(FistXbowTestArena.LONG_STRIKE);
                assertSpeed(arena, selected(arena, mind), ORDINARY_APPROACH, "施放中仍套用接敌提速");
                var speeds = HumanoidMeleeGuard.speeds(arena.fighter, mind, ORDINARY);
                assertSpeed(arena, speeds.flee(), ORDINARY_FLEE, "撤离导航倍率变了");
                context.succeed();
            }
        });
    }

    private static HumanoidMind chasing(FistXbowTestArena arena) {
        var mind = new HumanoidMind();
        mind.engage(arena.target);
        mind.direct(HumanoidMotion.Directive.FOLLOWING);
        return mind;
    }

    private static double selected(FistXbowTestArena arena, HumanoidMind mind) {
        return HumanoidMeleeGuard.speeds(arena.fighter, mind, ORDINARY).approach();
    }

    private static double configuredSpeed() {
        return HumanoidBrain.config().villager().guardApproachSpeed();
    }

    private static void assertSpeed(FistXbowTestArena arena, double actual, double expected, String message) {
        arena.context.assertTrue(Math.abs(actual - expected) < TOLERANCE,
                message + "：实际=" + actual + "，预期=" + expected);
    }

    private static Field navigationSpeedField() {
        try {
            Field field = PathNavigation.class.getDeclaredField("speedModifier");
            field.setAccessible(true /* accessible */);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static double navigationSpeed(PathNavigation navigation) {
        try {
            return NAV_SPEED.getDouble(navigation);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
