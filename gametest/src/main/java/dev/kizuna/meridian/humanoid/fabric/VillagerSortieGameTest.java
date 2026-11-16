package dev.kizuna.meridian.humanoid.fabric;

import com.mojang.authlib.GameProfile;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 村民主动出击：看得见的敌意对象还没冲着他来，村民评估划算就迎上去；不划算（对方强、对方人多）就当没看见，
 * 等对方到了原版的近距离再照旧躲。凡人村民也学了上卷，一样迎上去。和村子无仇的玩家不打，村里恨透的玩家打。
 * 用例放在 Threatening 的包里，借它的包内入口看大脑认的对手和姿态。
 */
public final class VillagerSortieGameTest {
    private static final String FIELD = "meridian_villager_test:sortie_field";
    private static final String PREFIX = "meridian_villager:";
    private static final BlockPos HOME = new BlockPos(3, 0, 3);
    /** 12 格外：比原版村民认尸壳的 8 格、认卫道士的 10 格都远，在出击距离 16 格以内。 */
    private static final BlockPos FAR = new BlockPos(3, 0, 15);
    /** 14 格外：村民要自己闲逛过去 6 格才进得了原版认尸壳的 8 格。 */
    private static final BlockPos FARTHER = new BlockPos(3, 0, 17);
    private static final double VANILLA_SIGHT = 8;
    private static final BlockPos BESIDE = new BlockPos(3, 0, 8);
    private static final int TIMEOUT = 600;
    private static final int WATCH_TICKS = 100;
    private static final int NOTICE_TICKS = 100;
    /** 原版感知每秒刷新一次，大脑每半秒决策一次；两秒半内该认出来。 */
    private static final int SPOT_TICKS = 50;
    private static final int CROWD = 5;
    /** 重大恶评每点算 −5 声望；25 点是 −125，过了 −100 的门槛。 */
    private static final int BAD_GOSSIP = 25;
    private static final int HOSTILE_REPUTATION = -100;
    private static final double CLEAR_RADIUS = 48;

    @GameTest(environment = "meridian_villager_test:sortie_engage", structure = FIELD, maxTicks = TIMEOUT)
    public void capableVillagerGoesOutToMeetAHuskItSees(GameTestHelper context) {
        // 尸壳站在 14 格外不动、没盯任何人：原版村民 8 格内才认它。会拳的游炁村民两秒半内就把它当对手，
        // 那一刻两边还隔着 8 格以上（不是闲逛到跟前才看见的）；然后迎上去用袖手拳打伤它，整个过程村民没挨过打。
        goesOut(context, HumanoidTier.WANDERING_QI);
    }

    @GameTest(environment = "meridian_villager_test:sortie_mortal", structure = FIELD, maxTicks = TIMEOUT)
    public void mortalVillagerGoesOutToMeetAHuskToo(GameTestHelper context) {
        // 凡人村民一条经都没开，也学了上卷（外家只靠肌肉）：14 格外的尸壳照样认它、迎上去用袖手拳打伤它。
        goesOut(context, HumanoidTier.MORTAL);
    }

    private static void goesOut(GameTestHelper context, HumanoidTier tier) {
        // 这一档的村民两秒半内把 14 格外的尸壳当对手，认的那一刻还隔着 8 格以上，然后迎上去打伤它。
        clearMobs(context);
        Husk husk = FistMasterDutyGameTest.dummy(context, FARTHER);
        Villager villager = fighter(context, tier, husk);
        await(context, SPOT_TICKS, () -> opponentIs(villager, husk),
                () -> "14 格外的尸壳，会拳的村民没有把它当对手" + state(villager), () -> {
                    context.assertTrue(villager.distanceTo(husk) > VANILLA_SIGHT,
                            "村民是走到原版认得出的距离才认的：相距 " + villager.distanceTo(husk));
                    strikesIt(context, villager, husk);
                });
    }

