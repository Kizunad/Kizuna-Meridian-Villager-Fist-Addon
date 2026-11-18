package dev.kizuna.meridian.human.fabric;

import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.CENTER;
import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.HEIGHT;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.phys.Vec3;

/** 崩拳、撩天照目录的出拳时刻打中正前方的牛；撩天按击退抗性把人掀起。 */
public final class VillagerFistStrikeGameTest {
    private static final String CRUSHING = VillagerFistTestPlayer.PREFIX + "crushing_fist";
    private static final String GOLEM = VillagerFistTestPlayer.PREFIX + "golem_lift";
    /** 牛站在玩家正前方 2 格，在两式 2.5 格的出拳距离内。 */
    private static final double TARGET_DISTANCE = 2;
    /** 崩拳 1.0 秒、撩天 1.9 秒出拳，按 20 tick/秒。 */
    private static final int CRUSHING_STRIKE_TICKS = 20;
    private static final int GOLEM_STRIKE_TICKS = 38;
    /** 出拳后再等这么久才断言，掀起速度要在出拳后的下一 tick 读。 */
    private static final int SETTLE_TICKS = 5;
    private static final int TIMEOUT_TICKS = 100;
    private static final double LIFT_SPEED = 8;
    private static final double TICKS_PER_SECOND = 20;
    private static final double NO_RESISTANCE = 0;
    private static final double HALF_RESISTANCE = 0.5;
    private static final double EPSILON = 0.00001;
    private static final double FIST_MOVEMENT_MULTIPLIER = 0.4;

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void warmupPursuitKeepsPlayerMovementLimit(GameTestHelper context) {
        try (var fixture = new VillagerFistTestPlayer(context)) {
            var player = fixture.player;
            double before = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            fixture.start(CRUSHING, "崩拳");
            double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            context.assertTrue(Math.abs(speed - before * FIST_MOVEMENT_MULTIPLIER) < EPSILON, "玩家起手限速改变");
            context.succeed();
        }
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void crushingFistLandsOneSecondAfterTheCast(GameTestHelper context) {
        // 崩拳起手 1 秒后出拳，伤害在施放后第 20 tick 进入目标。
        var fixture = new VillagerFistTestPlayer(context);
        Target target = target(context);
        long castTick = fixture.tick();
        fixture.start(CRUSHING, "崩拳");
        context.runAfterDelay(CRUSHING_STRIKE_TICKS + SETTLE_TICKS, () -> {
            try (fixture) {
                assertLanded(context, target, castTick + CRUSHING_STRIKE_TICKS, "崩拳");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void golemLiftThrowsAnUnresistingTargetAtEightMetresPerSecond(GameTestHelper context) {
        // 击退抗性 0：竖直速度 8 m/s，即每 tick 0.4 格。
        liftsAt(context, NO_RESISTANCE);
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void golemLiftIsHalvedByHalfKnockbackResistance(GameTestHelper context) {
        // 击退抗性 0.5：竖直速度减半。
        liftsAt(context, HALF_RESISTANCE);
    }

    private static void liftsAt(GameTestHelper context, double resistance) {
        // 撩天起手 1.9 秒后出拳，伤害在第 38 tick 进入；下一 tick 牛自己动之前，竖直速度 = 8 ÷ 20 × (1 − 抗性)。
        var fixture = new VillagerFistTestPlayer(context);
        Target target = target(context);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(resistance);
        long castTick = fixture.tick();
        fixture.start(GOLEM, "撩天");
        context.runAfterDelay(GOLEM_STRIKE_TICKS + SETTLE_TICKS, () -> {
            try (fixture) {
                String label = "击退抗性 " + resistance + " 的撩天";
                assertLanded(context, target, castTick + GOLEM_STRIKE_TICKS, label);
                double expected = LIFT_SPEED / TICKS_PER_SECOND * (1 - resistance);
                context.assertTrue(target.liftSpeed != null && Math.abs(target.liftSpeed - expected) < EPSILON,
                        label + "竖直速度 " + target.liftSpeed + "，应为 " + expected);
                context.succeed();
            }
        });
    }

    private static void assertLanded(GameTestHelper context, Target target, long expectedTick, String label) {
        // 恰好在目录的出拳时刻第一次受伤。
        context.assertTrue(target.firstHurtTick == expectedTick,
                label + "在第 " + target.firstHurtTick + " tick 打中，应为第 " + expectedTick + " tick");
    }

    private static Target target(GameTestHelper context) {
        // 不动、不落的牛，站在玩家正前方；只记录受伤时刻和受伤后下一 tick 的竖直速度。
        Target target = new Target(context.getLevel());
        Vec3 relative = new Vec3(CENTER + 0.5, HEIGHT, CENTER + 0.5 + TARGET_DISTANCE);
        Vec3 position = context.absoluteVec(relative);
        target.snapTo(position.x, position.y, position.z, 0, 0);
        target.setNoAi(true /* aiDisabled */);
        target.setNoGravity(true /* noGravity */);
        context.assertTrue(context.getLevel().addFreshEntity(target), "目标牛生成失败");
        return target;
    }

    /** 记录第一次受伤的世界 tick，以及受伤后自己第一次 tick 前的竖直速度。 */
    static final class Target extends Cow {
        long firstHurtTick = -1;
        Double liftSpeed;

        Target(ServerLevel level) {
            // 原版牛，血量和防御不改。
            super(EntityTypes.COW, level);
        }

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
            // 只记第一次；伤害照常走原版伤害链。
            if (firstHurtTick < 0) {
                firstHurtTick = level.getGameTime();
            }
            return super.hurtServer(level, source, amount);
        }

        @Override
        public void tick() {
            // 功法在服务器 tick 末出拳，牛下一次 tick 开头的速度就是掀起设下的速度。
            if (firstHurtTick >= 0 && liftSpeed == null) {
                liftSpeed = getDeltaMovement().y;
            }
            super.tick();
        }
    }
}
