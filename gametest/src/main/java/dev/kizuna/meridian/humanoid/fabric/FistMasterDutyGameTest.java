package dev.kizuna.meridian.humanoid.fabric;

import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.protocol.pb.BodyMessages;
import dev.kizuna.meridian.protocol.pb.BodyMessages.MeridianOpeningStage;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Hand;
import dev.kizuna.meridian.technique.server.TechniqueBinder;
import dev.kizuna.meridian.technique.server.TechniqueSelector;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 拳师的职守和打法：村民成了拳师就上任（会上卷、两臂练到位、不退），不再是拳师就撤任，凡人拳师不任职；
 * 对手举盾时只出背摔，被围时先出撩天。用例放在 Threatening 的包里，借它的包内入口看身体和当前施放。
 */
public final class FistMasterDutyGameTest {
    private static final ResourceKey<VillagerProfession> FIST_MASTER = ResourceKey.create(
            Registries.VILLAGER_PROFESSION, Identifier.parse("meridian_villager:fist_master"));
    private static final String PREFIX = "meridian_villager:";
    private static final String THROW = PREFIX + "shoulder_throw";
    private static final String LIFT = PREFIX + "golem_lift";
    private static final List<String> OUTER = List.of(PREFIX + "crushing_fist", LIFT, THROW);
    private static final String ARM_MUSCLE = "human:arm_muscle";
    private static final double WANDERING_QI_GROWTH = 0.20;
    private static final String CRUSHING_FIST = PREFIX + "crushing_fist";
    private static final double EPSILON = 1e-9;
    private static final int FLOOR_Y = 1;
    private static final int STRUCTURE_SIZE = 8;
    private static final int SETTLE_TICKS = 20;
    private static final int APPOINT_TIMEOUT = 100;
    private static final int FIGHT_TIMEOUT = 700;
    private static final int TRAIL = 12;
    private static final int BIRTH_ATTEMPTS = 40;
    private static final String HAND_MERIDIAN = "human:hand_meridian";
    private static final int APPOINTED_HANDS = 1;
    private static final String INCH_POWER = "meridian_villager:inch_power";
    private static final String FOOT_MERIDIAN = "human:foot_meridian";
    private static final int OPENING_SAMPLES = 3;
    private static final double DUMMY_HEALTH = 1000;
    private static final double CLEAR_RADIUS = 40;
    private static final BlockPos CENTER = new BlockPos(3, 2, 3);

