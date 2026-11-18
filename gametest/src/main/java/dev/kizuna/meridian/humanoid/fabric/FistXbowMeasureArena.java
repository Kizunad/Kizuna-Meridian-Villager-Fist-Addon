package dev.kizuna.meridian.humanoid.fabric;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** 隔离的石地测量场；有钟场地保留真实守钟逻辑，推迟额外袭击波次以免混入第三者。 */
final class FistXbowMeasureArena {
    enum Kind { PLAIN, BELL }

    private static final int ARENA_DISTANCE = 160;
    private static final int ARENA_OFFSET = 128;
    private static final int CASE_OFFSET = 384;
    private static final int PLAIN_WIDTH = 8;
    private static final int PLAIN_LENGTH = 24;
    private static final int BELL_SIZE = 64;
    private static final int WALL_HEIGHT = 5;
    private static final int CHUNK_SHIFT = 4;
    private static final int PLAIN_X = 3;
    private static final int PLAIN_Z = 5;
    private static final int BELL_CENTER = 31;
    private static final int BELL_BEHIND = 2;
    private static final int FOE_DISTANCE = 10;
    private static final int NEXT_WAVE_TICKS = 2400;
    final Kind kind;
    final BlockPos base;
    final BlockPos fighterStart;
    final BlockPos foeStart;
    final Optional<BlockPos> bell;
    private Raid raid;

    private record Layout(int width, int length, int fighterX, int fighterZ, int offset) { }

    FistXbowMeasureArena(GameTestHelper context, int sample, Kind kind) {
        this.kind = kind;
        var layout = layout(kind);
        var origin = context.absolutePos(BlockPos.ZERO);
        int x = origin.getX() + ARENA_OFFSET + sample * ARENA_DISTANCE;
        int z = origin.getZ() + ARENA_OFFSET + layout.offset();
        var level = context.getLevel();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        base = new BlockPos(x, Math.max(ground, origin.getY()), z);
        loadChunks(level, layout.width(), layout.length());
        floor(level, layout.width(), layout.length());
        fighterStart = base.offset(layout.fighterX(), 0, layout.fighterZ());
        foeStart = fighterStart.offset(0, 0, FOE_DISTANCE);
        if (kind == Kind.BELL) {
            var position = fighterStart.offset(0, 0, -BELL_BEHIND);
            level.setBlock(position, Blocks.BELL.defaultBlockState(), Block.UPDATE_CLIENTS);
            bell = Optional.of(position);
        } else {
            bell = Optional.empty();
        }
    }

    private static Layout layout(Kind kind) {
        if (kind == Kind.BELL) {
            return new Layout(BELL_SIZE, BELL_SIZE, BELL_CENTER, BELL_CENTER, CASE_OFFSET + CASE_OFFSET);
        }
        return new Layout(PLAIN_WIDTH, PLAIN_LENGTH, PLAIN_X, PLAIN_Z, CASE_OFFSET);
    }

    private void loadChunks(ServerLevel level, int width, int length) {
        int minChunkX = ((base.getX() - 1) >> CHUNK_SHIFT) - 1;
        int maxChunkX = ((base.getX() + width) >> CHUNK_SHIFT) + 1;
        int minChunkZ = ((base.getZ() - 1) >> CHUNK_SHIFT) - 1;
        int maxChunkZ = ((base.getZ() + length) >> CHUNK_SHIFT) + 1;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, true /* forced */);
            }
        }
    }

    private void floor(ServerLevel level, int width, int length) {
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= length; z++) {
                level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y < WALL_HEIGHT; y++) {
                    var block = Blocks.AIR;
                    if (x == -1 || x == width || z == -1 || z == length) {
                        block = Blocks.BARRIER;
                    }
                    level.setBlock(base.offset(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    void post(GameTestHelper context, Villager fighter) {
        if (bell.isEmpty()) {
            return;
        }
        var level = context.getLevel();
        var position = bell.orElseThrow();
        var claimed = level.getPoiManager().take(type -> type.is(PoiTypes.MEETING),
                (type, at) -> at.equals(position), position, 1 /* range */);
        context.assertTrue(claimed.isPresent() && level.isVillage(position), "测量钟未登记为村庄集合点");
        fighter.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), position));
        String identity = "xbow-measure-raid-" + fighter.getStringUUID();
        UUID uuid = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        var player = FakePlayer.get(level, new GameProfile(uuid, "XbowMeasure"));
        player.setGameMode(GameType.SURVIVAL);
        // 仅借玩家上下文启动袭击，不把玩家加入世界或当作交战对象。
        raid = level.getRaids().createOrExtendRaid(player, position);
        context.assertTrue(raid != null, "测量场地未能启动守钟状态");
        deferWave();
        context.assertTrue(HumanoidGuards.posted(level, fighter), "拳师没有进入守钟状态");
    }

    private void deferWave() {
        try {
            var field = Raid.class.getDeclaredField("raidCooldownTicks");
            field.setAccessible(true /* accessible */);
            field.setInt(raid, NEXT_WAVE_TICKS);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    void check(GameTestHelper context, Villager fighter) {
        if (bell.isPresent() && fighter.isAlive()) {
            context.assertTrue(HumanoidGuards.posted(context.getLevel(), fighter), "测量途中丢失守钟状态");
        }
    }

    void finish() {
        if (raid != null) {
            raid.stop();
        }
    }
}
