package dev.kizuna.meridian.villager.verification;

import dev.kizuna.meridian.protocol.pb.BodyMessages.Realm;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ArmSelection;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.BlockWork;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Category;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.LegKnockbackKind;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.LimbStrike;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ModuleKind;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ScrollDrop;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueSlot;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.WorkCost;
import dev.kizuna.meridian.technique.server.TechniqueRegistry;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 发行目录经正式 Human 内容和功法库校验器注册，逐式对照 C7 SPEC 第三、六节的表。 */
public final class VillagerFistCatalogTest {
    private static final String BOOK = "meridian_villager:sleeve_fist";
    private static final String OUTER = "meridian_villager:outer";
    private static final String CRUSHING_FIST = "meridian_villager:crushing_fist";
    private static final String GOLEM_LIFT = "meridian_villager:golem_lift";
    private static final String DUMMY_DRILL = "meridian_villager:dummy_drill";
    private static final List<String> OUTER_TECHNIQUES = List.of(CRUSHING_FIST, GOLEM_LIFT, DUMMY_DRILL);
    private static final double SCROLL_CHANCE = 0.25;
    private static final int ARM_MUSCLES = 6;
    private static final int LEG_MUSCLES = 6;
    private static final int MAXIMUM_HAND_MERIDIANS = 6;
    private static final double FIGHT_MOVEMENT = 0.4;
    private static final double LIFT_SPEED = 8;
    private static final String DRILL_PROCESSOR = "meridian_villager:dummy_drill";
    private static final String DRILL_TAG = "meridian_villager:drill_targets";
    private static final double DRILL_REACH = 2;
    private static final long DRILL_START_MILLIS = 1_000;
    private static final long DRILL_INTERVAL_MILLIS = 500;
    private static final long DRILL_DURATION_MILLIS = 30_000;
    private static final List<Long> DRILL_WORK_MILLIS = List.of(1_000L, 7_000L, 13_000L, 19_000L, 25_000L);
    private static final double DRILL_FORCE = 80;
    private static final double DRILL_WATTS = 45;
    private static final double DRILL_SEGMENT_SECONDS = 6;
    private static final double TOLERANCE = 1e-9;

    /** 一式打人的表：起手、收势、冷却、出拳时刻、出拳手、伤害类型、倍率、击退方式。 */
    private record Strike(long warmupMillis, long recoveryMillis, long cooldownMillis, long strikeMillis,
                          ArmSelection arm, String damageType, double multiplier, LegKnockbackKind knockback) {
    }

    private static final Map<String, Strike> STRIKES = Map.of(
            CRUSHING_FIST, new Strike(1_000, 2_000, 1_000, 1_000, ArmSelection.REAR_ARM,
                    "meridian_villager:fist_rear_torso", 1.4, LegKnockbackKind.PUSH),
            GOLEM_LIFT, new Strike(1_900, 3_100, 4_000, 1_900, ArmSelection.BOTH_ARMS,
                    "meridian_villager:fist_both_torso", 1.5, LegKnockbackKind.LIFT));

    @Test
    public void catalogRegistersOneBookWithTheOuterVolume() throws IOException {
        // 一书一卷三式；上卷没有前置卷，生成卷轴并由村民掉落。
        TechniqueRegistry registry = VillagerFistContent.registry();
        assertEquals(1, registry.books().size());
        assertEquals(BOOK, registry.books().getFirst().getId());
        assertEquals(1, registry.volumes().size());
        var outer = registry.findVolume(OUTER).orElseThrow();
        assertEquals(BOOK, outer.getBookId());
        assertEquals(OUTER_TECHNIQUES, outer.getTechniquesList().stream().map(reference -> reference.getId()).toList());
        assertTrue(outer.getPrerequisiteVolumeIdsList().isEmpty());
        assertFalse("上卷要有卷轴", outer.hasScrollItem() && !outer.getScrollItem());
        var drop = ScrollDrop.newBuilder().setEntityType("minecraft:villager").setChance(SCROLL_CHANCE).build();
        assertEquals(List.of(drop), outer.getScrollDropsList());
        assertEquals(OUTER_TECHNIQUES.size(), registry.definitions().size());
    }

    @Test
    public void bookFightsBareHandedOnTheGround() throws IOException {
        // 书级拳掌规则：不看持物、要着地。
        var book = VillagerFistContent.registry().books().getFirst();
        assertTrue(book.getArmRules().getIgnoreHeldItems());
        assertTrue(book.getArmRules().getRequireGrounded());
    }

    @Test
    public void strikesUseHandMeridiansAndBothLimbGroups() throws IOException {
        // 崩拳、撩天：游炁起，手经 1–6 条要已开，两臂、两腿各 6 块肌肉，限速 0.4，都是进攻式。
        TechniqueRegistry registry = VillagerFistContent.registry();
        for (String id : STRIKES.keySet()) {
            TechniqueDefinition definition = registry.findLatest(id).orElseThrow();
            assertEquals(id, Category.ATTACK, definition.getCategory());
            assertEquals(id, Realm.WANDERING_QI, definition.getMinimumRealm());
            assertEquals(id, FIGHT_MOVEMENT, definition.getMovementSpeedMultiplier(), TOLERANCE);
            TechniqueSlot hand = slot(definition, "hand_meridians");
            assertEquals(ModuleKind.MERIDIAN, hand.getModule());
            assertEquals(List.of("human:hand_meridian"), hand.getTagsList());
            assertEquals(1, hand.getMinimumCount());
            assertEquals(MAXIMUM_HAND_MERIDIANS, hand.getMaximumCount());
            assertTrue(hand.getRequireOpened());
            assertMuscles(slot(definition, "arm_muscles"), "human:arm_muscle", ARM_MUSCLES);
            assertMuscles(slot(definition, "leg_muscles"), "human:leg_muscle", LEG_MUSCLES);
        }
    }

