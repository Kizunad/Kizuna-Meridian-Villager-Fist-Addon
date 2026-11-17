package dev.kizuna.meridian.human.fabric;

import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.CENTER;
import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.HEIGHT;
import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.PREFIX;

import dev.kizuna.meridian.core.Anatomy;
import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageTypeTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/** 正式功法对真实玩家身体的落点：撩天打头颈，崩拳继续打躯干。 */
public final class VillagerFistAimGameTest {
    private static final double HALF = 0.5;
    private static final double TARGET_DISTANCE = 2;
    private static final float FACING_NORTH = 180;
    private static final int JOIN_INVULNERABILITY_TICKS = 61;
    private static final int SETTLE_TICKS = 5;
    private static final int TIMEOUT_TICKS = 140;
    private static final int UPPERCUT_STRIKE_TICKS = 38;
    private static final int CRUSHING_STRIKE_TICKS = 20;
    private static final Strike UPPERCUT = new Strike("golem_lift", UPPERCUT_STRIKE_TICKS, "head_neck",
            Set.of("head", "neck"));
    private static final Strike CRUSHING = new Strike("crushing_fist", CRUSHING_STRIKE_TICKS, "torso",
            Set.of("neck", "chest", "abdomen", "pelvis"));

    private record Strike(String id, int strikeTicks, String aim, Set<String> parts) { }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void golemLiftHitsOnlyTheHeadOrNeckOfAHumanTarget(GameTestHelper context) {
        // 撩天走正式施放入口，头颈瞄准与真实身体扣血必须一致。
        hitsBody(context, UPPERCUT);
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void crushingFistStillHitsTheTorsoOfAHumanTarget(GameTestHelper context) {
        // 崩拳沿用躯干瞄准，落点和真实身体扣血都留在躯干组内。
        hitsBody(context, CRUSHING);
    }

    private static void hitsBody(GameTestHelper context, Strike strike) {
        // 等入服无敌期结束再施放，招式结算后核对伤害通知和玩家身体。
        var caster = new VillagerFistTestPlayer(context);
        Vec3 feet = new Vec3(CENTER + HALF, HEIGHT, CENTER + HALF + TARGET_DISTANCE);
        var target = new VillagerFistTestPlayer(context, "aim_target", feet, FACING_NORTH);
        var hits = new ArrayList<VanillaDamageFabric.DamageEvent>();
        VanillaDamageFabric.registerDamageObserver(event -> {
            if (event.attacker() == caster.player && event.target() == target.player) {
                hits.add(event);
            }
        });
        context.runAfterDelay(JOIN_INVULNERABILITY_TICKS, () -> {
            String strikeId = strike.id();
            caster.start(PREFIX + strikeId, strikeId);
            context.runAfterDelay(strike.strikeTicks() + SETTLE_TICKS, () -> {
                try (caster; target) {
                    assertHit(context, strike, hits);
                    context.succeed();
                }
            });
        });
    }

    private static void assertHit(GameTestHelper context, Strike strike,
                                  List<VanillaDamageFabric.DamageEvent> hits) {
        // 一次命中的来源标签、通知落点和身体扣血部位必须相符。
        int hitCount = hits.size();
        String strikeId = strike.id();
        String aim = strike.aim();
        context.assertTrue(hitCount == 1, strikeId + " 应恰好打中一次，实际 " + hitCount);
        VanillaDamageFabric.DamageEvent hit = hits.getFirst();
        context.assertTrue(VanillaDamageTypeTags.aim(hit.source()).orElse("").equals(aim),
                strikeId + " 的伤害来源没有瞄准 " + aim);
        String part = hit.input().hit().orElseThrow().getPartId();
        context.assertTrue(strike.parts().contains(part), strikeId + " 落到了 " + part);
        var body = HumanTechniqueHost.state().find(hit.target().getUUID()).snapshot();
        List<String> injured = injuredParts(body);
        context.assertTrue(injured.equals(List.of(part)), "身体扣血部位与伤害通知不一致：" + injured);
    }

    private static List<String> injuredParts(BodySnapshot body) {
        // 当前生命低于结构定义上限的部位才算受伤。
        return body.partStates().stream()
                .filter(state -> state.hp() < maxHp(body, state.id()))
                .map(state -> state.id().value())
                .toList();
    }

    private static double maxHp(BodySnapshot body, Anatomy.PartId partId) {
        // 按部位 ID 读取结构定义中的生命上限。
        return body.structure().parts().stream()
                .filter(definition -> definition.id().equals(partId))
                .findFirst().orElseThrow().maxHp();
    }
}
