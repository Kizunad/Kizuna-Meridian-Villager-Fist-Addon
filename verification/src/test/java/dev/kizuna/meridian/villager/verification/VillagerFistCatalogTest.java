package dev.kizuna.meridian.villager.verification;

import dev.kizuna.meridian.protocol.pb.BodyMessages.Realm;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ArmSelection;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.BlockWork;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Category;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Grapple;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.LegKnockbackKind;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.LimbStrike;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ModuleKind;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Numb;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ScrollDrop;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueSlot;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Throw;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.WorkCost;
import dev.kizuna.meridian.technique.server.TechniqueLimbStrikes;
import dev.kizuna.meridian.technique.server.TechniqueRegistry;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 发行目录经正式 Human 内容和功法库校验器注册，逐式对照 C7 SPEC 第三、六节的表。 */
public final class VillagerFistCatalogTest {
    private static final int ALL_TECHNIQUES = 6;
    private static final String HAND_MERIDIAN = "human:hand_meridian";
    private static final String BOOK = "meridian_villager:sleeve_fist";
    private static final String OUTER = "meridian_villager:outer";
    private static final String CRUSHING_FIST = "meridian_villager:crushing_fist";
    private static final String GOLEM_LIFT = "meridian_villager:golem_lift";
    private static final String SHOULDER_THROW = "meridian_villager:shoulder_throw";
    private static final String DUMMY_DRILL = "meridian_villager:dummy_drill";
    private static final List<String> OUTER_TECHNIQUES = List.of(CRUSHING_FIST, GOLEM_LIFT, SHOULDER_THROW,
            DUMMY_DRILL);
    private static final String INNER = "meridian_villager:inner";
    private static final String INCH_POWER = "meridian_villager:inch_power";
    private static final String WRIST_LOCK = "meridian_villager:wrist_lock";
    private static final List<String> INNER_TECHNIQUES = List.of(INCH_POWER, WRIST_LOCK);
    private static final String INCH_TYPE = "meridian_villager:inch_power";
    private static final long INCH_MILLIS = 1_450;
    private static final double INCH_MULTIPLIER = 1.6;
    private static final double INCH_REACH = 1.5;
    private static final double INCH_ORGAN_SHARE = 0.6;
    private static final double INCH_ARMOR = 0.5;
    private static final String LOCK_TYPE = "meridian_villager:joint_lock";
    private static final long LOCK_GRAPPLE_MILLIS = 900;
    private static final long LOCK_HOLD_MILLIS = 1_600;
    private static final List<Long> LOCK_TWIST_MILLIS = List.of(1_400L, 2_100L);
    private static final List<Double> LOCK_MULTIPLIERS = List.of(0.6, 0.9);
    private static final double NUMB_MULTIPLIER = 0.3;
    private static final long NUMB_MILLIS = 6_000;
    private static final String GRAPPLE_PROCESSOR = "meridian_villager:grapple";
    private static final String THROW_TYPE = "meridian_villager:throw";
    private static final long GRAPPLE_MILLIS = 1_200;
    private static final long THROW_MILLIS = 2_200;
    private static final long THROW_ACTIVE_MILLIS = 1_400;
    private static final long THROW_RECOVERY_MILLIS = 3_400;
    private static final long THROW_COOLDOWN_MILLIS = 5_000;
    private static final long HOLD_MILLIS = 1_000;
    private static final long LEAN_DELAY_MILLIS = 300;
    private static final long FLIGHT_MILLIS = 400;
    private static final long DOWN_MILLIS = 1_500;
    private static final long DOWN_IMMUNITY_MILLIS = 3_000;
    private static final double GRAPPLE_REACH = 2;
    private static final double HOLD_DISTANCE = 1;
    private static final double LANDING_DISTANCE = 2;
    private static final double APEX_HEIGHT = 0.5;
    private static final double THROW_MULTIPLIER = 2;
    private static final double LEAN_MULTIPLIER = 0.4;
    private static final double LEAN_PUSH = 1;
    private static final double SCROLL_CHANCE = 0.25;
    private static final int ARM_MUSCLES = 6;
    private static final int LEG_MUSCLES = 6;
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
        // 一书两卷。上卷：三式打人加打木桩，没有前置卷，生成卷轴并由村民掉落。
        TechniqueRegistry registry = VillagerFistContent.registry();
        assertEquals(1, registry.books().size());
        assertEquals(BOOK, registry.books().getFirst().getId());
        assertEquals(2, registry.volumes().size());
        var outer = registry.findVolume(OUTER).orElseThrow();
        assertEquals(BOOK, outer.getBookId());
        assertEquals(OUTER_TECHNIQUES, outer.getTechniquesList().stream().map(reference -> reference.getId()).toList());
        assertTrue(outer.getPrerequisiteVolumeIdsList().isEmpty());
        assertFalse("上卷要有卷轴", outer.hasScrollItem() && !outer.getScrollItem());
        var drop = ScrollDrop.newBuilder().setEntityType("minecraft:villager").setChance(SCROLL_CHANCE).build();
        assertEquals(List.of(drop), outer.getScrollDropsList());
        assertEquals(OUTER_TECHNIQUES.size() + INNER_TECHNIQUES.size(), registry.definitions().size());
    }

    @Test
    public void innerVolumeNeedsTheOuterAndHasNoScroll() throws IOException {
        // 中卷：寸劲、缠腕，前置上卷，只在武馆传所以不生成卷轴、不掉落。
        TechniqueRegistry registry = VillagerFistContent.registry();
        var inner = registry.findVolume(INNER).orElseThrow();
        assertEquals(BOOK, inner.getBookId());
        assertEquals(INNER_TECHNIQUES, inner.getTechniquesList().stream().map(reference -> reference.getId()).toList());
        assertEquals(List.of(OUTER), inner.getPrerequisiteVolumeIdsList());
        assertTrue("中卷不该有卷轴", inner.hasScrollItem() && !inner.getScrollItem());
        assertTrue(inner.getScrollDropsList().isEmpty());
        for (String id : INNER_TECHNIQUES) {
            TechniqueDefinition definition = registry.findLatest(id).orElseThrow();
            assertEquals(id, Category.ATTACK, definition.getCategory());
            assertEquals(id, FIGHT_MOVEMENT, definition.getMovementSpeedMultiplier(), TOLERANCE);
        }
    }

    @Test
    public void inchPowerSinksThroughArmorWithoutKnockback() throws IOException {
        // 寸劲：1.45 s 前手一拳，×1.6，透劲六成、护甲只算一半，不击退，走直接路。
        TechniqueDefinition definition = VillagerFistContent.registry().findLatest(INCH_POWER).orElseThrow();
        TechniqueEffect effect = only(definition, TechniqueEffect::hasLimbStrike);
        LimbStrike strike = effect.getLimbStrike();
        assertEquals(INCH_MILLIS, effect.getAtMillis());
        assertEquals(ArmSelection.FRONT_ARM, strike.getArm());
        assertEquals(INCH_TYPE, strike.getDamageTypeId());
        assertEquals(INCH_MULTIPLIER, strike.getMultiplier().getValue(), TOLERANCE);
        assertEquals(INCH_REACH, strike.getDistance().getValue(), TOLERANCE);
        assertEquals(LegKnockbackKind.NONE, strike.getKnockbackKind());
        assertEquals(INCH_ORGAN_SHARE, strike.getPenetration().getOrganShare().getValue(), TOLERANCE);
        assertEquals(INCH_ARMOR, strike.getPenetration().getArmorMultiplier().getValue(), TOLERANCE);
        assertTrue(TechniqueLimbStrikes.direct(strike));
        assertEquals(INCH_MILLIS, definition.getPhases().getWarmupMillis());
    }

    @Test
    public void wristLockHoldsThenWrenchesTheArmedForearmTwice() throws IOException {
        // 缠腕：0.90 s 擒到 2.50 s 放手；1.40、2.10 两下拧对方持械前臂，×0.6、×0.9，整条手臂麻 6 s，第二下卸兵器。
        TechniqueDefinition definition = VillagerFistContent.registry().findLatest(WRIST_LOCK).orElseThrow();
        TechniqueEffect hold = only(definition, TechniqueEffect::hasGrapple);
        assertEquals(LOCK_GRAPPLE_MILLIS, hold.getAtMillis());
        assertEquals(LOCK_HOLD_MILLIS, hold.getGrapple().getDurationMillis());
        assertFalse("缠腕摔不动就只拧，不带一靠", hold.getGrapple().hasLean());
        List<TechniqueEffect> twists = definition.getEffectsList().stream()
                .filter(TechniqueEffect::hasLimbStrike).toList();
        assertEquals(LOCK_TWIST_MILLIS, twists.stream().map(TechniqueEffect::getAtMillis).toList());
        assertEquals(LOCK_MULTIPLIERS, twists.stream()
                .map(twist -> twist.getLimbStrike().getMultiplier().getValue()).toList());
        for (TechniqueEffect twist : twists) {
            LimbStrike strike = twist.getLimbStrike();
            assertEquals(ArmSelection.BOTH_ARMS, strike.getArm());
            assertEquals(LOCK_TYPE, strike.getDamageTypeId());
            assertTrue(strike.getArmedForearm());
            Numb numb = strike.getAttachments(0).getNumb();
            assertEquals(NUMB_MULTIPLIER, numb.getMultiplier().getValue(), TOLERANCE);
            assertEquals(NUMB_MILLIS, numb.getDurationMillis());
            assertTrue(numb.getWholeArm());
        }
        assertFalse(twists.get(0).getLimbStrike().getDisarm());
        assertTrue(twists.get(1).getLimbStrike().getDisarm());
    }

    @Test
    public void bookFightsBareHandedAnywhere() throws IOException {
        // 书级拳掌规则：不看持物；不要求着地（用户 11-15：站在地上才能出拳没必要），跳起、下落中都能出拳。
        var book = VillagerFistContent.registry().books().getFirst();
        assertTrue(book.getArmRules().getIgnoreHeldItems());
        assertFalse(book.getArmRules().getRequireGrounded());
    }

    @Test
    public void strikesUseBothLimbGroups() throws IOException {
        // 崩拳、撩天：游炁起，两臂、两腿各 6 块肌肉，限速 0.4，都是进攻式。
        TechniqueRegistry registry = VillagerFistContent.registry();
        for (String id : STRIKES.keySet()) {
            TechniqueDefinition definition = registry.findLatest(id).orElseThrow();
            assertEquals(id, Category.ATTACK, definition.getCategory());
            assertEquals(id, Realm.WANDERING_QI, definition.getMinimumRealm());
            assertEquals(id, FIGHT_MOVEMENT, definition.getMovementSpeedMultiplier(), TOLERANCE);
            assertMuscles(slot(definition, "arm_muscles"), "human:arm_muscle", ARM_MUSCLES);
            assertMuscles(slot(definition, "leg_muscles"), "human:leg_muscle", LEG_MUSCLES);
        }
    }

    @Test
    public void noTechniqueSpendsQiAndOnlyTheInnerVolumeAsksForAnOpenedHandMeridian() throws IOException {
        // 袖手拳是拳法，只靠肌肉：每一式都不扣真元，打人的式都有肌肉做功。
        // 上卷外家不占经脉槽，一条经都没开的凡人也使得出；中卷内家要开着一条手经（只当门槛，不扣真元）。
        TechniqueRegistry registry = VillagerFistContent.registry();
        List<String> all = registry.definitions().stream().map(TechniqueDefinition::getId).toList();
        assertEquals(ALL_TECHNIQUES, all.size());
        for (String id : all) {
            TechniqueDefinition definition = registry.findLatest(id).orElseThrow();
            assertTrue(id + " 还在扣真元", definition.getCost().getQiList().isEmpty());
            List<TechniqueSlot> meridians = definition.getSlotsList().stream()
                    .filter(slot -> slot.getModule() == ModuleKind.MERIDIAN).toList();
            if (INNER_TECHNIQUES.contains(id)) {
                assertEquals(id + " 应当只有一个手经门槛", 1, meridians.size());
                TechniqueSlot gate = meridians.getFirst();
                assertEquals(id, List.of(HAND_MERIDIAN), gate.getTagsList());
                assertEquals(id, 1, gate.getMinimumCount());
                assertEquals(id, 1, gate.getMaximumCount());
                assertTrue(id + " 的手经门槛没要求开着", gate.getRequireOpened());
            } else {
                assertTrue(id + " 还占着经脉槽", meridians.isEmpty());
            }
            if (definition.getCategory() == Category.ATTACK) {
                assertTrue(id + " 没有肌肉做功", !definition.getCost().getWorkList().isEmpty());
            }
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
    public void shoulderThrowGrabsAtWarmupEndAndThrowsBehind() throws IOException {
        // 背摔：1.20 擒（2 格、拉到 1 格、擒 1 s，摔不动 0.3 s 后一靠 ×0.4 推开 1.0）；
        // 2.20 抛 0.4 s 落身后 2 格，×2.0，倒地 1.5 s，起来后 3 s 免摔。
        TechniqueDefinition definition = VillagerFistContent.registry().findLatest(SHOULDER_THROW).orElseThrow();
        assertEquals(GRAPPLE_MILLIS, definition.getPhases().getWarmupMillis());
        assertEquals(THROW_ACTIVE_MILLIS, definition.getPhases().getActiveMillis());
        assertEquals(THROW_RECOVERY_MILLIS, definition.getPhases().getRecoveryMillis());
        assertEquals(THROW_COOLDOWN_MILLIS, definition.getCooldownMillis());
        assertTrue(definition.getPhases().getInterruptibleByDamage());
        TechniqueEffect grab = only(definition, TechniqueEffect::hasGrapple);
        assertEquals(GRAPPLE_MILLIS, grab.getAtMillis());
        Grapple grapple = grab.getGrapple();
        assertEquals(GRAPPLE_PROCESSOR, grapple.getProcessorId());
        assertEquals(GRAPPLE_REACH, grapple.getMaximumDistance().getValue(), TOLERANCE);
        assertEquals(HOLD_DISTANCE, grapple.getHoldDistance().getValue(), TOLERANCE);
        assertEquals(HOLD_MILLIS, grapple.getDurationMillis());
        assertEquals(LEAN_DELAY_MILLIS, grapple.getLean().getDelayMillis());
        assertEquals(THROW_TYPE, grapple.getLean().getDamageTypeId());
        assertEquals(LEAN_MULTIPLIER, grapple.getLean().getMultiplier().getValue(), TOLERANCE);
        assertEquals(LEAN_PUSH, grapple.getLean().getPush().getValue(), TOLERANCE);
        TechniqueEffect tossed = only(definition, TechniqueEffect::hasThrow);
        assertEquals(THROW_MILLIS, tossed.getAtMillis());
        Throw toss = tossed.getThrow();
        assertEquals(GRAPPLE_PROCESSOR, toss.getProcessorId());
        assertEquals(FLIGHT_MILLIS, toss.getFlightMillis());
        assertEquals(LANDING_DISTANCE, toss.getLandingDistance().getValue(), TOLERANCE);
        assertEquals(APEX_HEIGHT, toss.getApexHeight().getValue(), TOLERANCE);
        assertEquals(THROW_TYPE, toss.getDamageTypeId());
        assertEquals(THROW_MULTIPLIER, toss.getMultiplier().getValue(), TOLERANCE);
        assertEquals(DOWN_MILLIS, toss.getDownMillis());
        assertEquals(DOWN_IMMUNITY_MILLIS, toss.getDownImmunityMillis());
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

    private static TechniqueEffect only(TechniqueDefinition definition, Predicate<TechniqueEffect> kind) {
        // 这一式里这类效果恰好一个。
        List<TechniqueEffect> found = definition.getEffectsList().stream().filter(kind).toList();
        assertEquals(1, found.size());
        return found.getFirst();
    }
}
