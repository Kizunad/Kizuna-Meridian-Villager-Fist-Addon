package dev.kizuna.meridian.villager.clienttest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 村民主动出击的连拍：平地上一个会拳的游炁村民，14 格外站着一只不动、没盯任何人的尸壳。
 * 村民看见了就迎上去，用袖手拳把它打倒。镜头是旁观模式的玩家，在侧面；游戏冻结后一 tick 放行一次、拍一张。
 */
public final class VillagerSortieClientTest implements FabricClientGameTest {
    private static final String CLIP_PROPERTY = "meridian.villager.clip";
    private static final String CLIP = "sortie";
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final String SPAWN = "meridian humanoid spawn minecraft:villager wandering_qi %.1f %.1f %.1f";
    private static final int CLEAR_RADIUS = 24;
    private static final int CLEAR_HEIGHT = 12;
    private static final int SETTLE_TICKS = 10;
    private static final int STEP_SETTLE_TICKS = 2;
    private static final double HUSK_DISTANCE = 14;
    private static final float HUSK_HEALTH = 6;
    private static final Vec3 VILLAGER_FEET = new Vec3(0.5, 0, 0.5);
    /** 镜头在两者连线中点的东侧 13 格、高 3 格，朝连线中点看；视野收到 40 度，14 格的路整段都在画面里。 */
    private static final Vec3 CAMERA_FEET = new Vec3(13.5, 3, HUSK_DISTANCE / 2 + 0.5);
    private static final Vec3 CAMERA_TARGET = new Vec3(0.5, 1, HUSK_DISTANCE / 2 + 0.5);
    private static final double EYE_HEIGHT = 1.62;
    /** 最多拍这么多 tick；尸壳倒下后再拍一小段就停。 */
    private static final int FILM_TICKS = 700;
    private static final int AFTER_DEATH_TICKS = 40;
    private static final boolean CLEAR_SENT_HISTORY = false;
    private static final String RECORD = "sortie.tsv";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!CLIP.equals(System.getProperty(CLIP_PROPERTY))) {
            // 这一次录的是别的段。
            return;
        }
        context.getInput().resizeWindow(WINDOW_WIDTH, WINDOW_HEIGHT);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            BlockPos origin = prepare(context, world);
            Vec3 base = Vec3.atLowerCornerOf(origin);
            look(context, world, base.add(CAMERA_FEET), base.add(CAMERA_TARGET));
            command(world, "tick freeze");
            Villager villager = world.getServer().computeOnServer(server -> villager(server, base.add(VILLAGER_FEET)));
            Husk husk = world.getServer().computeOnServer(
                    server -> husk(server, base.add(VILLAGER_FEET).add(0, 0, HUSK_DISTANCE)));
            film(context, world, villager, husk);
            command(world, "tick unfreeze");
        }
    }

    private static BlockPos prepare(ClientGameTestContext context, TestSingleplayerContext world) {
        // 原点取 (0, 0) 处的地表；正午、晴天，界面藏起来，四周清成平地；玩家改旁观，不进任何生物的眼。
        int surface = world.getServer().computeOnServer(
                server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
        BlockPos origin = new BlockPos(0, surface, 0);
        command(world, "time set noon");
        command(world, "gamerule advance_time false");
        command(world, "weather clear");
        command(world, "gamemode spectator @a");
        context.runOnClient(client -> {
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

    private static Villager villager(MinecraftServer server, Vec3 feet) {
        // 用正式的生成命令出生一个游炁村民，上卷三式是学卷表发的。袖手拳只靠肌肉，不用另外开经脉、补真元。
        ServerLevel level = server.overworld();
        var source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, String.format(Locale.ROOT, SPAWN, feet.x, feet.y, feet.z));
        List<Villager> spawned = level.getEntitiesOfClass(Villager.class, new AABB(BlockPos.containing(feet)));
        if (spawned.size() != 1) {
            throw new IllegalStateException("生成命令没有放出恰好一个村民：" + spawned);
        }
        Villager villager = spawned.getFirst();
        villager.setPersistenceRequired();
        return villager;
    }

    private static Husk husk(MinecraftServer server, Vec3 feet) {
        // 不动、不出声的尸壳，背对着村民，没盯任何人。只留 6 点血（原版满血 20）：村民一拳两点上下、四五秒一拳，
        // 满血要打一分多钟，半分钟的录像里看不到它倒下。
        Husk husk = EntityTypes.HUSK.create(server.overworld(), EntitySpawnReason.COMMAND);
        husk.snapTo(feet.x, feet.y, feet.z, 0, 0);
        husk.setNoAi(true /* aiDisabled */);
        husk.setSilent(true /* silent */);
        husk.setPersistenceRequired();
        husk.setHealth(HUSK_HEALTH);
        server.overworld().addFreshEntity(husk);
        return husk;
    }

    private static void film(ClientGameTestContext context, TestSingleplayerContext world, Villager villager,
                             Husk husk) {
        // 游戏已经冻结：每次只放行一个 tick，等它的包到了再拍，一张正好是一个游戏 tick。
        // 每张记一行：第几张、两边相距、尸壳的血、村民挨没挨过打，给看录像的人对照。
        List<String> rows = new ArrayList<>();
        int deadAt = -1;
        for (int frame = 1; frame <= FILM_TICKS && (deadAt < 0 || frame <= deadAt + AFTER_DEATH_TICKS); frame++) {
            command(world, "tick step 1");
            context.waitTicks(STEP_SETTLE_TICKS);
            shoot(context, String.format(Locale.ROOT, "sortie_%04d", frame));
            String row = world.getServer().computeOnServer(server -> String.format(Locale.ROOT, "%.2f\t%.1f\t%s",
                    villager.distanceTo(husk), husk.getHealth(), villager.getLastDamageSource() != null));
            rows.add(frame + "\t" + row);
            if (deadAt < 0 && world.getServer().computeOnServer(server -> !husk.isAlive())) {
                deadAt = frame;
            }
        }
        // 尸壳倒没倒看最后一行的血量；没倒也照样留下截图，不在这里判失败。
        write(RECORD, "张\t相距\t尸壳血量\t村民挨过打\n" + String.join("\n", rows) + "\n");
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
