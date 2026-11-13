package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.core.AnatomyState;
import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.DebugQi;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.DebugQiMode;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.FailureCode;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.fabric.testing.RecordingConnection;
import dev.kizuna.meridian.technique.fabric.testing.TestPlayers;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import dev.kizuna.meridian.technique.server.TechniqueCastEnded;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 经过真实登录的袖手拳测试玩家：站在结构中心朝 +z，开全身经脉、灌满真元。
 * 内存连接记下声音包和动作栏，按心跳回包，打木桩 32 秒也不掉线；施放结束事件按施放 ID 记下。
 */
final class VillagerFistTestPlayer implements AutoCloseable {
    static final String PREFIX = "meridian_villager:";
    static final String TEST_PREFIX = "meridian_villager_test:";
    static final int CENTER = 4;
    static final int HEIGHT = 2;
    private static final String NAME = "fist_test";
    private static final String ARM_MUSCLE = "human:arm_muscle";
    private static final List<Identifier> CHANNELS = List.of(
            Identifier.parse("meridian:body_view"),
            Identifier.parse("kizuna_players_animation:play"),
            Identifier.parse("kizuna_players_animation:stop"));
    private static final Map<UUID, VillagerFistTestPlayer> ONLINE = new LinkedHashMap<>();
    private static final Map<String, TechniqueCastEnded> ENDED = new HashMap<>();
    private static boolean listening;

    final ServerPlayer player;
    private final GameTestHelper context;
    private final RecordingConnection connection;
    private final List<HeardSound> sounds = new ArrayList<>();
    private final List<Component> overlays = new ArrayList<>();
    private final List<Runnable> observers = new ArrayList<>();
    private final Map<String, String> castIds = new HashMap<>();

    /** 收到声音包时的世界 tick、声音 ID 和发声位置；并行用例离得近，按位置分开各自的桩。 */
    record HeardSound(long tick, Identifier id, Vec3 position) {
    }