    @Test
    public void strikesLandAtTheirTableTimes() throws IOException {
        // 每式恰好一记肢击，时刻、出拳手、伤害类型、倍率、击退方式照表；撩天把人掀起 8 m/s。
        TechniqueRegistry registry = VillagerFistContent.registry();
        for (var entry : STRIKES.entrySet()) {
            TechniqueDefinition definition = registry.findLatest(entry.getKey()).orElseThrow();
            Strike expected = entry.getValue();
            assertEquals(expected.warmupMillis(), definition.getPhases().getWarmupMillis());
            assertEquals(expected.recoveryMillis(), definition.getPhases().getRecoveryMillis());
            assertEquals(expected.cooldownMillis(), definition.getCooldownMillis());
            List<TechniqueEffect> strikes = definition.getEffectsList().stream()
                    .filter(TechniqueEffect::hasLimbStrike)
                    .toList();
            assertEquals(1, strikes.size());
            assertEquals(expected.strikeMillis(), strikes.getFirst().getAtMillis());
            LimbStrike strike = strikes.getFirst().getLimbStrike();
            assertEquals(expected.arm(), strike.getArm());
            assertEquals(expected.damageType(), strike.getDamageTypeId());
            assertEquals(expected.multiplier(), strike.getMultiplier().getValue(), TOLERANCE);
            assertEquals(expected.knockback(), strike.getKnockbackKind());
        }
        LimbStrike lift = registry.findLatest(GOLEM_LIFT).orElseThrow().getEffectsList().stream()
                .filter(TechniqueEffect::hasLimbStrike)
                .findFirst()
                .orElseThrow()
                .getLimbStrike();
        assertEquals(LIFT_SPEED, lift.getLiftSpeed().getValue(), TOLERANCE);
    }

    @Test
    public void dummyDrillTrainsArmsAgainstAPostForThirtySeconds() throws IOException {
        // 打木桩：站定、修炼类、AI 权重 0；1.0 s 起每 0.5 s 一次木桩事件共 30 s，五笔 6 s 训练做功。
        TechniqueDefinition drill = VillagerFistContent.registry().findLatest(DUMMY_DRILL).orElseThrow();
        assertEquals(Category.CULTIVATION, drill.getCategory());
        assertEquals(Realm.WANDERING_QI, drill.getMinimumRealm());
        assertEquals(0, drill.getMovementSpeedMultiplier(), TOLERANCE);
        assertEquals(0, drill.getAiHints().getCategoryWeight().getValue(), TOLERANCE);
        assertTrue(drill.getPhases().getInterruptibleByDamage());
        assertMuscles(slot(drill, "arm_muscles"), "human:arm_muscle", ARM_MUSCLES);
        TechniqueEffect blockEffect = drill.getEffectsList().stream()
                .filter(TechniqueEffect::hasBlockWork)
                .findFirst()
                .orElseThrow();
        assertEquals(DRILL_START_MILLIS, blockEffect.getAtMillis());
        BlockWork work = blockEffect.getBlockWork();
        assertEquals(DRILL_PROCESSOR, work.getProcessorId());
        assertEquals(List.of(DRILL_TAG), work.getBlockTagsList());
        assertEquals(DRILL_REACH, work.getMaximumDistance().getValue(), TOLERANCE);
        assertEquals(DRILL_INTERVAL_MILLIS, work.getIntervalMillis());
        assertEquals(DRILL_DURATION_MILLIS, work.getDurationMillis());
        List<TechniqueEffect> segments = drill.getEffectsList().stream().filter(TechniqueEffect::hasWork).toList();
        assertEquals(DRILL_WORK_MILLIS, segments.stream().map(TechniqueEffect::getAtMillis).toList());
        for (TechniqueEffect segment : segments) {
            WorkCost cost = segment.getWork();
            assertEquals("arm_muscles", cost.getSlotId());
            assertEquals(DRILL_FORCE, cost.getForce().getValue(), TOLERANCE);
            assertEquals(DRILL_WATTS, cost.getPower().getValue(), TOLERANCE);
            assertEquals(DRILL_SEGMENT_SECONDS, cost.getDuration().getValue(), TOLERANCE);
            assertTrue(cost.getTraining());
        }
    }

    private static TechniqueSlot slot(TechniqueDefinition definition, String id) {
        // 槽位按 ID 取，缺了直接失败。
        return definition.getSlotsList().stream()
                .filter(slot -> slot.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError(definition.getId() + " 缺槽 " + id));
    }

    private static void assertMuscles(TechniqueSlot slot, String tag, int count) {
        // 肌肉槽恰好绑这一组的全部肌肉。
        assertEquals(ModuleKind.TISSUE, slot.getModule());
        assertEquals(List.of(tag), slot.getTagsList());
        assertEquals(count, slot.getMinimumCount());
        assertEquals(count, slot.getMaximumCount());
    }
}