    @GameTest(maxTicks = APPOINT_TIMEOUT)
    public void villagerTakingTheJobIsAppointedAndLeavingItDismissed(GameTestHelper context) {
        // 一条手经都没开的游炁村民成了拳师：会上卷三式和中卷；上任前上卷三式就放得出（只靠肌肉，不花真元、
        // 不占经脉），中卷的寸劲绑不上（内家要开着一条手经）；上任后保底开了一条手经，寸劲绑得上了，
        // 两臂训练成长到位、挂上不退；不再是拳师，不退撤掉，开的脉和练出来的两臂留着。
        // 凡人成了拳师不任职、身体不动，但上卷三式照样放得出。都不开 AI，职业由用例直接改。
        // 第三个村民不是拳师，「不退」「守村」是别处给他挂的：本仓对职守时不动他的。
        Villager master = villager(context, HumanoidTier.MORTAL, new BlockPos(2, 2, 2), true /* idle */,
                Optional.empty());
        // 出生抽到什么和所在区域有关，不能指望一定抽得到没开手经的游炁村民：拿凡人的身体先开一条足经，
        // 它就是一个游炁、手经一条没开的村民。
        HumanoidTechniqueHost.prepare(master).body().openOne(FOOT_MERIDIAN, HumanoidFabric.config().openedLumenGain());
        context.assertTrue(HumanoidTiers.of(master).equals(Optional.of(HumanoidTier.WANDERING_QI)) && !handOpened(master),
                "用例要的是游炁、没开手经的村民");
        Villager mortal = villager(context, HumanoidTier.MORTAL, new BlockPos(5, 2, 5), true /* idle */,
                Optional.empty());
        Villager outsider = villager(context, HumanoidTier.MORTAL, new BlockPos(5, 2, 2), true /* idle */,
                Optional.empty());
        HumanoidDuties.standGround(outsider, true);
        HumanoidDuties.guardVillage(outsider, true);
        List<BodyMessages.TissueState> before = body(master).getTissueStatesList();
        List<BodyMessages.TissueState> mortalBefore = body(mortal).getTissueStatesList();
        Husk opponent = dummy(context, new BlockPos(2, 2, 3));
        context.assertTrue(candidates(master, opponent).containsAll(OUTER) && !bindable(master, INCH_POWER),
                "没开手经的游炁村民应当放得出上卷三式、绑不上中卷的寸劲：" + candidates(master, opponent));
        employ(context, master, FIST_MASTER);
        employ(context, mortal, FIST_MASTER);
        context.runAfterDelay(SETTLE_TICKS, () -> {
            context.assertTrue(HumanoidDuties.standsGround(master) && HumanoidDuties.guardsVillage(master),
                    "拳师上任后没有挂上不退和守村");
            context.assertTrue(HumanoidMeleeGuard.enabled(master), "拳师没有登记近身守卫策略");
            context.assertTrue(handOpened(master) && bindable(master, CRUSHING_FIST), "拳师上任后还是用不了拳");
            context.assertTrue(handsOpened(master) == APPOINTED_HANDS && bindable(master, INCH_POWER),
                    "拳师上任后应保底开着一条手经、绑得上中卷的寸劲，实际开了 " + handsOpened(master));
            verifyArms(context, master, before, WANDERING_QI_GROWTH);
            List<String> known = TechniqueFabric.service().known(master.getUUID()).stream()
                    .map(entry -> entry.technique().getId()).toList();
            context.assertTrue(known.containsAll(OUTER), "拳师不会上卷三式：" + known);
            List<String> castable = candidates(master, opponent);
            context.assertTrue(castable.containsAll(OUTER), "拳师放不出上卷三式：" + castable);
            List<String> mortalCastable = candidates(mortal, opponent);
            context.assertTrue(mortalCastable.containsAll(OUTER), "凡人村民放不出上卷三式：" + mortalCastable);
            opponent.discard();
            context.assertTrue(!HumanoidDuties.standsGround(mortal) && !HumanoidDuties.guardsVillage(mortal),
                    "凡人拳师不该任职");
            context.assertTrue(!HumanoidMeleeGuard.enabled(mortal) && !HumanoidMeleeGuard.enabled(outsider),
                    "凡人或未任职村民获得了近身守卫策略");
            context.assertTrue(body(mortal).getTissueStatesList().equals(mortalBefore) && !handOpened(mortal),
                    "凡人拳师的身体被动过");
            context.assertTrue(HumanoidDuties.standsGround(outsider) && HumanoidDuties.guardsVillage(outsider),
                    "不是拳师的村民，别处挂的不退和守村被本仓撤掉了");
            employ(context, master, VillagerProfession.NONE);
            context.runAfterDelay(SETTLE_TICKS, () -> {
                context.assertTrue(!HumanoidDuties.standsGround(master) && !HumanoidDuties.guardsVillage(master),
                        "不再是拳师，不退和守村没有撤掉");
                context.assertTrue(!master.entityTags().contains(HumanoidDuties.MELEE_GUARD_TAG),
                        "卸任后仍有近身守卫策略");
                context.assertTrue(handsOpened(master) == APPOINTED_HANDS, "不再是拳师，开的手经不该收回");
                verifyArms(context, master, before, WANDERING_QI_GROWTH);
                master.discard();
                mortal.discard();
                outsider.discard();
                context.succeed();
            });
        });
    }

    @GameTest(environment = "meridian_villager_test:fist_master_shield", maxTicks = FIGHT_TIMEOUT)
    public void fistMastersOpenWithTheThrowAgainstAShield(GameTestHelper context) {
        checkOpenings(context, Matchup.SHIELD);
    }

    @GameTest(environment = "meridian_villager_test:fist_master_crowd", maxTicks = FIGHT_TIMEOUT)
    public void surroundedFistMastersOpenWithTheLift(GameTestHelper context) {
        checkOpenings(context, Matchup.CROWD);
    }

