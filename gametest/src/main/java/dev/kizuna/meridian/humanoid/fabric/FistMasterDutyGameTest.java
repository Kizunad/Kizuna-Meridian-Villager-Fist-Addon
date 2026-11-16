package dev.kizuna.meridian.humanoid.fabric;

import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.protocol.pb.BodyMessages;
import dev.kizuna.meridian.protocol.pb.BodyMessages.MeridianOpeningStage;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Hand;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueProgress;
import dev.kizuna.meridian.technique.server.TechniqueBinder;
import dev.kizuna.meridian.technique.server.TechniqueSelector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final int SHIELD_UP_TICKS = 10;
    private static final int WAKE_TICKS = 10;
    private static final Map<UUID, String> AT_WAKE = new HashMap<>();
    private static final double DUMMY_HEALTH = 1000;
    private static final double CLEAR_RADIUS = 40;
    private static final BlockPos CENTER = new BlockPos(3, 2, 3);
    /** 三个拳师各占结构一角，互相隔开 4 格以上；对手摆在各自身边。 */
    private static final List<BlockPos> SPOTS = List.of(new BlockPos(1, 2, 1), new BlockPos(6, 2, 1),
            new BlockPos(3, 2, 6));

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
            context.assertTrue(body(mortal).getTissueStatesList().equals(mortalBefore) && !handOpened(mortal),
                    "凡人拳师的身体被动过");
            context.assertTrue(HumanoidDuties.standsGround(outsider) && HumanoidDuties.guardsVillage(outsider),
                    "不是拳师的村民，别处挂的不退和守村被本仓撤掉了");
            employ(context, master, VillagerProfession.NONE);
            context.runAfterDelay(SETTLE_TICKS, () -> {
                context.assertTrue(!HumanoidDuties.standsGround(master) && !HumanoidDuties.guardsVillage(master),
                        "不再是拳师，不退和守村没有撤掉");
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
        // 三个拳师各对着一只一直举盾、不动不还手的尸壳：拳打在盾上会被挡，第一式都是背摔。
        // 三个一起看，免得单个碰巧抽中。
        floor(context);
        clearMobs(context);
        List<Villager> masters = new ArrayList<>();
        Map<Integer, String> firstCast = new HashMap<>();
        List<Husk> shielded = new ArrayList<>();
        List<Map<String, String>> casts = new ArrayList<>();
        for (BlockPos position : SPOTS) {
            Husk husk = dummy(context, position.east());
            husk.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            husk.startUsingItem(InteractionHand.OFF_HAND);
            shielded.add(husk);
            casts.add(new LinkedHashMap<>());
        }
        // 盾举起来要过几 tick 才算挡住；等盾都举稳了再放拳师进来，免得第一次决策时对面还不算举盾。
        context.runAfterDelay(SHIELD_UP_TICKS, () -> {
            for (int index = 0; index < SPOTS.size(); index++) {
                masters.add(recruit(context, SPOTS.get(index), List.of(shielded.get(index)), THROW));
            }
            wake(context, masters);
        });
        context.succeedWhen(() -> {
            context.assertTrue(masters.size() == SPOTS.size(), "拳师还没进场");
            for (int index = 0; index < masters.size(); index++) {
                List<String> techniques = castsSoFar(context, masters.get(index), casts.get(index));
                if (techniques.isEmpty()) {
                    // 还没出手时记下这一刻背摔的状况。出手那一 tick 不再记，留下的就是付钱之前的样子。
                    firstCast.put(index, throwState(masters.get(index), shielded.get(index)));
                }
                String where = "第 " + index + " 个拳师";
                context.assertTrue(!techniques.isEmpty(), where + "还没出手" + trail(masters.get(index)));
                context.assertTrue(shielded.get(index).isBlocking(), where + "对面的盾放下了，这条用例不成立");
                context.assertTrue(techniques.getFirst().equals(THROW), where + "对着盾的第一式不是背摔：" + techniques
                        + "；出手前一刻背摔的状况 " + firstCast.get(index) + "；开打那一刻"
                        + AT_WAKE.get(masters.get(index).getUUID()) + " 对手位置=" + shielded.get(index).position()
                        + trail(masters.get(index)));
            }
        });
    }

    @GameTest(environment = "meridian_villager_test:fist_master_crowd", maxTicks = FIGHT_TIMEOUT)
    public void surroundedFistMastersOpenWithTheLift(GameTestHelper context) {
        // 三个拳师各被两只尸壳贴身盯着：被围，第一式都是撩天。三个一起看，免得单个碰巧抽中。
        floor(context);
        clearMobs(context);
        List<Villager> masters = new ArrayList<>();
        List<Map<String, String>> casts = new ArrayList<>();
        for (BlockPos position : SPOTS) {
            List<Husk> husks = List.of(dummy(context, position.east()), dummy(context, position.south()));
            masters.add(recruit(context, position, husks, LIFT));
            casts.add(new LinkedHashMap<>());
        }
        wake(context, masters);
        context.succeedWhen(() -> {
            for (int index = 0; index < masters.size(); index++) {
                List<String> techniques = castsSoFar(context, masters.get(index), casts.get(index));
                context.assertTrue(!techniques.isEmpty(), "第 " + index + " 个拳师还没出手" + trail(masters.get(index)));
                context.assertTrue(techniques.getFirst().equals(LIFT), "被围的拳师第一式不是撩天：" + techniques
                        + "；开打那一刻" + AT_WAKE.get(masters.get(index).getUUID()) + trail(masters.get(index)));
            }
        });
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

    private static String throwState(Villager villager, Husk opponent) {
        // 背摔这一刻为什么可用或不可用：绑定、付费、是不是候选、冷却还剩多久、离对手多远、对手是不是举着盾、算不算被围。
        var message = body(villager);
        var body = BodyProtobuf.fromMessage(message);
        var definition = TechniqueFabric.service().registry().findLatest(THROW).orElseThrow();
        var binding = TechniqueBinder.bind(body, definition, Hand.HAND_UNSPECIFIED,
                HumanoidWayfarerWeapons.inspect(villager).getMainHandSide());
        String bound = binding.hasBinding() ? "绑得上" : "绑不上（" + binding.getReason() + "）";
        boolean payable = binding.hasBinding() && HumanoidRepertoires.payable(body, definition, binding.getBinding());
        TechniqueProgress progress = HumanoidTechniqueHost.prepare(villager).body().techniqueProgress();
        long cooldown = progress.getCooldownsOrDefault(THROW, 0) - body.settledAt().elapsedMillis();
        return "[" + bound + " 付得起=" + payable + " 是候选=" + candidates(villager, opponent).contains(THROW)
                + " 冷却还剩毫秒=" + Math.max(0, cooldown) + " 距离=" + String.format("%.2f", villager.distanceTo(opponent))
                + " 对手举盾=" + opponent.isBlocking() + " 被围=" + HumanoidThreats.surrounded(villager)
                + " 对手=" + HumanoidBrain.decisions(villager.getUUID()).stream().reduce((first, last) -> last)
                        .flatMap(HumanoidMind.Logged::opponent).map(entity -> entity == opponent).orElse(false) + "]";
    }

    private static String diagnosis(Villager villager) {
        // 大脑读到的自身状况和进攻手段：一直在躲的时候看得出是伤、是累、还是没有放得出的式。
        var body = BodyProtobuf.fromMessage(body(villager));
        var readout = HumanoidRepertoires.read(villager, body, Optional.empty(), technique -> false);
        return " 状况=" + HumanoidFighters.condition(body) + " 手段=" + readout.means() + " 打击式候选="
                + readout.strikes().stream().map(known -> known.getTechnique().getId()).toList()
                + " 姿态=" + HumanoidBrain.stance(villager.getUUID());
    }

    private static void watch(Villager villager, Map<String, String> casts) {
        // 记下拳师出过的每一次袖手拳施放：施放 ID → 哪一式，按先后；调息、轻步这类准备式不算出手。
        TechniqueProgress progress = HumanoidTechniqueHost.prepare(villager).body().techniqueProgress();
        if (progress.hasActiveCast()) {
            var request = progress.getActiveCast().getRequest();
            if (request.getTechnique().getId().startsWith(PREFIX)) {
                casts.putIfAbsent(request.getCastId(), request.getTechnique().getId());
            }
        }
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

    private static Villager fistMaster(GameTestHelper context, BlockPos relative, Husk opponent, String technique) {
        // 开着 AI 的游炁拳师。袖手拳只靠肌肉，出生是什么样就是什么样：不挑开了哪些经脉、不补真元，
        // 上卷三式都该放得出，放不出就是用例环境坏了。
        // 原版会把没有工作站点、又没做过买卖的村民打回无业，给一点交易经验职业才留得住。
        Villager master = villager(context, HumanoidTier.WANDERING_QI, relative, false /* idle */, Optional.empty());
        List<String> castable = candidates(master, opponent);
        context.assertTrue(castable.contains(technique), "游炁拳师放不出 " + technique + "：" + castable);
        // 先不开 AI：等对手都盯上它、职守也挂好了再放开，免得第一次决策时还不算被围、还没上任。
        master.setNoAi(true /* aiDisabled */);
        master.setVillagerXp(1);
        employ(context, master, FIST_MASTER);
        return master;
    }

    private static Villager recruit(GameTestHelper context, BlockPos spot, List<Husk> opponents, String technique) {
        // 招一个当场放得出这一式的拳师站到这个位置，让这里的对手都盯上它。
        Villager master = fistMaster(context, spot, opponents.getFirst(), technique);
        opponents.forEach(husk -> husk.setTarget(master));
        return master;
    }

    private static List<String> castsSoFar(GameTestHelper context, Villager master, Map<String, String> casts) {
        // 开打以后才看；返回到现在出过的袖手拳，按先后。
        context.assertTrue(!master.isNoAi(), "拳师还没开打");
        watch(master, casts);
        return new ArrayList<>(casts.values());
    }

    private static void wake(GameTestHelper context, List<Villager> masters) {
        // 对手设好目标、上任处理完以后再开 AI，拳师的第一次决策看到的就是摆好的局面。
        context.runAfterDelay(WAKE_TICKS, () -> masters.forEach(master -> {
            context.assertTrue(HumanoidDuties.standsGround(master), "开打前拳师还没上任");
            // 开打那一刻大脑读到的状况和候选记下来：第一式选错时看得出当时是哪一式不在候选里。
            AT_WAKE.put(master.getUUID(), diagnosis(master) + " 被围=" + HumanoidThreats.surrounded(master)
                    + " 位置=" + master.position());
            master.setNoAi(false /* aiDisabled */);
        }));
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