    VillagerFistTestPlayer(GameTestHelper context) {
        // 只替换传输层；玩家列表、Human 登录与 Fabric 网络事件都用正式实现。
        listen();
        this.context = context;
        player = TestPlayers.detached(context.getLevel(), NAME);
        connection = new RecordingConnection(CHANNELS);
        ONLINE.put(player.getUUID(), this);
        TestPlayers.logIn(player, connection);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true /* noGravity */);
        Vec3 position = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(CENTER, HEIGHT, CENTER)));
        player.snapTo(position.x, position.y, position.z, 0, 0);
        player.setOnGround(true /* onGround */);
        prepareBody();
    }

    void start(String techniqueId, String label) {
        // 起式必须进入起手段；失败时把整条施放结果带进断言，看得到拒绝原因。
        CastOutcome outcome = cast(techniqueId);
        context.assertTrue(outcome.getPhase() == CastPhase.WARMUP, label + "起式失败：" + outcome);
    }

    private CastOutcome cast(String techniqueId) {
        // 学习和施放都引用正式目录；施放 ID 每次随机，按式记下，并行用例的结束事件不会串。
        var service = TechniqueFabric.service();
        var definition = service.registry().findLatest(techniqueId).orElseThrow();
        service.learn(player.getUUID(), definition);
        TechniqueReference reference = TechniqueReference.newBuilder()
                .setId(definition.getId())
                .setVersion(definition.getVersion())
                .build();
        CastRequest request = CastRequest.newBuilder()
                .setBodyId(player.getStringUUID())
                .setCastId(castIds.computeIfAbsent(techniqueId, ignored -> UUID.randomUUID().toString()))
                .setTechnique(reference)
                .build();
        return service.cast(player.getUUID(), request);
    }

    Optional<TechniqueCastEnded> ended(String techniqueId) {
        // 还没结束就为空。
        return Optional.ofNullable(castIds.get(techniqueId)).map(ENDED::get);
    }

    void lookAt(Vec3 relativeTarget) {
        // 眼睛对准结构内的一点；木人桩只有 1.5 格高，平视会越过桩顶。
        player.lookAt(EntityAnchorArgument.Anchor.EYES, context.absoluteVec(relativeTarget));
    }

    List<HeardSound> sounds() {
        // 按收到的先后排列。
        return sounds;
    }

    boolean sawOverlay(String translationKey) {
        // 动作栏收到过这条翻译键的提示。
        return overlays.stream().anyMatch(message -> message.getContents() instanceof TranslatableContents text
                && text.getKey().equals(translationKey));
    }

    void everyTick(Runnable observer) {
        // 逐 tick 观察挂在连接 tick 上：GameTest 的逐 tick 表不能在回调里添项；断线后不再调用。
        observers.add(observer);
    }

    long tick() {
        // 用例里的时刻都以主世界 tick 计，与功法调度同源。
        return player.level().getServer().overworld().getGameTime();
    }

    List<AnatomyState.Tissue> armMuscles() {
        // 按 Human 正式标签读六块手臂肌肉的当前状态。
        HumanPlayerBody body = HumanTechniqueHost.state().find(player.getUUID());
        body.settle(HumanTechniqueHost.nowMillis());
        BodySnapshot snapshot = body.snapshot();
        var armIds = snapshot.structure().tissues().stream()
                .filter(tissue -> tissue.tags().stream().anyMatch(tag -> tag.value().equals(ARM_MUSCLE)))
                .map(tissue -> tissue.id())
                .toList();
        return snapshot.tissueStates().stream().filter(tissue -> armIds.contains(tissue.id())).toList();
    }

    @Override
    public void close() {
        // 真实断线同时取消施放、释放身体认领；重复调用不再处理。
        if (ONLINE.remove(player.getUUID()) != null) {
            connection.disconnectFromClient();
        }
    }

    private void prepareBody() {
        // 两式要手经有气、境界到游炁；打木桩只用手臂肌肉，同样开好。
        HumanPlayerBody body = HumanTechniqueHost.state().find(player.getUUID());
        long nowMillis = HumanTechniqueHost.nowMillis();
        context.assertTrue(body.completeOpening(nowMillis, "all") == FailureCode.FAILURE_CODE_UNSPECIFIED,
                "可信开脉失败");
        DebugQi fill = DebugQi.newBuilder().setMode(DebugQiMode.FILL_LUMENS).build();
        context.assertTrue(body.debugQi(nowMillis, fill) == FailureCode.FAILURE_CODE_UNSPECIFIED,
                "可信填充真元失败");
    }

    private static void listen() {
        // 内存连接不在服务器的套接字列表里：每 tick 补投连接 tick 并处理收到的包；施放结束事件只登记一次。
        if (listening) {
            return;
        }
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (VillagerFistTestPlayer fixture : List.copyOf(ONLINE.values())) {
                if (fixture.connection.isConnected()) {
                    fixture.connection.tick();
                    fixture.drainPackets();
                    List.copyOf(fixture.observers).forEach(Runnable::run);
                }
            }
        });
        TechniqueFabric.CAST_ENDED.register(event -> ENDED.put(event.castId(), event));
        listening = true;
    }

    private void drainPackets() {
        // 声音按收到时的世界 tick 记下，动作栏提示按内容记下，心跳立即应答。
        var listener = (ServerGamePacketListenerImpl) connection.getPacketListener();
        for (var packet : connection.packets()) {
            if (packet instanceof ClientboundSoundPacket sound) {
                Vec3 position = new Vec3(sound.getX(), sound.getY(), sound.getZ());
                sounds.add(new HeardSound(tick(), sound.getSound().value().location(), position));
            }
            if (packet instanceof ClientboundSystemChatPacket chat && chat.overlay()) {
                overlays.add(chat.content());
            }
            if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
                listener.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId()));
            }
        }
        connection.packets().clear();
    }
}
