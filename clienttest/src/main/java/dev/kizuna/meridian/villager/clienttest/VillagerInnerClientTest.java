package dev.kizuna.meridian.villager.clienttest;

import dev.kizuna.meridian.humanoid.fabric.ClientTestCasters;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastEntry;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 中卷内家两式的连拍：一个真炁村民（学卷表发了上、中两卷）对着身前的对手出寸劲（对手穿铁胸甲）
 * 和缠腕（对手右手拿铁剑）。对手先是尸壳（没有身体，走原版受伤入口），寸劲再对一个卫道士生人打一遍
 * （有身体，走直接路；红闪和受伤动作是执行器补的）。
 * 村民不开 AI，由这里替它起式；镜头是旁观模式的玩家，在侧面。游戏冻结后一 tick 放行一次、拍一张，
 * 同时记下每张对手的血、离村民多远、手里还有没有东西、客户端上它的受伤动作还剩几 tick。
 * 两式的动作还没做进动画库，画面里村民是原版站姿；看得见的是对手变红、掉血不后退、被拉到身前、剑掉在地上。
 */
public final class VillagerInnerClientTest implements FabricClientGameTest {
    private static final String CLIP_PROPERTY = "meridian.villager.clip";
    private static final String CLIP = "inner";
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final String SPAWN = "meridian humanoid spawn minecraft:villager true_qi %.1f %.1f %.1f";
    private static final String VINDICATOR_SPAWN =
            "meridian humanoid spawn minecraft:vindicator wandering_qi %.1f %.1f %.1f";
    private static final String INCH_POWER = "meridian_villager:inch_power";
    private static final String WRIST_LOCK = "meridian_villager:wrist_lock";
    private static final int CLEAR_RADIUS = 16;
    private static final int CLEAR_HEIGHT = 12;
    private static final int SETTLE_TICKS = 10;
    private static final int STEP_SETTLE_TICKS = 2;
    private static final Vec3 CASTER_FEET = new Vec3(0.5, 0, 0.5);
    /** 镜头在两者东侧 6 格、和人差不多高，朝两者中间看。 */
    private static final Vec3 CAMERA_FEET = new Vec3(6.5, 0, 1.3);
    private static final Vec3 CAMERA_TARGET = new Vec3(0.5, 1.1, 1.3);
    private static final double EYE_HEIGHT = 1.62;
    private static final float FOE_FACING_CASTER = 180;
    /** 村民出生时开了几条手经是抽的，中卷两式要开着一条；起不成就换一个再来，最多换这么多个。 */
    private static final int CASTER_ATTEMPTS = 12;
    private static final int LEAD_FRAMES = 10;
    private static final boolean CLEAR_SENT_HISTORY = false;
    private static final String RECORD = "inner.tsv";

    /** 对手是哪一种：尸壳没有身体；卫道士生人有身体。 */
    private enum Foe { HUSK, VINDICATOR }