    private enum Matchup {
        SHIELD(THROW), CROWD(LIFT);

        final String opening;

        Matchup(String opening) {
            this.opening = opening;
        }
    }

    private static void checkOpenings(GameTestHelper context, Matchup matchup) {
        floor(context);
        clearMobs(context);
        var sequence = context.startSequence();
        for (int sample = 1; sample <= OPENING_SAMPLES; sample++) {
            var bout = new OpeningBout(context, matchup, sample);
            sequence.thenExecute(bout::setUp)
                    .thenWaitUntil(bout::requireReady)
                    .thenExecute(() -> bout.master.setNoAi(false /* aiDisabled */))
                    .thenWaitUntil(bout::observeOpening)
                    .thenExecute(bout::verifyOpening);
        }
        sequence.thenSucceed();
    }

    /** 一场只留本拳师和它的对手，从开 AI 的那个 tick 开始读首次施放。 */
    private static final class OpeningBout {
        private final GameTestHelper context;
        private final Matchup matchup;
        private final int sample;
        private Villager master;
        private List<Husk> opponents;
        private CastRequest first;

        OpeningBout(GameTestHelper context, Matchup matchup, int sample) {
            this.context = context;
            this.matchup = matchup;
            this.sample = sample;
        }

        void setUp() {
            var level = context.getLevel();
            master = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            // 正式选式按世界种子与实体 UUID 加权抽样。每种局面用三个固定样本，保留目录中的备用式。
            String name = matchup.name() + "/" + sample;
            UUID identity = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
            master.setUUID(new UUID(identity.getMostSignificantBits() ^ level.getSeed(),
                    identity.getLeastSignificantBits()));
            Vec3 position = context.absoluteVec(Vec3.atBottomCenterOf(CENTER));
            master.snapTo(position.x, position.y, position.z, 0, 0);
            master.setNoAi(true /* aiDisabled */);
            // 对手本来就在起手距离内。等错相决策的几 tick 内保持站位，避免走动、挤碰给首击预测带入速度。
            master.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            master.setPersistenceRequired();
            HumanoidSpeciesBirths.birth(master, HumanoidTier.WANDERING_QI);
            context.assertTrue(level.addFreshEntity(master), "拳师未加入世界");
            master.setVillagerXp(1);
            employ(context, master, FIST_MASTER);

            Husk opponent = dummy(context, CENTER.east());
            if (matchup == Matchup.SHIELD) {
                opponent.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                opponent.startUsingItem(InteractionHand.OFF_HAND);
                opponents = List.of(opponent);
            } else {
                opponents = List.of(opponent, dummy(context, CENTER.south()));
            }
            opponents.forEach(husk -> husk.setTarget(master));
        }

        void requireReady() {
            context.assertTrue(HumanoidDuties.standsGround(master) && HumanoidMeleeGuard.enabled(master), "拳师尚未任职");
            requireMatchup();
            for (Husk husk : opponents) {
                var castable = candidates(master, husk);
                context.assertTrue(castable.containsAll(OUTER), "开打前上卷三式没有全部进入候选：" + castable);
            }
        }

        void requireMatchup() {
            context.assertTrue(opponents.stream().allMatch(husk -> husk.getTarget() == master), "尸壳没有盯住拳师");
            context.assertTrue(HumanoidThreats.surrounded(master) == (matchup == Matchup.CROWD), "围攻人数不符");
            if (matchup == Matchup.SHIELD) {
                context.assertTrue(opponents.getFirst().isBlocking(), "对手没有举稳盾");
            }
        }

        void observeOpening() {
            // 调息等准备式不算出拳。读到任一袖手拳就结束等待，同一 tick 验它和实际对手。
            first = HumanoidTechniqueHost.activeCast(master)
                    .map(active -> active.getRequest())
                    .filter(request -> request.getTechnique().getId().startsWith(PREFIX))
                    .orElseThrow(() -> context.assertionException(Component.literal(
                            "第 " + sample + " 个拳师还没出手" + engagement(master) + trail(master))));
        }

