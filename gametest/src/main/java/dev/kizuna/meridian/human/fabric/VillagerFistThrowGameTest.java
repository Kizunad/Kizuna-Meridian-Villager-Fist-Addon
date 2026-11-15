package dev.kizuna.meridian.human.fabric;

import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.CENTER;
import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.HEIGHT;

import dev.kizuna.meridian.humanoid.fabric.FistMasterDutyGameTest;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.server.TechniqueService.StaggerKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 背摔：僵尸（没有功法宿主）被擒时拉到身前 1 格、落在身后 2 格、倒地，期间大脑停着，靠的是本仓的锁；
 * 身后有墙落在墙前一格；村民被擒时大脑停着，靠的是 Threatening 的锁；铁傀儡摔不动，退成一靠 ×0.4。
 */
public final class VillagerFistThrowGameTest {
    private static final String THROW = VillagerFistTestPlayer.PREFIX + "shoulder_throw";
    private static final String TOO_HEAVY = "message.meridian_villager.grapple.too_heavy";
    /** 1.20 擒、2.20 抛、0.40 s 后落地、倒地 1.5 s，按 20 tick/秒。 */
    private static final int GRAB_TICKS = 24;
    private static final int THROW_TICKS = 44;
    private static final int LAND_TICKS = 52;
    private static final int DOWN_END_TICKS = 82;
    private static final int LEAN_TICKS = 30;
    /** 断言窗口两头各留几 tick，避开擒、落地、起来那一 tick 的先后。 */
    private static final int MARGIN_TICKS = 3;
    private static final int AWAKE_CHECK_TICKS = 100;
    /** 第一摔的僵尸第 82 tick 起来；第二个玩家第 60 tick 起式，第 84 tick 擒、第 112 tick 落地，仍在免摔里。 */
    private static final int SECOND_CAST_TICKS = 60;
    private static final int SECOND_LAND_TICKS = SECOND_CAST_TICKS + LAND_TICKS;
    private static final int SECOND_AWAKE_GAP_TICKS = 6;
    private static final int SECOND_AWAKE_TICKS = SECOND_LAND_TICKS + SECOND_AWAKE_GAP_TICKS;
    private static final int IMMUNITY_TIMEOUT_TICKS = 200;
    private static final String SECOND_NAME = "fist_second";
    /** 第二个玩家站在第一摔落点西边 1.5 格、朝东，僵尸在他身前 1.5 格。 */
    private static final double SECOND_SIDE_OFFSET = 1.5;
    private static final float FACING_EAST = -90;
    private static final int TIMEOUT_TICKS = 160;
    private static final int STRUCTURE_SIZE = 8;
    private static final int FLOOR_Y = HEIGHT - 1;
    private static final double HOLD_DISTANCE = 1;
    private static final double LANDING_DISTANCE = 2;
    private static final double WALLED_LANDING_DISTANCE = 1;
    private static final double GOLEM_DISTANCE = 1.8;
    private static final double POSITION_TOLERANCE = 0.2;
    /** 书级空手 1.5 × 一靠 0.4 × 双手 1.2。 */
    private static final double LEAN_DOSE = 1.5 * 0.4 * 1.2;
    private static final double DOSE_TOLERANCE = 0.0001;
    private static final double HALF = 0.5;

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void zombieIsHeldThrownBehindAndKeptDownByTheAddonLock(GameTestHelper context) {
        // 被擒时在身前 1 格；落在身后 2 格；被擒和倒地期间 serverAiStep 一次都没跑（大脑计数不涨），起来后恢复。
        throwsZombie(context, LANDING_DISTANCE, false /* walled */);
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void zombieLandsBeforeAWallBehindTheCaster(GameTestHelper context) {
        // 身后第 2 格立一块石头：落在挡住之前的最后一格，即身后 1 格。
        throwsZombie(context, WALLED_LANDING_DISTANCE, true /* walled */);
    }

    @GameTest(maxTicks = IMMUNITY_TIMEOUT_TICKS)
    public void zombieThrownAgainWithinThreeSecondsOfGettingUpStaysOnItsFeet(GameTestHelper context) {
        // 第一摔落地倒地 1.5 s；起来 1 s 后第二个玩家从侧面再摔：照样落地挨打，但在 3 s 免摔里，不再倒地。
        floor(context);
        trough(context);
        var first = new VillagerFistTestPlayer(context);
        first.player.getAbilities().invulnerable = true;
        Vec3 secondFeet = new Vec3(CENTER + HALF - SECOND_SIDE_OFFSET, HEIGHT, CENTER + HALF - LANDING_DISTANCE);
        var second = new VillagerFistTestPlayer(context, SECOND_NAME, secondFeet, FACING_EAST);
        second.player.getAbilities().invulnerable = true;
        BrainCountingZombie zombie = new BrainCountingZombie(context.getLevel());
        Vec3 spot = context.absoluteVec(new Vec3(CENTER + HALF, HEIGHT, CENTER + HALF + HOLD_DISTANCE));
        zombie.snapTo(spot.x, spot.y, spot.z, 0, 0);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        context.assertTrue(context.getLevel().addFreshEntity(zombie), "僵尸生成失败");
        long castTick = first.tick();
        Map<Long, Vec3> positions = new HashMap<>();
        Map<Long, Integer> brain = new HashMap<>();
        Map<Long, CastPhase> secondPhase = new HashMap<>();
        first.everyTick(() -> {
            // 第二摔在逐 tick 观察里起式：这里不能断言，起式结果记下来最后再看。
            long offset = first.tick() - castTick;
            if (offset == SECOND_CAST_TICKS) {
                secondPhase.put(offset, second.cast(THROW).getPhase());
            }
            positions.put(offset, context.relativeVec(zombie.position()));
            brain.put(offset, zombie.brainTicks);
        });
        first.start(THROW, "第一摔");
        context.runAfterDelay(SECOND_AWAKE_TICKS + 1, () -> {
            try (first; second) {
                context.assertTrue(secondPhase.get((long) SECOND_CAST_TICKS) == CastPhase.WARMUP, "第二摔起式失败");
                assertImmuneLanding(context, positions, brain);
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void villagerIsHeldWithItsBrainPausedByTheHumanoidLock(GameTestHelper context) {
        // 村民有功法宿主（完整装配里的 Threatening 认领）：被擒记进功法库，本仓的无宿主锁就不上，停大脑的只能是
        // Threatening 的锁。
        floor(context);
        trough(context);
        var fixture = new VillagerFistTestPlayer(context);
        fixture.player.getAbilities().invulnerable = true;
        BrainCountingVillager villager = new BrainCountingVillager(context.getLevel());
        Vec3 spot = context.absoluteVec(new Vec3(CENTER + HALF, HEIGHT, CENTER + HALF + HOLD_DISTANCE));
        villager.snapTo(spot.x, spot.y, spot.z, 0, 0);
        context.assertTrue(context.getLevel().addFreshEntity(villager), "村民生成失败");
        long castTick = fixture.tick();
        Map<Long, Integer> brain = new HashMap<>();
        Map<Long, Long> holdRemaining = new HashMap<>();
        fixture.everyTick(() -> {
            brain.put(fixture.tick() - castTick, villager.brainTicks);
            holdRemaining.put(fixture.tick() - castTick,
                    TechniqueFabric.service().staggerRemainingMillis(villager.getUUID(), StaggerKind.HOLD));
        });
        fixture.start(THROW, "背摔");
        context.runAfterDelay(THROW_TICKS, () -> {
            try (fixture) {
                Long remaining = holdRemaining.get((long) GRAB_TICKS + MARGIN_TICKS);
                context.assertTrue(remaining != null && remaining > 0, "被擒的村民没有记进功法库（没有宿主？）");
                assertFrozen(context, brain, GRAB_TICKS + MARGIN_TICKS, THROW_TICKS - 1, "被擒");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void ironGolemIsTooHeavyAndTakesALeanInstead(GameTestHelper context) {
        // 铁傀儡在不可摔名单里：不擒不摔，1.50 挨一靠，剂量 1.5 × 0.4 × 1.2；施放者动作栏看到「摔不动」。
        floor(context);
        var fixture = new VillagerFistTestPlayer(context);
        Golem golem = new Golem(context.getLevel());
        Vec3 spot = context.absoluteVec(new Vec3(CENTER + HALF, HEIGHT, CENTER + HALF + GOLEM_DISTANCE));
        golem.snapTo(spot.x, spot.y, spot.z, 0, 0);
        golem.setNoAi(true /* aiDisabled */);
        context.assertTrue(context.getLevel().addFreshEntity(golem), "铁傀儡生成失败");
        long castTick = fixture.tick();
        fixture.start(THROW, "背摔");
        context.runAfterDelay(LAND_TICKS + MARGIN_TICKS, () -> {
            try (fixture) {
                context.assertTrue(golem.firstHurtTick == castTick + LEAN_TICKS,
                        "一靠在第 " + golem.firstHurtTick + " tick，应为第 " + (castTick + LEAN_TICKS) + " tick");
                context.assertTrue(Math.abs(golem.firstDose - LEAN_DOSE) < DOSE_TOLERANCE,
                        "一靠剂量 " + golem.firstDose + "，应为 " + LEAN_DOSE);
                context.assertTrue(golem.getZ() > fixture.player.getZ(), "摔不动的铁傀儡被摔到了身后");
                context.assertTrue(fixture.sawOverlay(TOO_HEAVY), "施放者没看到「摔不动」");
                context.succeed();
            }
        });
    }

    private static void throwsZombie(GameTestHelper context, double landingDistance, boolean walled) {
        // 玩家无敌，僵尸不会先来打断起手；僵尸戴头盔，不被太阳点着。
        floor(context);
        trough(context);
        if (walled) {
            context.setBlock(new BlockPos(CENTER, HEIGHT, CENTER - (int) LANDING_DISTANCE), Blocks.STONE);
        }
        var fixture = new VillagerFistTestPlayer(context);
        fixture.player.getAbilities().invulnerable = true;
        BrainCountingZombie zombie = new BrainCountingZombie(context.getLevel());
        Vec3 spot = context.absoluteVec(new Vec3(CENTER + HALF, HEIGHT, CENTER + HALF + HOLD_DISTANCE));
        zombie.snapTo(spot.x, spot.y, spot.z, 0, 0);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        context.assertTrue(context.getLevel().addFreshEntity(zombie), "僵尸生成失败");
        long castTick = fixture.tick();
        Map<Long, Integer> idle = new HashMap<>();
        Map<Long, Vec3> positions = new HashMap<>();
        Map<Long, Boolean> seenDowned = new HashMap<>();
        // 旁观的一方不进世界，只拿来问「大脑选式时认不认僵尸倒着」，不碍着摔。
        Mob watcher = EntityTypes.COW.create(context.getLevel(), EntitySpawnReason.COMMAND);
        fixture.everyTick(() -> {
            idle.put(fixture.tick() - castTick, zombie.brainTicks);
            positions.put(fixture.tick() - castTick, context.relativeVec(zombie.position()));
            seenDowned.put(fixture.tick() - castTick, FistMasterDutyGameTest.seesDowned(watcher, zombie));
        });
        fixture.start(THROW, "背摔");
        context.runAfterDelay(AWAKE_CHECK_TICKS, () -> {
            try (fixture) {
                assertHeld(context, positions, idle);
                assertLanded(context, positions, landingDistance);
                assertSeenDownedOnlyWhileDown(context, seenDowned);
                assertFrozen(context, idle, LAND_TICKS + MARGIN_TICKS, DOWN_END_TICKS - MARGIN_TICKS, "倒地");
                int awake = idle.get((long) AWAKE_CHECK_TICKS - 1);
                int down = idle.get((long) DOWN_END_TICKS - MARGIN_TICKS);
                context.assertTrue(awake > down, "起来以后僵尸的大脑没有恢复：" + down + " → " + awake);
                context.succeed();
            }
        });
    }

    private static void assertImmuneLanding(GameTestHelper context, Map<Long, Vec3> positions,
                                            Map<Long, Integer> brain) {
        // 第一摔倒地、大脑停着；第二摔落在第二个玩家身后 2 格，落地后不倒地，大脑接着跑。
        assertFrozen(context, brain, LAND_TICKS + MARGIN_TICKS, DOWN_END_TICKS - MARGIN_TICKS, "第一摔倒地");
        Vec3 landed = positions.get((long) SECOND_LAND_TICKS + MARGIN_TICKS);
        Vec3 expected = new Vec3(CENTER + HALF - SECOND_SIDE_OFFSET - LANDING_DISTANCE, HEIGHT,
                CENTER + HALF - LANDING_DISTANCE);
        context.assertTrue(landed != null && landed.distanceTo(expected) < POSITION_TOLERANCE,
                "第二摔落在 " + landed + "，应在 " + expected);
        int before = brain.get((long) SECOND_LAND_TICKS + MARGIN_TICKS);
        int after = brain.get((long) SECOND_AWAKE_TICKS);
        context.assertTrue(after > before, "起来 3 s 内又被摔倒了，大脑没跑：" + before + " → " + after);
    }

    private static void assertSeenDownedOnlyWhileDown(GameTestHelper context, Map<Long, Boolean> seen) {
        // 僵尸没有功法宿主，倒地记在本仓的锁里；登记给 Threatening 以后，大脑选式时认得它倒着。被擒着、起来以后都不算倒地。
        context.assertTrue(!seen.get((long) GRAB_TICKS + MARGIN_TICKS), "僵尸只是被擒着，却被认成倒地");
        context.assertTrue(seen.get((long) LAND_TICKS + MARGIN_TICKS) && seen.get((long) DOWN_END_TICKS - MARGIN_TICKS),
                "僵尸被摔倒在地，大脑选式时没认出来");
        context.assertTrue(!seen.get((long) AWAKE_CHECK_TICKS - 1), "僵尸已经起来了，还被认成倒地");
    }

    private static void assertHeld(GameTestHelper context, Map<Long, Vec3> positions, Map<Long, Integer> idle) {
        // 擒住到抛出之间：僵尸在玩家正前方 1 格，大脑停着。
        Vec3 held = positions.get((long) GRAB_TICKS + MARGIN_TICKS);
        Vec3 expected = new Vec3(CENTER + HALF, HEIGHT, CENTER + HALF + HOLD_DISTANCE);
        context.assertTrue(held != null && held.distanceTo(expected) < POSITION_TOLERANCE,
                "被擒的僵尸在 " + held + "，应在身前 1 格 " + expected);
        assertFrozen(context, idle, GRAB_TICKS + MARGIN_TICKS, THROW_TICKS - 1, "被擒");
    }

    private static void assertLanded(GameTestHelper context, Map<Long, Vec3> positions, double distance) {
        // 落地后躺在身后 distance 格。
        Vec3 landed = positions.get((long) LAND_TICKS + MARGIN_TICKS);
        Vec3 expected = new Vec3(CENTER + HALF, HEIGHT, CENTER + HALF - distance);
        context.assertTrue(landed != null && landed.distanceTo(expected) < POSITION_TOLERANCE,
                "僵尸落在 " + landed + "，应在身后 " + distance + " 格 " + expected);
    }

    private static void assertFrozen(GameTestHelper context, Map<Long, Integer> idle, long from, long to,
                                     String label) {
        // 大脑计数记在 customServerAiStep 里，它只从 Mob.serverAiStep 调：整段不变说明大脑一次都没跑。
        // 不用原版不动时计数：玩家在 32 格内时 checkDespawn 每 tick 把它清零，锁不锁看起来都一样。
        Integer first = idle.get(from);
        Integer last = idle.get(to);
        context.assertTrue(first != null && first.equals(last),
                label + "期间僵尸的大脑还在跑：第 " + from + " tick 计数 " + first + "，第 " + to + " tick " + last);
    }

    private static void trough(GameTestHelper context) {
        // 身前 1 格是一格宽的石槽（两侧和前方两格高）：开着 AI 的生物擒住之前走不开，不会逛出前方扇形。
        for (BlockPos wall : List.of(new BlockPos(CENTER - 1, HEIGHT, CENTER + 1),
                new BlockPos(CENTER + 1, HEIGHT, CENTER + 1), new BlockPos(CENTER, HEIGHT, CENTER + 2))) {
            context.setBlock(wall, Blocks.STONE);
            context.setBlock(wall.above(), Blocks.STONE);
        }
    }

    private static void floor(GameTestHelper context) {
        // 默认空结构全是空气：铺一层石地板，僵尸和铁傀儡站得住。
        for (int x = 0; x < STRUCTURE_SIZE; x++) {
            for (int z = 0; z < STRUCTURE_SIZE; z++) {
                context.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
            }
        }
    }

    /** 数大脑跑了几次的原版村民；类型仍是村民，照常由 Threatening 认领。 */
    static final class BrainCountingVillager extends Villager {
        int brainTicks;

        BrainCountingVillager(ServerLevel level) {
            // 原版村民，只多一个计数。
            super(EntityTypes.VILLAGER, level);
        }

        @Override
        protected void customServerAiStep(ServerLevel level) {
            // Mob.serverAiStep 跑到这里就记一次，再照原版走。
            brainTicks++;
            super.customServerAiStep(level);
        }
    }

    /** 数大脑跑了几次的原版僵尸。 */
    static final class BrainCountingZombie extends Zombie {
        int brainTicks;

        BrainCountingZombie(ServerLevel level) {
            // 原版僵尸，只多一个计数。
            super(level);
        }

        @Override
        protected void customServerAiStep(ServerLevel level) {
            // Mob.serverAiStep 跑到这里就记一次，再照原版走。
            brainTicks++;
            super.customServerAiStep(level);
        }
    }

    /** 记录第一次受伤的世界 tick 和剂量。 */
    static final class Golem extends IronGolem {
        long firstHurtTick = -1;
        double firstDose = -1;

        Golem(ServerLevel level) {
            // 原版铁傀儡，血量和抗性不改。
            super(EntityTypes.IRON_GOLEM, level);
        }

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
            // 只记第一次；伤害照常走原版伤害链。
            if (firstHurtTick < 0) {
                firstHurtTick = level.getGameTime();
                firstDose = amount;
            }
            return super.hurtServer(level, source, amount);
        }
    }
}
