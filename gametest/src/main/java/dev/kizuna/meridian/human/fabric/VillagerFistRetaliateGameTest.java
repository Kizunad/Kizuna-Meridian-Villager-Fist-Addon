package dev.kizuna.meridian.human.fabric;

import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.CENTER;
import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.HEIGHT;
import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.PREFIX;

import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 村民会还手：本仓往 Threatening 的学卷表登记了上卷（{@code humanoid_volumes.toml}），
 * 游炁村民出生就会崩拳、撩天、背摔；被尸壳盯上时类人大脑评估打得过，迎上去用这几式把它打伤。
 */
public final class VillagerFistRetaliateGameTest {
    private static final List<String> OUTER_STRIKES =
            List.of(PREFIX + "crushing_fist", PREFIX + "golem_lift", PREFIX + "shoulder_throw");
    private static final String SPAWN = "meridian humanoid spawn minecraft:villager wandering_qi %.1f %.1f %.1f";
    private static final BlockPos VILLAGER = new BlockPos(CENTER, HEIGHT, CENTER - 1);
    private static final BlockPos HUSK = new BlockPos(CENTER, HEIGHT, CENTER + 1);
    private static final int STRUCTURE_SIZE = 8;
    private static final int FLOOR_Y = HEIGHT - 1;
    private static final double HUSK_HEALTH = 1000;
    /** 类人大脑放下敌人的距离是 32 格；开场清掉这个范围里别的批次留下的生物，免得村民先盯上它们。 */
    private static final double CLEAR_RADIUS = 40;
    private static final int TIMEOUT_TICKS = 800;

    @GameTest(environment = "meridian_villager_test:retaliate", maxTicks = TIMEOUT_TICKS)
    public void wanderingQiVillagerKnowsOuterVolumeAndFightsBackWithIt(GameTestHelper context) {
        // 用正式的生成命令出生一个游炁村民：没人另外教它，上卷三式是学卷表发的。
        // 尸壳不动、不还手，只当敌意来源；村民用袖手拳的伤害类型打掉它的血才算还手。
        floor(context);
        clearMobs(context);
        Villager villager = spawn(context);
        Husk husk = context.spawn(EntityTypes.HUSK, HUSK);
        husk.setNoAi(true /* aiDisabled */);
        husk.setSilent(true /* silent */);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HUSK_HEALTH);
        husk.setHealth((float) HUSK_HEALTH);
        husk.setTarget(villager);
        List<String> known = TechniqueFabric.service().known(villager.getUUID()).stream()
                .map(entry -> entry.technique().getId())
                .toList();
        context.assertTrue(known.containsAll(OUTER_STRIKES), "游炁村民出生没学到袖手拳上卷：" + known);
        context.succeedWhen(() -> {
            DamageSource last = husk.getLastDamageSource();
            context.assertTrue(husk.getHealth() < HUSK_HEALTH && last != null, "村民还没打伤尸壳");
            context.assertTrue(last.getEntity() == villager, "打伤尸壳的不是这个村民：" + last.getEntity());
            context.assertTrue(last.typeHolder().getRegisteredName().startsWith(PREFIX),
                    "村民不是用袖手拳打的：" + last.typeHolder().getRegisteredName());
        });
    }

    private static Villager spawn(GameTestHelper context) {
        // 走 /meridian humanoid spawn，和玩家在游戏里生成的一样按学卷表学卷；本仓测试不编译依赖 Threatening。
        Vec3 position = Vec3.atBottomCenterOf(context.absolutePos(VILLAGER));
        var server = context.getLevel().getServer();
        var source = server.createCommandSourceStack().withLevel(context.getLevel()).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source,
                String.format(Locale.ROOT, SPAWN, position.x, position.y, position.z));
        List<Villager> spawned = context.getLevel().getEntitiesOfClass(Villager.class,
                new AABB(context.absolutePos(VILLAGER)), villager -> true);
        context.assertTrue(spawned.size() == 1, "生成命令没有放出恰好一个村民：" + spawned);
        return spawned.getFirst();
    }

    private static void clearMobs(GameTestHelper context) {
        // 这一例独占一个批次，前面批次留在附近的生物先移除。
        AABB area = new AABB(context.absolutePos(new BlockPos(CENTER, HEIGHT, CENTER))).inflate(CLEAR_RADIUS);
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