        void verifyOpening() {
            requireMatchup();
            String targetId = first.getTargetId();
            context.assertTrue(opponents.stream().anyMatch(husk -> husk.getStringUUID().equals(targetId)),
                    "首式选了场外对手：" + targetId);
            String actual = first.getTechnique().getId();
            context.assertTrue(matchup.opening.equals(actual),
                    "第 " + sample + " 个拳师首式应为 " + matchup.opening + "，实际为 " + actual + trail(master));
            master.discard();
            opponents.forEach(Entity::discard);
        }
    }

    private static void verifyArms(GameTestHelper context, Villager villager, List<BodyMessages.TissueState> before,
                                   double growth) {
        // 声明了训练的两臂肌肉都到「给定值和上限里小的那个」（不低于原值），别的组织没变。
        BodyMessages.BodySnapshot body = body(villager);
        int arms = 0;
        for (int index = 0; index < body.getTissueStatesCount(); index++) {
            BodyMessages.TissueDefinition definition = body.getStructure().getTissues(index);
            boolean arm = definition.getTagsList().contains(ARM_MUSCLE) && definition.hasTraining();
            double was = before.get(index).getTrainingGrowth();
            double expected = arm ? Math.max(was, Math.min(growth, definition.getTraining().getMaximumGrowth())) : was;
            double actual = body.getTissueStates(index).getTrainingGrowth();
            context.assertTrue(Math.abs(actual - expected) < EPSILON, definition.getId() + " 的训练成长是 " + actual
                    + "，应当是 " + expected);
            arms += arm ? 1 : 0;
        }
        context.assertTrue(arms > 0, "身体里没有带训练定义的两臂肌肉，这条用例什么都没验");
    }

    private static String diagnosis(Villager villager) {
        // 大脑读到的自身状况和进攻手段：一直在躲的时候看得出是伤、是累、还是没有放得出的式。
        var body = BodyProtobuf.fromMessage(body(villager));
        var readout = HumanoidRepertoires.read(villager, body, Optional.empty(), technique -> false);
        return " 状况=" + HumanoidFighters.condition(body) + " 手段=" + readout.means() + " 打击式候选="
                + readout.strikes().stream().map(known -> known.getTechnique().getId()).toList()
                + " 姿态=" + HumanoidBrain.stance(villager.getUUID());
    }

    /** 大脑选式时认不认为这个对手倒在地上（和选式同一个入口；self 是出手的一方）。别的包里的用例也能调。 */
    public static boolean seesDowned(Mob self, LivingEntity target) {
        var opponent = new HumanoidOpponent(target, TechniqueFabric.service().observe(target.getUUID()));
        return HumanoidRepertoires.facts(self, opponent).contains(TechniqueSelector.CombatFact.TARGET_DOWNED);
    }

    /** 失败信息用：大脑现在认谁当对手（是什么、在哪、多远）、在不在指挥。别的包里的用例也能调。 */
    public static String engagement(Villager villager) {
        return "；对手=" + HumanoidBrain.opponent(villager).map(opponent -> opponent.getType().toShortString()
                + " 在 " + opponent.blockPosition().toShortString() + " 相距 " + villager.distanceTo(opponent))
                + " 大脑在指挥=" + HumanoidBrain.directing(villager);
    }

    /** 失败信息用：这个村民的大脑最近做了哪些决策、带没带着不退。别的包里的用例也能调。 */
    public static String trail(Villager villager) {
        List<String> recent = HumanoidBrain.decisions(villager.getUUID()).stream()
                .map(logged -> logged.decision().action() + "(" + logged.decision().reason() + ")")
                .toList();
        return "；最近的决策 " + recent.subList(Math.max(0, recent.size() - TRAIL), recent.size())
                + " 不退=" + HumanoidDuties.standsGround(villager)
                + " 最近一次施放失败=" + HumanoidBrain.lastFailure(villager.getUUID()) + diagnosis(villager);
    }

    static List<String> candidates(Villager villager, LivingEntity opponent) {
        // 大脑现在把哪几式算成对这个对手的打击式候选（绑得上、付得起、做得动）。
        var body = BodyProtobuf.fromMessage(body(villager));
        return HumanoidRepertoires.read(villager, body, Optional.of(new HumanoidOpponent(opponent, Optional.empty())),
                technique -> false).strikes().stream().map(known -> known.getTechnique().getId()).toList();
    }