    private static void strikesIt(GameTestHelper context, Villager villager, Husk husk) {
        // 迎上去，用袖手拳打伤它；自己没挨过打。
        Set<String> seen = new LinkedHashSet<>();
        context.succeedWhen(() -> {
            DamageSource last = husk.getLastDamageSource();
            if (last != null) {
                seen.add(last.typeHolder().getRegisteredName() + "←" + last.getEntity());
            }
            context.assertTrue(last != null && last.getEntity() == villager,
                    "村民还没迎上去打伤尸壳：相距 " + villager.distanceTo(husk) + " 尸壳活着=" + husk.isAlive()
                            + " 血=" + husk.getHealth() + " 挨过的=" + seen + state(villager));
            context.assertTrue(last.typeHolder().getRegisteredName().startsWith(PREFIX),
                    "村民不是用袖手拳打的：" + last.typeHolder().getRegisteredName());
            context.assertTrue(villager.getLastDamageSource() == null, "村民是挨了打才动手的");
            leave(villager, husk);
        });
    }

    @GameTest(environment = "meridian_villager_test:sortie_stronger", structure = FIELD, maxTicks = TIMEOUT)
    public void capableVillagerDoesNotGoOutAgainstAStrongerFoe(GameTestHelper context) {
        // 12 格外站着一个真炁的卫道士（带着斧子、没盯任何人）：会拳的游炁村民评估打不过，不出击。
        clearMobs(context);
        pen(context);
        Vindicator vindicator = EntityTypes.VINDICATOR.create(context.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 far = Vec3.atBottomCenterOf(context.absolutePos(FAR));
        vindicator.snapTo(far.x, far.y, far.z, 0, 0);
        vindicator.setNoAi(true /* aiDisabled */);
        vindicator.setPersistenceRequired();
        HumanoidSpeciesBirths.birth(vindicator, HumanoidTier.TRUE_QI);
        context.assertTrue(context.getLevel().addFreshEntity(vindicator), "卫道士未加入世界");
        Villager villager = fighter(context, vindicator);
        staysOut(context, villager, WATCH_TICKS, () -> {
            leave(villager, vindicator);
            context.succeed();
        });
    }

    @GameTest(environment = "meridian_villager_test:sortie_outnumbered", structure = FIELD, maxTicks = TIMEOUT)
    public void capableVillagerDoesNotGoOutWhenOutnumbered(GameTestHelper context) {
        // 12 格外并排站着五只尸壳：一对一打得过，一对五不划算，不出击。
        clearMobs(context);
        pen(context);
        List<Mob> husks = new ArrayList<>();
        for (int index = 0; index < CROWD; index++) {
            husks.add(FistMasterDutyGameTest.dummy(context, FAR.offset(index - CROWD / 2, 0, 0)));
        }
        Villager villager = fighter(context, husks.getFirst());
        staysOut(context, villager, WATCH_TICKS, () -> {
            husks.forEach(Entity::discard);
            villager.discard();
            context.succeed();
        });
    }

    @GameTest(environment = "meridian_villager_test:sortie_player", structure = FIELD, maxTicks = TIMEOUT)
    public void villagerLeavesAnInnocentPlayerAloneAndTurnsOnAHatedOne(GameTestHelper context) {
        // 生存模式的玩家站在 5 格外、没打任何人：和村子无仇，村民不理他。等村里对他的声望到了 −125，
        // 没带守村的普通村民（会拳）也把他当对手。
        clearMobs(context);
        pen(context);
        ServerLevel level = context.getLevel();
        FakePlayer player = player(context, "SortiePlayer", BESIDE);
        Villager villager = fighter(context, player);
        Runnable cleanup = () -> {
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            villager.discard();
        };
        staysOut(context, villager, WATCH_TICKS, cleanup, () -> {
            villager.getGossips().add(player.getUUID(), GossipType.MAJOR_NEGATIVE, BAD_GOSSIP);
            ensure(context, villager.getPlayerReputation(player) <= HOSTILE_REPUTATION,
                    () -> "声望没有降到门槛以下：" + villager.getPlayerReputation(player), cleanup);
            await(context, NOTICE_TICKS, () -> opponentIs(villager, player),
                    () -> "村里恨透的玩家，会拳的村民没有把他当对手" + state(villager), cleanup, () -> {
                        cleanup.run();
                        context.succeed();
                    });
        });
    }


    private static Villager fighter(GameTestHelper context, LivingEntity opponent) {
        return fighter(context, HumanoidTier.WANDERING_QI, opponent);
    }

    private static Villager fighter(GameTestHelper context, HumanoidTier tier, LivingEntity opponent) {
        // 开着 AI 的这一档村民。袖手拳只靠肌肉，不挑开了哪些经脉、不补真元：对这个对手至少放得出一式。
        Villager villager = FistMasterDutyGameTest.villager(context, tier, HOME, false /* idle */, Optional.empty());
        context.assertTrue(!FistMasterDutyGameTest.candidates(villager, opponent).isEmpty(),
                tier.id() + " 村民放不出袖手拳");
        return villager;
    }

    private static void staysOut(GameTestHelper context, Villager villager, int ticks, Runnable next) {
        staysOut(context, villager, ticks, () -> { }, next);
    }

    private static void staysOut(GameTestHelper context, Villager villager, int ticks, Runnable cleanup,
                                 Runnable next) {
        // 连着这么多 tick，大脑都没有给这个村民认对手（没事时调息是它自己的事，不算）。
        ensure(context, HumanoidBrain.opponent(villager).isEmpty(),
                () -> "不该出击的村民认了对手" + state(villager), cleanup);
        if (ticks == 0) {
            next.run();
        } else {
            context.runAfterDelay(1, () -> staysOut(context, villager, ticks - 1, cleanup, next));
        }
    }

    private static boolean opponentIs(Villager villager, LivingEntity expected) {
        return HumanoidBrain.opponent(villager).filter(found -> found == expected).isPresent();
    }

    private static String state(Villager villager) {
        // 失败时带出：大脑认的对手、在不在指挥、最近的决策和自身状况。
        return "；对手=" + HumanoidBrain.opponent(villager) + " 大脑在指挥=" + HumanoidBrain.directing(villager)
                + FistMasterDutyGameTest.trail(villager);
    }

    private static FakePlayer player(GameTestHelper context, String name, BlockPos relative) {
        // 生存模式才算得上敌人（测试世界默认创造）；站着不动，不打任何人。
        ServerLevel level = context.getLevel();
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayer.get(level, new GameProfile(id, name));
        player.setGameMode(GameType.SURVIVAL);
        Vec3 position = Vec3.atBottomCenterOf(context.absolutePos(relative));
        player.snapTo(position.x, position.y, position.z, 0, 0);
        level.addNewPlayer(player);
        return player;
    }

    private static void pen(GameTestHelper context) {
        // 把村民出生的那一格用栅栏圈起来：看得出去、走不出去。不该出击的用例里，免得它自己闲逛进原版认得出的距离。
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    context.setBlock(HOME.offset(x, 0, z), Blocks.OAK_FENCE);
                }
            }
        }
    }

    private static void leave(Mob... mobs) {
        // 用例结束把生物移走，不留给后面的批次。
        for (Mob mob : mobs) {
            mob.discard();
        }
    }

    private static void clearMobs(GameTestHelper context) {
        // 每条用例独占一个批次，先移除前面批次留在附近的生物，免得村民先盯上它们。
        AABB area = new AABB(context.absolutePos(HOME)).inflate(CLEAR_RADIUS);
        context.getLevel().getEntitiesOfClass(Mob.class, area, mob -> true).forEach(Entity::discard);
    }

    private static void await(GameTestHelper context, int ticks, BooleanSupplier condition, Supplier<String> failure,
                              Runnable next) {
        await(context, ticks, condition, failure, () -> { }, next);
    }

    private static void await(GameTestHelper context, int ticks, BooleanSupplier condition, Supplier<String> failure,
                              Runnable cleanup, Runnable next) {
        // 每 tick 看一次，条件满足就往下走，到期还不满足就收场、失败。
        if (condition.getAsBoolean()) {
            next.run();
        } else if (ticks == 0) {
            ensure(context, false /* held */, failure, cleanup);
        } else {
            context.runAfterDelay(1, () -> await(context, ticks - 1, condition, failure, cleanup, next));
        }
    }

    private static void ensure(GameTestHelper context, boolean held, Supplier<String> failure, Runnable cleanup) {
        // 不成立就先把场上留下的玩家收走再报失败：留下的生存玩家会搅到后面批次的用例。
        if (!held) {
            String message = failure.get();
            cleanup.run();
            throw context.assertionException(Component.literal(message));
        }
    }
}