    /** 一段：名字、放哪一式、对手是谁、站多远、带什么、起式以后拍多少 tick。 */
    private record Clip(String name, String technique, Foe foe, double distance, EquipmentSlot slot, ItemStack gear,
                        int frames) { }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!CLIP.equals(System.getProperty(CLIP_PROPERTY))) {
            // 这一次录的是别的段。
            return;
        }
        context.getInput().resizeWindow(WINDOW_WIDTH, WINDOW_HEIGHT);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            Vec3 base = Vec3.atLowerCornerOf(prepare(context, world));
            look(context, world, base.add(CAMERA_FEET), base.add(CAMERA_TARGET));
            command(world, "tick freeze");
            List<String> rows = new ArrayList<>();
            film(context, world, base, rows, new Clip("inch_power", INCH_POWER, Foe.HUSK, 1.3,
                    EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE), 85));
            film(context, world, base, rows, new Clip("wrist_lock", WRIST_LOCK, Foe.HUSK, 1.8,
                    EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD), 105));
            film(context, world, base, rows, new Clip("inch_power_bodied", INCH_POWER, Foe.VINDICATOR, 1.3,
                    EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE), 85));
            command(world, "tick unfreeze");
            write(RECORD, "段\t张\t对手的血\t离村民\t对手主手空了\t客户端受伤动作\t这一式\n"
                    + String.join("\n", rows) + "\n");
        }
    }

    private static BlockPos prepare(ClientGameTestContext context, TestSingleplayerContext world) {
        // 原点取 (0, 0) 处的地表；正午、晴天，界面藏起来，四周清成平地；玩家改旁观，只当镜头。
        int surface = world.getServer().computeOnServer(
                server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
        BlockPos origin = new BlockPos(0, surface, 0);
        command(world, "time set noon");
        command(world, "gamerule advance_time false");
        command(world, "weather clear");
        command(world, "gamemode spectator @a");
        context.runOnClient(client -> {
            // 新玩家进世界会弹出身抽选界面，整屏挡住画面；合上它等于点「先不选」。
            client.gui.setScreen(null);
            if (!client.gui.hud.isHidden()) {
                client.gui.hud.toggle();
            }
        });
        BlockPos from = origin.offset(-CLEAR_RADIUS, 0, -CLEAR_RADIUS);
        BlockPos to = origin.offset(CLEAR_RADIUS, CLEAR_HEIGHT, CLEAR_RADIUS);
        command(world, "fill " + coordinates(from) + " " + coordinates(to) + " air");
        command(world, "fill " + coordinates(from.below()) + " " + coordinates(to.atY(origin.getY() - 1))
                + " grass_block");
        AABB area = new AABB(Vec3.atLowerCornerOf(from), Vec3.atLowerCornerOf(to.offset(1, 1, 1)));
        world.getServer().runOnServer(server -> server.overworld()
                .getEntities((Entity) null, area, entity -> !(entity instanceof Player))
                .forEach(Entity::discard));
        return origin;
    }

    private static void film(ClientGameTestContext context, TestSingleplayerContext world, Vec3 base,
                             List<String> rows, Clip clip) {
        // 摆一个起得成这一式的村民和一个不动的对手，先空拍几张，起式，然后一 tick 一张拍到收势；拍完核对该看见的结果。
        Vec3 feet = base.add(CASTER_FEET);
        Mob foe = world.getServer().computeOnServer(server -> foe(server, feet, clip));
        float full = foe.getHealth();
        String[] cast = {"还没出手"};
        Villager[] caster = new Villager[1];
        int flashed = 0;
        for (int frame = 1; frame <= LEAD_FRAMES + clip.frames(); frame++) {
            if (frame == 1) {
                caster[0] = world.getServer().computeOnServer(server -> caster(server, feet));
            }
            if (frame == LEAD_FRAMES) {
                cast[0] = world.getServer().computeOnServer(server -> start(server, caster, feet, clip.technique()));
            }
            command(world, "tick step 1");
            context.waitTicks(STEP_SETTLE_TICKS);
            shoot(context, String.format(Locale.ROOT, "%s_%03d", clip.name(), frame));
            String state = world.getServer().computeOnServer(server -> String.format(Locale.ROOT, "%.1f\t%.2f\t%s",
                    foe.getHealth(), foe.distanceTo(caster[0]), foe.getMainHandItem().isEmpty()));
            int hurt = context.computeOnClient(client -> hurtTime(client, foe.getId()));
            flashed = Math.max(flashed, hurt);
            rows.add(clip.name() + "\t" + frame + "\t" + state + "\t" + hurt + "\t" + cast[0]);
        }
        check(world, clip, foe, full, cast[0]);
        if (flashed <= 0) {
            throw new AssertionError(clip.name() + "：客户端上对手从头到尾没有受伤动作（不红、不晃）");
        }
        world.getServer().runOnServer(server -> {
            foe.discard();
            caster[0].discard();
            server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(feet)).inflate(4))
                    .forEach(Entity::discard);
        });
    }

    private static void check(TestSingleplayerContext world, Clip clip, Mob foe, float full, String cast) {
        // 每段都要起得成、打得着；缠腕那段对手手里的剑要被打掉。
        boolean disarmed = world.getServer().computeOnServer(server -> foe.getMainHandItem().isEmpty());
        float health = world.getServer().computeOnServer(server -> foe.getHealth());
        if (!cast.contains("阶段=WARMUP") || health >= full) {
            throw new AssertionError(clip.name() + " 没打着：" + cast + " 对手的血 " + health + "/" + full);
        }
        if (clip.slot() == EquipmentSlot.MAINHAND && !disarmed) {
            throw new AssertionError("缠腕没有打掉对手手里的剑");
        }
    }

    private static int hurtTime(Minecraft client, int entityId) {
        // 客户端这一刻看见的受伤动作剩余 tick：大于 0 时模型是红的。
        return client.level.getEntity(entityId) instanceof LivingEntity living ? living.hurtTime : -1;
    }

    private static Villager caster(MinecraftServer server, Vec3 feet) {
        // 用正式的生成命令出生一个真炁村民（学卷表发上、中两卷），面朝南站定、不开 AI。
        Villager villager = spawn(server, SPAWN, feet, Villager.class);
        villager.setNoAi(true /* aiDisabled */);
        villager.setPersistenceRequired();
        villager.snapTo(feet.x, feet.y, feet.z, 0, 0);
        // 不开 AI 的生物不走移动结算，着地状态不会自己更新；它确实站在地上，直接标上。
        villager.setOnGround(true /* onGround */);
        villager.setYHeadRot(0);
        villager.setYBodyRot(0);
        return villager;
    }

    private static <T extends Mob> T spawn(MinecraftServer server, String command, Vec3 feet, Class<T> type) {
        // 用正式的生成命令在这一格出生一个生人，身体、档位、学卷都照正式流程来。
        ServerLevel level = server.overworld();
        var source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source,
                String.format(Locale.ROOT, command, feet.x, feet.y, feet.z));
        List<T> spawned = level.getEntitiesOfClass(type, new AABB(BlockPos.containing(feet)));
        if (spawned.size() != 1) {
            throw new IllegalStateException(
                    "生成命令没有在这一格放出恰好一个 " + type.getSimpleName() + "：" + spawned);
        }
        return spawned.getFirst();
    }

    private static String start(MinecraftServer server, Villager[] caster, Vec3 feet, String technique) {
        // 替村民起式；这个村民起不成（中卷要开着一条手经，他多半一条没开）就换一个再来。
        String outcome = "";
        for (int attempt = 0; attempt < CASTER_ATTEMPTS; attempt++) {
            outcome = cast(caster[0], technique);
            if (outcome.contains("阶段=WARMUP")) {
                return outcome;
            }
            caster[0].discard();
            caster[0] = caster(server, feet);
        }
        return outcome;
    }

    private static Mob foe(MinecraftServer server, Vec3 casterFeet, Clip clip) {
        // 不动、不出声的对手，站在村民正南面朝村民，带上这一段要的东西。
        // 尸壳直接造一只；卫道士走正式的生成命令，它才有身体。
        ServerLevel level = server.overworld();
        Vec3 feet = casterFeet.add(0, 0, clip.distance());
        Mob foe;
        if (clip.foe() == Foe.HUSK) {
            foe = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            foe.snapTo(feet.x, feet.y, feet.z, FOE_FACING_CASTER, 0);
            level.addFreshEntity(foe);
        } else {
            foe = spawn(server, VINDICATOR_SPAWN, feet, Vindicator.class);
        }
        foe.setNoAi(true /* aiDisabled */);
        foe.snapTo(feet.x, feet.y, feet.z, FOE_FACING_CASTER, 0);
        foe.setYHeadRot(FOE_FACING_CASTER);
        foe.setYBodyRot(FOE_FACING_CASTER);
        foe.setSilent(true /* silent */);
        foe.setPersistenceRequired();
        foe.setItemSlot(clip.slot(), clip.gear().copy());
        return foe;
    }

    private static String cast(Villager caster, String technique) {
        // 精确引用取生产注册表。回报起成没有、没起成的原因。
        var definition = TechniqueFabric.service().registry().findLatest(technique).orElseThrow();
        var reference = TechniqueReference.newBuilder()
                .setId(definition.getId())
                .setVersion(definition.getVersion())
                .build();
        var request = CastRequest.newBuilder()
                .setCastId("inner_" + UUID.randomUUID())
                .setBodyId(ClientTestCasters.bodyId(caster))
                .setTechnique(reference)
                .setEntry(CastEntry.AUTO)
                .build();
        CastOutcome outcome = TechniqueFabric.service().cast(caster.getUUID(), request);
        return "阶段=" + outcome.getPhase() + " 失败=" + outcome.getFailure() + " " + outcome.getReason();
    }

    private static void look(ClientGameTestContext context, TestSingleplayerContext world, Vec3 feet, Vec3 target) {
        // 原版朝向：偏航 0 朝 +Z，俯仰为正时低头；tp 给的是脚底坐标，眼睛在其上方 1.62 格。
        Vec3 direction = target.subtract(feet.add(0, EYE_HEIGHT, 0));
        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        double pitch = Math.toDegrees(Math.atan2(-direction.y, direction.horizontalDistance()));
        command(world,
                String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.2f %.2f", feet.x, feet.y, feet.z, yaw, pitch));
        context.waitTicks(SETTLE_TICKS);
        world.getConnection().waitForChunksRender();
    }

    private static void shoot(ClientGameTestContext context, String name) {
        // 命令回显和右上角提示会挡画面，拍之前清掉。
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(CLEAR_SENT_HISTORY);
            client.gui.toastManager().clear();
        });
        context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
    }

    private static void command(TestSingleplayerContext world, String command) {
        // 以服务端身份执行，和管理员在游戏里输入的一样。
        world.getServer().runCommand(command);
    }

    private static void write(String name, String content) {
        // 写到本次运行目录里。
        try {
            Files.writeString(FabricLoader.getInstance().getGameDir().resolve(name), content);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
