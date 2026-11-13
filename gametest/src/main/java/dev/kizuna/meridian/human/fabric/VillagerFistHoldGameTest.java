package dev.kizuna.meridian.human.fabric;

import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.CENTER;
import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.HEIGHT;

import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.server.TechniqueService.Stagger;
import dev.kizuna.meridian.technique.server.TechniqueService.StaggerKind;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 玩家挨擒、倒地：被擒的拉到身前、举着盾也擒得住、正在起手的被打断、不能动不能出手；施放者掉线就放手。
 * 举盾和起手分两条：起式本身会放下手里的东西，放在一条里分不出盾是谁放下的。
 * 倒地的玩家趴着爬，移速只剩 ×0.2，不能跳、不能施放；到点起来，一切照旧。
 */
public final class VillagerFistHoldGameTest {
    private static final String THROW = VillagerFistTestPlayer.PREFIX + "shoulder_throw";
    private static final String LIFT = VillagerFistTestPlayer.PREFIX + "golem_lift";
    private static final String TARGET_NAME = "fist_target";
    private static final float FACING_NORTH = 180;
    /** 背摔 1.20 擒住；被擒的玩家在 0.50 起撩天，起手 1.90，擒住时还在起手。 */
    private static final int GRAB_TICKS = 24;
    private static final int TARGET_CAST_TICKS = 10;
    private static final int HELD_CHECK_TICKS = 28;
    private static final int CASTER_LEAVES_TICKS = 30;
    private static final int RELEASED_CHECK_TICKS = 32;
    private static final int HOLD_TIMEOUT_TICKS = 80;
    private static final long DOWN_MILLIS = 1_500;
    private static final double DOWN_MOVEMENT = 0.2;
    private static final int DOWN_CHECK_TICKS = 5;
    private static final int UP_CHECK_TICKS = 40;
    private static final int DOWN_TIMEOUT_TICKS = 80;
    /** 站在 1.6 格外，擒住后应被拉到 1 格。 */
    private static final double START_DISTANCE = 1.6;
    private static final double HOLD_DISTANCE = 1;
    private static final double POSITION_TOLERANCE = 0.2;
    private static final double RATIO_TOLERANCE = 0.0001;
    private static final double HALF = 0.5;

    @GameTest(maxTicks = HOLD_TIMEOUT_TICKS)
    public void shieldedPlayerIsHeldAndReleasedWhenTheCasterLeaves(GameTestHelper context) {
        // 守方左手举盾站着不动：擒之前一 tick 盾还举着，1.20 擒住后盾放下；其余同下一条。
        heldPlayer(context, true /* shielded */);
    }

    @GameTest(maxTicks = HOLD_TIMEOUT_TICKS)
    public void heldPlayerIsPulledInFrontAndItsWarmupIsInterrupted(GameTestHelper context) {
        // 守方 0.50 起撩天，1.20 被擒时还在起手，被打断。起式本身会放下手里的东西，所以这一条不举盾。
        heldPlayer(context, false /* shielded */);
    }

    @GameTest(maxTicks = DOWN_TIMEOUT_TICKS)
    public void downedPlayerCrawlsAtAFifthOfTheSpeedAndCannotCast(GameTestHelper context) {
        // 功法库给玩家记 1.5 s 倒地（背摔落地对有宿主的目标就是这一笔）：趴下、移速 ×0.2、不能跳、起式被拒；
        // 到点起来，姿势、移速、门禁都恢复。
        var fixture = new VillagerFistTestPlayer(context);
        ServerPlayer player = fixture.player;
        double baseSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        long startTick = fixture.tick();
        Map<Long, Downed> samples = new HashMap<>();
        fixture.everyTick(() -> {
            long offset = fixture.tick() - startTick;
            samples.put(offset, Downed.of(fixture, baseSpeed, offset == DOWN_CHECK_TICKS));
        });
        TechniqueFabric.service().stagger(player.getUUID(), new Stagger(DOWN_MILLIS, DOWN_MOVEMENT, StaggerKind.DOWN));
        context.runAfterDelay(UP_CHECK_TICKS + 1, () -> {
            try (fixture) {
                Downed down = samples.get((long) DOWN_CHECK_TICKS);
                context.assertTrue(down != null && down.pose() == Pose.SWIMMING, "倒地没有趴下：" + down);
                context.assertTrue(Math.abs(down.speedRatio() - DOWN_MOVEMENT) < RATIO_TOLERANCE,
                        "倒地移速是原来的 " + down.speedRatio() + " 倍，应为 " + DOWN_MOVEMENT);
                context.assertTrue(down.jump() == 0 && down.locked() && !down.castStarted(),
                        "倒地还能跳、出手或施放：" + down);
                Downed up = samples.get((long) UP_CHECK_TICKS);
                context.assertTrue(up != null && up.pose() == Pose.STANDING && !up.locked(), "起来以后没恢复：" + up);
                context.assertTrue(Math.abs(up.speedRatio() - 1) < RATIO_TOLERANCE, "起来以后移速没恢复：" + up);
                context.succeed();
            }
        });
    }

