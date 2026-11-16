package dev.kizuna.meridian.human.fabric;

import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.CENTER;
import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.HEIGHT;

import dev.kizuna.meridian.core.BodySnapshot;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** 中卷内家两式落到人身上：寸劲透进器官、不把人打退；缠腕拧持械那条前臂、整条手臂发麻、打掉兵器。 */
public final class VillagerFistInnerGameTest {
    private static final String INCH_POWER = VillagerFistTestPlayer.PREFIX + "inch_power";
    private static final String WRIST_LOCK = VillagerFistTestPlayer.PREFIX + "wrist_lock";
    private static final String TARGET_NAME = "inner_target";
    private static final float FACING_NORTH = 180;
    private static final double HALF = 0.5;
    private static final double INCH_DISTANCE = 1.2;
    private static final double LOCK_DISTANCE = 1.6;
    /** 新进世界的玩家有 60 tick 无敌，过了再起式。 */
    private static final int JOIN_INVULNERABILITY_TICKS = 61;
    private static final int INCH_STRIKE_TICKS = 29;
    private static final int FIRST_TWIST_TICKS = 28;
    private static final int SECOND_TWIST_TICKS = 42;
    private static final int SETTLE_TICKS = 3;
    private static final int TIMEOUT_TICKS = 160;
    private static final double ORGAN_SHARE = 0.6;
    private static final double MOST_ORGAN_SHARE = 0.8;
    private static final int ARM_MUSCLES = 3;
    private static final String ARMED_FOREARM = "right_forearm";
    private static final String NUMB_PREFIX = "meridian_human:numb_right_";
    private static final double EPSILON = 0.00001;
    private static final double STILL = 0.01;
    private static final double PICKUP_RADIUS = 3;

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void inchPowerSinksIntoTheOrgansAndDoesNotPushBack(GameTestHelper context) {
        // 寸劲打在有身体的人身上：只伤躯干里的一个部位，那个部位有脏腑时至少六成落在脏腑上；人没被打退。
        duel(context, INCH_DISTANCE, (caster, target) -> {
            BodySnapshot before = body(target);
            Vec3 stood = target.player.position();
            caster.start(INCH_POWER, "寸劲");
            context.runAfterDelay(INCH_STRIKE_TICKS + SETTLE_TICKS, () -> finish(context, caster, target, () -> {
                BodySnapshot after = body(target);
                String part = hurtPart(context, before, after);
                double partLoss = partHp(before, part) - partHp(after, part);
                boolean hasOrgans = before.structure().organs().stream()
                        .anyMatch(organ -> organ.partId().value().equals(part));
                double share = (organHp(before) - organHp(after)) / partLoss;
                // 六成是按皮、肌肉、骨、脏腑四层都在算的；这个部位缺哪一层，那一层的份就按比例分给在的几层，
                // 所以脏腑吃到的只会比六成多（腹部没有肌肉层，是七成一）。普通钝击脏腑最多一成半。
                boolean sunk = hasOrgans ? share > ORGAN_SHARE - EPSILON && share < MOST_ORGAN_SHARE : share == 0;
                context.assertTrue(sunk, "寸劲打在 " + part + "，脏腑吃到 " + share + "，有脏腑的部位应不少于六成");
                context.assertTrue(target.player.position().distanceTo(stood) < STILL, "寸劲不该把人打退");
            }));
        });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void wristLockWrenchesTheArmedForearmTwiceAndNumbsTheArm(GameTestHelper context) {
        // 缠腕打在右手拿着弓的人身上：两下都落在右前臂（第二下比第一下又少一截），右臂三块肌肉各挂一条麻。
        duel(context, LOCK_DISTANCE, (caster, target) -> {
            target.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            BodySnapshot before = body(target);
            double[] afterFirst = new double[1];
            caster.start(WRIST_LOCK, "缠腕");
            context.runAfterDelay(FIRST_TWIST_TICKS + SETTLE_TICKS,
                    () -> afterFirst[0] = partHp(body(target), ARMED_FOREARM));
            context.runAfterDelay(SECOND_TWIST_TICKS + SETTLE_TICKS, () -> finish(context, caster, target, () -> {
                BodySnapshot after = body(target);
                double full = partHp(before, ARMED_FOREARM);
                context.assertTrue(afterFirst[0] < full, "第一下拧没有落在右前臂");
                context.assertTrue(partHp(after, ARMED_FOREARM) < afterFirst[0], "第二下拧没有落在右前臂");
                context.assertTrue(hurtPart(context, before, after).equals(ARMED_FOREARM), "缠腕伤到了别的部位");
                long numbed = after.modifiers().stream()
                        .filter(modifier -> modifier.id().value().startsWith(NUMB_PREFIX)).count();
                context.assertTrue(numbed == ARM_MUSCLES, "右臂三块肌肉应各挂一条麻：" + after.modifiers());
            }));
        });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void wristLockHurtsABodilessHuskTwiceAndKnocksItsSwordLoose(GameTestHelper context) {
        // 没有身体的尸壳走原版受伤入口：两下拧隔不到一秒都算数，第二下把它手里的剑打掉在地上。
        var caster = new VillagerFistTestPlayer(context);
        Husk husk = context.spawn(EntityTypes.HUSK, CENTER, HEIGHT, CENTER + 2);
        husk.setNoAi(true /* aiDisabled */);
        husk.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        float[] health = {husk.getHealth(), 0};
        caster.start(WRIST_LOCK, "缠腕");
        context.runAfterDelay(FIRST_TWIST_TICKS + SETTLE_TICKS, () -> health[1] = husk.getHealth());
        context.runAfterDelay(SECOND_TWIST_TICKS + SETTLE_TICKS, () -> {
            try (caster) {
                context.assertTrue(health[1] < health[0], "第一下拧没有伤到尸壳");
                context.assertTrue(husk.getHealth() < health[1], "第二下拧被无敌帧吃掉了");
                context.assertTrue(husk.getMainHandItem().isEmpty(), "尸壳手里的剑没有被打掉");
                List<ItemEntity> dropped = context.getLevel().getEntitiesOfClass(ItemEntity.class,
                        husk.getBoundingBox().inflate(PICKUP_RADIUS), item -> item.getItem().is(Items.IRON_SWORD));
                context.assertTrue(dropped.size() == 1, "打掉的剑没有掉在地上：" + dropped);
                dropped.forEach(ItemEntity::discard);
                husk.discard();
                context.succeed();
            }
        });
    }

    /** 出手方站中心朝南，守方在正前方面朝它；过了入场无敌再开始。 */
    private static void duel(GameTestHelper context, double distance, Bout bout) {
        var caster = new VillagerFistTestPlayer(context);
        Vec3 feet = new Vec3(CENTER + HALF, HEIGHT, CENTER + HALF + distance);
        var target = new VillagerFistTestPlayer(context, TARGET_NAME, feet, FACING_NORTH);
        context.runAfterDelay(JOIN_INVULNERABILITY_TICKS, () -> bout.begin(caster, target));
    }

    private static void finish(GameTestHelper context, VillagerFistTestPlayer caster, VillagerFistTestPlayer target,
                               Runnable checks) {
        // 检查做完或挂了，两个玩家都下线。
        try (caster; target) {
            checks.run();
            context.succeed();
        }
    }

    private static BodySnapshot body(VillagerFistTestPlayer fixture) {
        // 读这个玩家身体当前已提交的样子。
        return HumanTechniqueHost.state().find(fixture.player.getUUID()).snapshot();
    }

    private static String hurtPart(GameTestHelper context, BodySnapshot before, BodySnapshot after) {
        // 这一式只该伤到一个部位，返回是哪一个。
        List<String> hurt = before.partStates().stream()
                .filter(part -> partHp(after, part.id().value()) < part.hp())
                .map(part -> part.id().value())
                .toList();
        context.assertTrue(hurt.size() == 1, "应当只伤一个部位，实际 " + hurt);
        return hurt.getFirst();
    }

    private static double partHp(BodySnapshot body, String partId) {
        return body.partStates().stream().filter(part -> part.id().value().equals(partId))
                .findFirst().orElseThrow().hp();
    }

    private static double organHp(BodySnapshot body) {
        return body.organStates().stream().mapToDouble(organ -> organ.hp()).sum();
    }

    /** 两个玩家摆好、过了入场无敌以后要做的事。 */
    private interface Bout {
        void begin(VillagerFistTestPlayer caster, VillagerFistTestPlayer target);
    }
}