    private static boolean bindable(Villager villager, String technique) {
        // 这一式在这个身体上绑不绑得上（不看付不付得起）。
        var definition = TechniqueFabric.service().registry().findLatest(technique).orElseThrow();
        return TechniqueBinder.bind(BodyProtobuf.fromMessage(body(villager)), definition, Hand.HAND_UNSPECIFIED,
                HumanoidWayfarerWeapons.inspect(villager).getMainHandSide()).hasBinding();
    }

    private static BodyMessages.BodySnapshot body(Villager villager) {
        return HumanoidTechniqueHost.prepare(villager).body().snapshot();
    }

    private static void employ(GameTestHelper context, Villager villager, ResourceKey<VillagerProfession> profession) {
        // 直接改职业，和原版认领、丢掉工作站点时走的是同一个入口。
        villager.setVillagerData(villager.getVillagerData()
                .withProfession(context.getLevel().registryAccess(), profession));
    }

    static Villager villager(GameTestHelper context, HumanoidTier tier, BlockPos relative, boolean idle,
                                     Optional<Boolean> hand) {
        // 指定档位出生再加入世界；idle 的不开 AI，只看上任撤任。
        // 游炁村民大约五个里有一个一条手经都没开。hand 写了的话只要「开着手经」或「没开手经」的那种，不合的丢掉重出。
        for (int attempt = 0; attempt < BIRTH_ATTEMPTS; attempt++) {
            Villager villager = EntityTypes.VILLAGER.create(context.getLevel(), EntitySpawnReason.COMMAND);
            Vec3 position = Vec3.atBottomCenterOf(context.absolutePos(relative));
            villager.snapTo(position.x, position.y, position.z, 0, 0);
            villager.setNoAi(idle);
            villager.setPersistenceRequired();
            HumanoidSpeciesBirths.birth(villager, tier);
            if (hand.isEmpty() || hand.get() == handOpened(villager)) {
                context.assertTrue(context.getLevel().addFreshEntity(villager), "村民未加入世界");
                return villager;
            }
            villager.discard();
        }
        throw context.assertionException(Component.literal("连出 " + BIRTH_ATTEMPTS + " 个村民都不是要的那种"));
    }

    /** 这个村民的身体有没有至少一条开了的手经（袖手拳三式都要）。身体已经出生才能问。别的包里的用例也能调。 */
    public static boolean handOpened(Villager villager) {
        return handsOpened(villager) > 0;
    }

    private static int handsOpened(Villager villager) {
        // 开着的手经条数。
        BodyMessages.BodySnapshot body = ((HumanoidEntityAccess) villager).meridian$humanoidBody().snapshot();
        int opened = 0;
        for (int index = 0; index < body.getMeridianStatesCount(); index++) {
            boolean hand = body.getStructure().getMeridians(index).getTagsList().contains(HAND_MERIDIAN);
            if (hand && body.getMeridianStates(index).getOpening().getStage() == MeridianOpeningStage.OPENED) {
                opened++;
            }
        }
        return opened;
    }

    static Husk dummy(GameTestHelper context, BlockPos relative) {
        // 不动、不出声的高血量尸壳，只当敌意来源。
        Husk husk = context.spawn(EntityTypes.HUSK, relative);
        husk.setNoAi(true /* aiDisabled */);
        husk.setSilent(true /* silent */);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(DUMMY_HEALTH);
        husk.setHealth((float) DUMMY_HEALTH);
        return husk;
    }

    private static void clearMobs(GameTestHelper context) {
        // 打法用例各占一个批次，先移除前面批次留在附近的生物。
        AABB area = new AABB(context.absolutePos(CENTER)).inflate(CLEAR_RADIUS);
        context.getLevel().getEntitiesOfClass(Mob.class, area, mob -> true).forEach(Entity::discard);
    }

    private static void floor(GameTestHelper context) {
        // 整个结构底下铺满一层石头。
        for (int x = 0; x < STRUCTURE_SIZE; x++) {
            for (int z = 0; z < STRUCTURE_SIZE; z++) {
                context.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
            }
        }
    }
}