    private static void heldPlayer(GameTestHelper context, boolean shielded) {
        // 守方站在 1.6 格外。1.20 被擒：拉到 1 格、门禁锁上、移速和跳归零、再起式被拒；
        // 施放者 1.50 掉线后放手：功法库发剩余 0，Human 的门禁和移速随之撤掉。
        var caster = new VillagerFistTestPlayer(context);
        Vec3 feet = new Vec3(CENTER + HALF, HEIGHT, CENTER + HALF + START_DISTANCE);
        var target = new VillagerFistTestPlayer(context, TARGET_NAME, feet, FACING_NORTH);
        if (shielded) {
            target.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            target.player.startUsingItem(InteractionHand.OFF_HAND);
        }
        double baseSpeed = target.player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        long castTick = caster.tick();
        Map<Long, Held> samples = new HashMap<>();
        Map<Long, CastPhase> liftPhase = new HashMap<>();
        target.everyTick(() -> {
            long offset = target.tick() - castTick;
            if (!shielded && offset == TARGET_CAST_TICKS) {
                liftPhase.put(offset, target.cast(LIFT).getPhase());
            }
            if (offset == CASTER_LEAVES_TICKS) {
                caster.close();
            }
            samples.put(offset, Held.of(context, target, offset == HELD_CHECK_TICKS));
        });
        caster.start(THROW, "背摔");
        context.runAfterDelay(RELEASED_CHECK_TICKS + 1, () -> {
            try (target) {
                if (shielded) {
                    assertShieldLowered(context, samples);
                } else {
                    assertWarmupInterrupted(context, liftPhase, target);
                }
                assertHeld(context, samples.get((long) HELD_CHECK_TICKS));
                assertReleased(context, samples.get((long) RELEASED_CHECK_TICKS), baseSpeed);
                context.succeed();
            }
        });
    }

    private static void assertShieldLowered(GameTestHelper context, Map<Long, Held> samples) {
        // 擒之前一 tick 盾还举着，擒住后放下：放下盾的是擒，举着盾也擒得住。
        Held before = samples.get((long) GRAB_TICKS - 1);
        Held held = samples.get((long) HELD_CHECK_TICKS);
        context.assertTrue(before != null && before.usingItem(), "擒住之前盾就放下了：" + before);
        context.assertTrue(held != null && !held.usingItem(), "被擒时盾还举着：" + held);
    }

    private static void assertWarmupInterrupted(GameTestHelper context, Map<Long, CastPhase> liftPhase,
                                                VillagerFistTestPlayer target) {
        // 撩天 0.50 起式、起手 1.90，本该 2.40 才出手；放手前就结束了，说明被擒时打断了起手。
        context.assertTrue(liftPhase.get((long) TARGET_CAST_TICKS) == CastPhase.WARMUP, "守方撩天没起手");
        context.assertTrue(target.ended(LIFT).isPresent(), "被擒时正在起手的撩天没有被打断");
    }

    private static void assertHeld(GameTestHelper context, Held held) {
        // 被擒期间：身前 1 格、门禁锁上、移速和跳归零、功法库记着被擒、起式被拒。
        Vec3 expected = new Vec3(CENTER + HALF, HEIGHT, CENTER + HALF + HOLD_DISTANCE);
        context.assertTrue(held != null && held.position().distanceTo(expected) < POSITION_TOLERANCE,
                "被擒的玩家在 " + held + "，应在身前 1 格 " + expected);
        context.assertTrue(held.locked() && held.speed() == 0 && held.jump() == 0, "被擒还能动或出手：" + held);
        context.assertTrue(held.holdRemaining() > 0 && !held.castStarted(),
                "功法库没记被擒，或者被擒还能起式：" + held);
    }

    private static void assertReleased(GameTestHelper context, Held released, double baseSpeed) {
        // 施放者掉线后：被擒剩余为 0，门禁撤掉，移速回到原值。
        context.assertTrue(released != null && released.holdRemaining() == 0 && !released.locked(),
                "施放者掉线后没有放手：" + released);
        context.assertTrue(Math.abs(released.speed() - baseSpeed) < RATIO_TOLERANCE,
                "放手后移速 " + released.speed() + "，应回到 " + baseSpeed);
    }

    /** 被擒玩家某一 tick 的状态：结构内位置、是否举着东西、门禁、移速、跳跃、被擒剩余、试着起背摔是否进了起手。 */
    private record Held(Vec3 position, boolean usingItem, boolean locked, double speed, double jump,
                        long holdRemaining, boolean castStarted) {
        static Held of(GameTestHelper context, VillagerFistTestPlayer target, boolean tryCast) {
            // 只在 tryCast 的那一 tick 试着起式，其余只读。
            ServerPlayer player = target.player;
            UUID uuid = player.getUUID();
            boolean started = tryCast && target.cast(THROW).getPhase() == CastPhase.WARMUP;
            return new Held(context.relativeVec(player.position()),
                    player.isUsingItem(),
                    HumanStaggerLock.isLocked(uuid),
                    player.getAttributeValue(Attributes.MOVEMENT_SPEED),
                    player.getAttributeValue(Attributes.JUMP_STRENGTH),
                    TechniqueFabric.service().staggerRemainingMillis(uuid, StaggerKind.HOLD),
                    started);
        }
    }

    /** 倒地玩家某一 tick 的状态：姿势、移速相对原值、跳跃、门禁、试着起撩天是否进了起手。 */
    private record Downed(Pose pose, double speedRatio, double jump, boolean locked, boolean castStarted) {
        static Downed of(VillagerFistTestPlayer fixture, double baseSpeed, boolean tryCast) {
            // 只在 tryCast 的那一 tick 试着起式，其余只读。
            ServerPlayer player = fixture.player;
            boolean started = tryCast && fixture.cast(LIFT).getPhase() == CastPhase.WARMUP;
            return new Downed(player.getPose(),
                    player.getAttributeValue(Attributes.MOVEMENT_SPEED) / baseSpeed,
                    player.getAttributeValue(Attributes.JUMP_STRENGTH),
                    HumanStaggerLock.isLocked(player.getUUID()),
                    started);
        }
    }
}
