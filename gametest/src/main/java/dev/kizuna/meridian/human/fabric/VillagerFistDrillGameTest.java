package dev.kizuna.meridian.human.fabric;

import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.CENTER;
import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.HEIGHT;

import dev.kizuna.meridian.core.AnatomyState;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueFailure;
import dev.kizuna.meridian.technique.server.TechniqueCastEndReason;
import dev.kizuna.meridian.technique.server.TechniqueCastEnded;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** 打木桩：没桩就拒绝并在动作栏提示，六种桩都认，桩被挖掉就中断，和劈柴练进同一组手臂肌肉。 */
public final class VillagerFistDrillGameTest {
    private static final String DRILL = VillagerFistTestPlayer.PREFIX + "dummy_drill";
    private static final String CHOP = VillagerFistTestPlayer.TEST_PREFIX + "chop_wood";
    private static final String NO_TARGET = "message.meridian_villager.dummy_drill.no_target";
    private static final Identifier WOODEN_DUMMY = Identifier.parse("meridian_villager:wooden_dummy");
    private static final Identifier TRAINING_DUMMY = Identifier.parse("meridian_humanoid_dummy:dummy");
    private static final Identifier WOOD_HIT = Identifier.parse("minecraft:block.wood.hit");
    private static final Identifier GRASS_HIT = Identifier.parse("minecraft:block.grass.hit");
    /** 玩家正前方一格的桩位。 */
    private static final BlockPos POST = new BlockPos(CENTER, HEIGHT, CENTER + 1);
    /** 方块桩瞄方块中心，实体桩瞄齐胸高。 */
    private static final double BLOCK_AIM_HEIGHT = 0.5;
    private static final double ENTITY_AIM_HEIGHT = 1.0;
    private static final Vec3 POST_MIDDLE = Vec3.atBottomCenterOf(POST).add(0, BLOCK_AIM_HEIGHT, 0);
    private static final BlockPos BESIDE = new BlockPos(CENTER + 1, HEIGHT, CENTER);
    private static final double ASIDE = 0.5;
    /** 击打声在桩附近这么远以内才算这根桩的。 */
    private static final double SOUND_RADIUS = 2;
    /** 第一个木桩事件在起式 1 秒（20 tick）只锁桩，之后每 10 tick 响一下；45 tick 时已响两下。 */
    private static final int REJECTED_TICKS = 25;
    private static final int STRIKING_TICKS = 45;
    private static final int MINIMUM_HITS = 2;
    /** 第 25 tick 挖掉桩，第 30 tick 的事件就该拒绝。 */
    private static final int MINE_TICKS = 25;
    private static final int AFTER_MINE_TICKS = 20;
    private static final int TIMEOUT_TICKS = 100;
    /** 劈柴 1.5 秒收势；打木桩 32 秒打完，多等 1 秒。 */
    private static final int CHOP_DONE_TICKS = 40;
    private static final int DRILL_DONE_TICKS = 660;
    private static final int GROWTH_TIMEOUT_TICKS = 800;
    /** 五笔训练做功，每笔 80 N、45 W、6 s，每块手臂肌肉记 270 J。 */
    private static final double DRILL_JOULES = 1350;
    /** 起式第 1 秒和收势 1 秒肌肉不被占用，按 600 秒半衰期会巩固掉不到 0.5% 的待巩固负荷。 */
    private static final double CONSOLIDATED_SHARE = 0.005;
    private static final double EPSILON = 0.000001;

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillWithoutAPostIsRejected(GameTestHelper context) {
        // 平视前方空地：第一个木桩事件找不到桩，处理器拒绝，施放以 EFFECT_REJECTED 结束，动作栏提示。
        var fixture = new VillagerFistTestPlayer(context);
        fixture.start(DRILL, "打木桩");
        context.runAfterDelay(REJECTED_TICKS, () -> {
            try (fixture) {
                assertRejected(context, fixture.ended(DRILL), "没桩时");
                context.assertTrue(fixture.sawOverlay(NO_TARGET), "没桩时动作栏没有提示要对着木桩打");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillAcceptsTheWoodenDummy(GameTestHelper context) {
        // 本仓的木人桩方块。
        Block woodenDummy = BuiltInRegistries.BLOCK.getValue(WOODEN_DUMMY);
        drillsAt(context, helper -> helper.setBlock(POST, woodenDummy), BLOCK_AIM_HEIGHT, WOOD_HIT, "木人桩");
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillAcceptsALog(GameTestHelper context) {
        // 原木按 #minecraft:logs 认。
        drillsAt(context, helper -> helper.setBlock(POST, Blocks.OAK_LOG), BLOCK_AIM_HEIGHT, WOOD_HIT, "橡木原木");
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillAcceptsAHayBlock(GameTestHelper context) {
        // 干草块的受击声是草类的。
        drillsAt(context, helper -> helper.setBlock(POST, Blocks.HAY_BLOCK), BLOCK_AIM_HEIGHT, GRASS_HIT, "干草块");
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillAcceptsAnArmorStand(GameTestHelper context) {
        // 实体桩：盔甲架。负对照让处理器不认实体桩时，这一条要挂。
        drillsAt(context, helper -> helper.spawn(EntityTypes.ARMOR_STAND, POST), ENTITY_AIM_HEIGHT, WOOD_HIT,
                "盔甲架");
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillAcceptsAMannequin(GameTestHelper context) {
        // 实体桩：原版玩家模型。
        drillsAt(context, helper -> helper.spawn(EntityTypes.MANNEQUIN, POST), ENTITY_AIM_HEIGHT, WOOD_HIT,
                "玩家模型");
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillAcceptsATrainingDummy(GameTestHelper context) {
        // 实体桩：训练假人，按 ID 从注册表取，测试不编译依赖假人仓。
        EntityType<?> dummy = BuiltInRegistries.ENTITY_TYPE.getValue(TRAINING_DUMMY);
        drillsAt(context, helper -> helper.spawn(dummy, POST), ENTITY_AIM_HEIGHT, WOOD_HIT, "训练假人");
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillStartsLookingLevelAtTheWoodenDummy(GameTestHelper context) {
        // 站在木人桩跟前平视：视线从桩顶上面过去，照样能起式，不用低头。
        Block woodenDummy = BuiltInRegistries.BLOCK.getValue(WOODEN_DUMMY);
        drillsFacing(context, helper -> helper.setBlock(POST, woodenDummy), POST_MIDDLE, WOOD_HIT, "平视木人桩",
                fixture -> { });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillStartsLookingLevelAtAPostTwoBlocksAhead(GameTestHelper context) {
        // 隔一格空地的原木还在够得着的两格以内。
        drillsFacing(context, helper -> helper.setBlock(POST.south(), Blocks.OAK_LOG),
                POST_MIDDLE.add(0, 0, 1), WOOD_HIT, "平视两格外的原木", fixture -> { });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillStartsLookingLevelAtAnArmorStandSlightlyAside(GameTestHelper context) {
        // 盔甲架偏在右前方半格（偏离正前方约 27°），准星没对着它，也算在面前。
        Vec3 stand = Vec3.atBottomCenterOf(POST).add(ASIDE, 0, 0);
        drillsFacing(context, helper -> helper.spawn(EntityTypes.ARMOR_STAND, stand), stand.add(0, ENTITY_AIM_HEIGHT, 0),
                WOOD_HIT, "平视偏在一旁的盔甲架", fixture -> { });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillLookingLevelIgnoresAPostBesideThePlayer(GameTestHelper context) {
        // 桩在正左边（偏离正前方 90°）：不算对着，照旧拒绝。
        var fixture = new VillagerFistTestPlayer(context);
        context.setBlock(BESIDE, BuiltInRegistries.BLOCK.getValue(WOODEN_DUMMY));
        fixture.start(DRILL, "桩在身旁：打木桩");
        context.runAfterDelay(REJECTED_TICKS, () -> {
            try (fixture) {
                assertRejected(context, fixture.ended(DRILL), "桩在身旁时");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillLookingLevelDoesNotReachThroughAWall(GameTestHelper context) {
        // 两格外的原木本来够得着（上一条），中间砌一堵两格高的墙就不算。
        var fixture = new VillagerFistTestPlayer(context);
        context.setBlock(POST.south(), Blocks.OAK_LOG);
        context.setBlock(POST, Blocks.STONE);
        context.setBlock(POST.above(), Blocks.STONE);
        fixture.start(DRILL, "隔墙：打木桩");
        context.runAfterDelay(REJECTED_TICKS, () -> {
            try (fixture) {
                assertRejected(context, fixture.ended(DRILL), "隔着墙时");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void drillStopsWhenThePostIsMined(GameTestHelper context) {
        // 打到一半挖掉木人桩：下一个木桩事件被拒绝，施放中断。
        var fixture = new VillagerFistTestPlayer(context);
        context.setBlock(POST, BuiltInRegistries.BLOCK.getValue(WOODEN_DUMMY));
        fixture.lookAt(Vec3.atBottomCenterOf(POST).add(0, BLOCK_AIM_HEIGHT, 0));
        fixture.start(DRILL, "对着木人桩打木桩");
        context.runAfterDelay(MINE_TICKS, () -> {
            context.assertTrue(fixture.ended(DRILL).isEmpty(), "挖桩前打木桩就结束了");
            context.destroyBlock(POST);
            context.runAfterDelay(AFTER_MINE_TICKS, () -> {
                try (fixture) {
                    assertRejected(context, fixture.ended(DRILL), "挖掉桩后");
                    context.succeed();
                }
            });
        });
    }

    @GameTest(maxTicks = GROWTH_TIMEOUT_TICKS)
    public void drillAndChoppingTrainTheSameArmMuscles(GameTestHelper context) {
        // 先劈柴，再对着木人桩打满一轮：六块手臂肌肉的待巩固训练负荷在劈柴那份上再加 1350 J。
        var fixture = new VillagerFistTestPlayer(context);
        context.setBlock(POST, BuiltInRegistries.BLOCK.getValue(WOODEN_DUMMY));
        fixture.lookAt(Vec3.atBottomCenterOf(POST).add(0, BLOCK_AIM_HEIGHT, 0));
        fixture.start(CHOP, "劈柴");
        context.runAfterDelay(CHOP_DONE_TICKS, () -> {
            Map<String, Double> chopped = pendingLoads(fixture);
            assertCompleted(context, fixture.ended(CHOP), "劈柴");
            context.assertTrue(chopped.values().stream().allMatch(load -> load > 0), "劈柴没有练到手臂肌肉：" + chopped);
            fixture.start(DRILL, "劈柴后打木桩");
            context.runAfterDelay(DRILL_DONE_TICKS, () -> {
                try (fixture) {
                    assertCompleted(context, fixture.ended(DRILL), "打木桩");
                    assertDrillAdded(context, chopped, pendingLoads(fixture));
                    context.succeed();
                }
            });
        });
    }

    private static void drillsAt(GameTestHelper context, Consumer<GameTestHelper> placePost, double aimHeight,
                                 Identifier hitSound, String label) {
        // 桩脚下垫一块石头（实体桩有重力，会掉出准星），摆好桩、对准、起式；45 tick 时施放还在，并在桩附近听到至少两下这根桩的击打声。
        Vec3 aim = Vec3.atBottomCenterOf(POST).add(0, aimHeight, 0);
        drillsFacing(context, placePost, aim, hitSound, label, fixture -> fixture.lookAt(aim));
    }

    private static void drillsFacing(GameTestHelper context, Consumer<GameTestHelper> placePost, Vec3 soundAt,
                                     Identifier hitSound, String label, Consumer<VillagerFistTestPlayer> look) {
        // look 摆视线（什么都不做就是平视正前方）；击打声要响在 soundAt 附近。
        var fixture = new VillagerFistTestPlayer(context);
        context.setBlock(POST.below(), Blocks.STONE);
        context.setBlock(POST.south().below(), Blocks.STONE);
        placePost.accept(context);
        look.accept(fixture);
        fixture.start(DRILL, label + "：打木桩");
        Vec3 post = context.absoluteVec(soundAt);
        context.runAfterDelay(STRIKING_TICKS, () -> {
            try (fixture) {
                Optional<TechniqueCastEnded> ended = fixture.ended(DRILL);
                context.assertTrue(ended.isEmpty(), label + "：打木桩提前结束 " + ended.map(Object::toString).orElse(""));
                long hits = fixture.sounds().stream()
                        .filter(sound -> sound.id().equals(hitSound))
                        .filter(sound -> sound.position().closerThan(post, SOUND_RADIUS))
                        .count();
                context.assertTrue(hits >= MINIMUM_HITS, label + "：桩上只响了 " + hits + " 下 " + hitSound);
                context.succeed();
            }
        });
    }

    private static void assertRejected(GameTestHelper context, Optional<TechniqueCastEnded> ended, String label) {
        // 处理器拒绝时功法库以 FAILED 结束施放，失败码是 EFFECT_REJECTED。
        context.assertTrue(ended.isPresent(), label + "打木桩没有结束");
        TechniqueCastEnded event = ended.orElseThrow();
        context.assertTrue(event.reason() == TechniqueCastEndReason.FAILED
                        && event.outcome().getFailure() == TechniqueFailure.EFFECT_REJECTED,
                label + "打木桩不是因处理器拒绝而结束：" + event);
    }

    private static void assertCompleted(GameTestHelper context, Optional<TechniqueCastEnded> ended, String label) {
        // 正常打完、收势结束。
        context.assertTrue(ended.isPresent(), label + "没有结束");
        TechniqueCastEnded event = ended.orElseThrow();
        context.assertTrue(event.reason() == TechniqueCastEndReason.COMPLETED, label + "没有正常打完：" + event);
    }

    private static void assertDrillAdded(GameTestHelper context, Map<String, Double> before,
                                         Map<String, Double> after) {
        // 每块肌肉增加的负荷在 1350 J 以内，至多少掉起式和收势时巩固走的那一点。
        context.assertTrue(after.keySet().equals(before.keySet()), "前后读到的手臂肌肉不一致");
        for (var entry : after.entrySet()) {
            double added = entry.getValue() - before.get(entry.getKey());
            boolean whole = added <= DRILL_JOULES + EPSILON;
            boolean nearlyWhole = added >= DRILL_JOULES * (1 - CONSOLIDATED_SHARE);
            context.assertTrue(whole && nearlyWhole, entry.getKey() + " 打木桩只加了 " + added + " J");
        }
    }

    private static Map<String, Double> pendingLoads(VillagerFistTestPlayer fixture) {
        // 六块手臂肌肉各自的待巩固训练负荷。
        List<AnatomyState.Tissue> muscles = fixture.armMuscles();
        return muscles.stream().collect(Collectors.toMap(
                tissue -> tissue.id().toString(), AnatomyState.Tissue::pendingTrainingLoad));
    }
}
