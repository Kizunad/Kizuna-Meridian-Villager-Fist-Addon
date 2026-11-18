package dev.kizuna.meridian.humanoid.fabric;

import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 用同一式的施放目标、持物和身体时钟检查起手跟进的边界。 */
public final class FistWarmupPursuitGameTest {
    private static final int TIMEOUT = 220;
    private static final int CHASE_TIMEOUT = 100;
    private static final int MOVE_TICKS = 8;
    private static final int WRIST_FIRST_HIT = 18;
    private static final int WRIST_HOLD = 24;
    private static final int WRIST_RECOVERY = 55;
    private static final double DISTANT_TARGET = 10;
    private static final double MINIMUM_ADVANCE = 1.6;
    private static final double OUTSIDE_POST = 17;
    private static final double ORDINARY_SPEED = 0.5;
    private static final double TOLERANCE = 0.0001;
    private static final String CRUSHING = "meridian_villager:crushing_fist";
    private static final String OUTER = "meridian_villager:outer";

    @GameTest(maxTicks = TIMEOUT)
    public void warmupPursuitAdvancesOnArmedPillager(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 14);
        arena.whenReady(() -> {
            arena.targetAt(DISTANT_TARGET);
            arena.wake();
            FistXbowGuardGameTest.awaitChase(arena, CHASE_TIMEOUT, () -> {
                arena.targetAt(DISTANT_TARGET);
                arena.start(FistXbowTestArena.GRAPPLE);
                var start = arena.fighter.position();
                context.runAfterDelay(MOVE_TICKS, () -> {
                    try (arena) {
                        context.assertTrue(HumanoidTechniqueHost.beforeWarmupEnd(arena.fighter), "已过起手");
                        context.assertTrue(arena.fighter.getZ() - start.z > MINIMUM_ADVANCE, "持弩起手没有快步跟上");
                        context.succeed();
                    }
                });
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void warmupPursuitEndsOnDisarm(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 15);
        arena.whenReady(() -> {
            arena.targetAt(DISTANT_TARGET);
            arena.wake();
            FistXbowGuardGameTest.awaitChase(arena, CHASE_TIMEOUT, () -> {
                arena.start(FistXbowTestArena.LONG_STRIKE);
                var mind = following(arena.target);
                assertPursuing(arena, mind);
                arena.target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                context.assertTrue(HumanoidMeleeGuard.warmupPursuit(arena.fighter, mind).isEmpty(), "掉弩仍在快走");
                context.runAfterDelay(1, () -> {
                    try (arena) {
                        HumanoidBrain.move(arena.fighter, new HumanoidMotion.Speeds(ORDINARY_SPEED, 1));
                        var navigation = arena.fighter.getNavigation();
                        double speed = FistGuardSpeedGameTest.navigationSpeed(navigation);
                        context.assertTrue(Math.abs(speed - ORDINARY_SPEED) < TOLERANCE, "掉弩后旧导航仍在快走");
                        context.succeed();
                    }
                });
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void warmupPursuitEndsAtFirstStrikeAndHoldsGrapple(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 16);
        arena.whenReady(() -> {
            arena.wake();
            FistXbowGuardGameTest.awaitChase(arena, CHASE_TIMEOUT, () -> {
                arena.targetAt(1);
                arena.start(FistXbowTestArena.GRAPPLE);
                assertPursuing(arena, following(arena.target));
                context.runAfterDelay(WRIST_FIRST_HIT, () -> assertHolding(arena));
                context.runAfterDelay(WRIST_HOLD, () -> assertHolding(arena));
                context.runAfterDelay(WRIST_RECOVERY, () -> {
                    try (arena) {
                        context.assertTrue(!HumanoidTechniqueHost.beforeRecovery(arena.fighter), "未到收势");
                        context.assertTrue(HumanoidMeleeGuard.warmupPursuit(arena.fighter,
                                following(arena.target)).isEmpty(), "收势仍在快走");
                        context.succeed();
                    }
                });
            });
        });
    }

    private static void assertHolding(FistXbowTestArena arena) {
        var fighter = arena.fighter;
        arena.context.assertTrue(!HumanoidTechniqueHost.beforeWarmupEnd(fighter)
                && HumanoidTechniqueHost.beforeRecovery(fighter), "观测错过首击／擒持");
        arena.context.assertTrue(HumanoidMeleeGuard.warmupPursuit(fighter, following(arena.target)).isEmpty(),
                "首击／擒持仍在快走");
        HumanoidBrain.move(fighter, new HumanoidMotion.Speeds(ORDINARY_SPEED, 1));
        arena.context.assertTrue(fighter.getNavigation().isDone(), "首击／擒持仍在导航");
    }

    @GameTest(maxTicks = TIMEOUT)
    public void warmupPursuitExcludesMeleeOpponent(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 17);
        arena.whenReady(() -> {
            try (arena) {
                var foe = EntityTypes.VINDICATOR.create(context.getLevel(), EntitySpawnReason.COMMAND);
                foe.setNoAi(true /* aiDisabled */);
                foe.snapTo(arena.target.position(), 0, 0);
                context.assertTrue(context.getLevel().addFreshEntity(foe), "近战对手未入场");
                try {
                    foe.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                    arena.fighter.setOnGround(true /* onGround */);
                    arena.start(CRUSHING, foe);
                    var mind = following(foe);
                    context.assertTrue(HumanoidMeleeGuard.followsWarmup(arena.fighter, mind), "原小步跟进未生效");
                    assertExcluded(arena, mind, "对持斧对手快走");
                    foe.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                    assertExcluded(arena, mind, "对其他持弩物种快走");
                } finally {
                    foe.discard();
                }
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void warmupPursuitExcludesMortal(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 18, HumanoidTier.MORTAL, FistGuardSpeedGameTest.FIST_MASTER);
        context.runAfterDelay(FistXbowTestArena.READY_TICKS, () -> {
            try (arena) {
                HumanoidDuties.guardVillage(arena.fighter, true /* enabled */);
                HumanoidDuties.appointMeleeGuard(arena.fighter);
                arena.fighter.addTag("meridian_villager.fist_master_appointed");
                assertOrdinaryCast(arena, "凡人拳师的起手在快走");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void warmupPursuitExcludesUnappointedVillager(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 19, HumanoidTier.WANDERING_QI, VillagerProfession.NONE);
        context.runAfterDelay(FistXbowTestArena.READY_TICKS, () -> {
            try (arena) {
                HumanoidDuties.guardVillage(arena.fighter, true /* enabled */);
                HumanoidDuties.appointMeleeGuard(arena.fighter);
                assertOrdinaryCast(arena, "未任职村民的起手在快走");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void warmupPursuitRechecksPostTargetAndWithdrawal(GameTestHelper context) {
        var arena = new FistXbowTestArena(context, 20);
        arena.whenReady(() -> {
            try (arena) {
                arena.fighter.setOnGround(true /* onGround */);
                arena.start(CRUSHING);
                var mind = following(arena.target);
                assertPursuing(arena, mind);
                arena.fighter.getBrain().setMemory(MemoryModuleType.MEETING_POINT,
                        GlobalPos.of(context.getLevel().dimension(), arena.fighter.blockPosition()));
                arena.targetAt(OUTSIDE_POST);
                assertExcluded(arena, mind, "越过守区仍跟进");
                arena.targetAt(1);
                arena.fighter.addTag(HumanoidMeleeGuard.WITHDRAWN_TAG);
                assertExcluded(arena, mind, "撤离仍跟进");
                arena.fighter.removeTag(HumanoidMeleeGuard.WITHDRAWN_TAG);
                var other = EntityTypes.PILLAGER.create(context.getLevel(), EntitySpawnReason.COMMAND);
                other.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                other.snapTo(arena.target.position(), 0, 0);
                mind.engage(other);
                assertExcluded(arena, mind, "换到另一名持弩者仍跟进旧施放");
                context.succeed();
            }
        });
    }

    private static HumanoidMind following(LivingEntity target) {
        var mind = new HumanoidMind();
        mind.engage(target);
        mind.direct(HumanoidMotion.Directive.FOLLOWING);
        return mind;
    }

    private static void assertOrdinaryCast(FistXbowTestArena arena, String message) {
        var fighter = arena.fighter;
        fighter.setOnGround(true /* onGround */);
        TechniqueFabric.service().learn(fighter.getUUID(), OUTER);
        arena.start(CRUSHING);
        assertExcluded(arena, following(arena.target), message);
    }

    private static void assertExcluded(FistXbowTestArena arena, HumanoidMind mind, String message) {
        arena.context.assertTrue(HumanoidMeleeGuard.warmupPursuit(arena.fighter, mind).isEmpty(), message);
    }

    private static void assertPursuing(FistXbowTestArena arena, HumanoidMind mind) {
        arena.context.assertTrue(HumanoidMeleeGuard.warmupPursuit(arena.fighter, mind).isPresent(),
                "持弩对局没有进入快步起手");
    }
}
