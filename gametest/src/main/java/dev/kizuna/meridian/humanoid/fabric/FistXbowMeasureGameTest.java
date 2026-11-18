package dev.kizuna.meridian.humanoid.fabric;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.EffectIntent;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 两种场地各五局独立的六十秒记录；不按胜负断言，原场地沿用拳剂量测量的尺寸。 */
public final class FistXbowMeasureGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("fist_xbow_measure");
    private static final Gson JSON = new Gson();
    private static final String ENVIRONMENT = "meridian_villager_test:xbow_measure";
    private static final int TIMEOUT_TICKS = 1400;
    private static final int ROUND_TICKS = 1200;
    private static final int READY_TICKS = 65;
    private static final int SAMPLES = 5;
    private static final float FACING_NORTH = 180;
    private static final Map<UUID, Scene> SCENES = new HashMap<>();
    private static boolean listening;
    private static final ResourceKey<VillagerProfession> FIST_MASTER = ResourceKey.create(
            Registries.VILLAGER_PROFESSION, Identifier.parse("meridian_villager:fist_master"));

    @GameTest(environment = ENVIRONMENT, maxTicks = TIMEOUT_TICKS)
    public void measureFistXbow(GameTestHelper context) {
        measure(context, FistXbowMeasureArena.Kind.PLAIN);
    }

    @GameTest(environment = ENVIRONMENT, maxTicks = TIMEOUT_TICKS)
    public void measureFistXbowBell(GameTestHelper context) {
        measure(context, FistXbowMeasureArena.Kind.BELL);
    }

    private static void measure(GameTestHelper context, FistXbowMeasureArena.Kind kind) {
        listen();
        List<Scene> scenes = new ArrayList<>();
        for (int sample = 1; sample <= SAMPLES; sample++) {
            scenes.add(new Scene(context, sample, kind));
        }
        context.runAfterDelay(READY_TICKS, () -> {
            scenes.forEach(Scene::start);
            watch(context, scenes);
        });
    }

    private static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        VanillaDamageFabric.registerDamageObserver(FistXbowMeasureGameTest::hit);
        TechniqueFabric.CAST_ENDED.register(event -> {
            Scene scene = SCENES.get(event.entityUuid());
            if (scene == null || !scene.started) {
                return;
            }
            JsonObject row = scene.row("cast_end");
            row.addProperty("actor", event.entityUuid().toString());
            row.addProperty("cast_id", event.castId());
            row.addProperty("ending", event.reason().name());
            row.addProperty("failure", event.outcome().getFailure().name());
            scene.facts(row);
            emit(row);
        });
        listenCasts();
    }

    private static void listenCasts() {
        TechniqueFabric.CAST_OBSERVED.register((uuid, outcome) -> {
            Scene scene = SCENES.get(uuid);
            if (scene == null || !scene.started) {
                return;
            }
            JsonObject row = scene.row("cast");
            row.addProperty("actor", uuid.toString());
            row.addProperty("cast_id", outcome.getCastId());
            row.addProperty("phase", outcome.getPhase().name());
            row.addProperty("failure", outcome.getFailure().name());
            row.addProperty("reason", outcome.getReason());
            scene.facts(row);
            emit(row);
        });
    }

    /** 测试模组的效果探针在实际提交前记录范围，不改变结算。 */
    public static void strike(UUID actor, EffectIntent effect) {
        Scene scene = SCENES.get(actor);
        if (scene == null || !scene.started || !actor.equals(scene.fighter.getUUID())) {
            return;
        }
        if (!scene.effects.add(effect.getCastId() + "/" + effect.getEffectId())) {
            return;
        }
        JsonObject row = scene.row("strike");
        row.addProperty("cast_id", effect.getCastId());
        row.addProperty("effect_id", effect.getEffectId());
        String operation = "limb";
        double reach = 0;
        if (effect.hasLimbStrike()) {
            reach = effect.getLimbStrike().getStrike().getDistance().getValue();
        } else if (effect.hasGrapple()) {
            operation = "grapple";
            reach = effect.getGrapple().getGrapple().getMaximumDistance().getValue();
        } else if (effect.hasThrow()) {
            operation = "throw";
        }
        row.addProperty("operation", operation);
        row.addProperty("reach", reach);
        row.addProperty("box_in_range", scene.fighter.getBoundingBox().inflate(reach)
                .intersects(scene.foe.getBoundingBox()));
        scene.facts(row);
        emit(row);
    }

    private static void hit(VanillaDamageFabric.DamageEvent event) {
        var target = event.target();
        var attacker = event.attacker();
        Scene scene = SCENES.get(target.getUUID());
        if (scene == null || !scene.started) {
            return;
        }
        String direction = "foreign";
        if (target == scene.foe && attacker == scene.fighter) {
            direction = "outgoing";
            scene.hits++;
        } else if (target == scene.fighter && attacker == scene.foe) {
            direction = "incoming";
            scene.incomingHits++;
        } else {
            scene.foreignHits++;
        }
        JsonObject row = scene.row("hit");
        row.addProperty("direction", direction);
        row.addProperty("type", event.source().typeHolder().getRegisteredName());
        row.addProperty("part", event.input().hit().map(value -> value.getPartId()).orElse("unbound"));
        row.addProperty("dose", event.input().damageAfterEffects());
        scene.facts(row);
        emit(row);
    }

    private static void watch(GameTestHelper context, List<Scene> scenes) {
        context.runAfterDelay(1, () -> {
            for (Scene scene : scenes) {
                scene.state();
            }
            if (scenes.getFirst().ticks() >= ROUND_TICKS) {
                scenes.forEach(Scene::finish);
                context.succeed();
            } else {
                watch(context, scenes);
            }
        });
    }

    private static void emit(JsonObject row) {
        LOGGER.info("XBOW_MEASURE {}", JSON.toJson(row));
    }

    private static void place(Mob mob, BlockPos position, String identity) {
        Vec3 feet = Vec3.atBottomCenterOf(position);
        mob.setUUID(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)));
        mob.snapTo(feet.x, feet.y, feet.z, FACING_NORTH, 0);
        mob.setNoAi(true /* aiDisabled */);
        mob.setPersistenceRequired();
    }

    private static void add(ServerLevel level, Mob mob) {
        if (!level.addFreshEntity(mob)) {
            throw new IllegalStateException("测量实体未加入世界");
        }
    }

    private static final class Scene {
        final GameTestHelper context;
        final int sample;
        final Villager fighter;
        final Pillager foe;
        final WrappedGoal crossbow;
        final FistXbowMeasureArena arena;
        final Set<String> effects = new HashSet<>();
        long startTick;
        int fighterTicks;
        int foeTicks;
        boolean started;
        int hits;
        int incomingHits;
        int foreignHits;

        Scene(GameTestHelper context, int sample, FistXbowMeasureArena.Kind kind) {
            this.context = context;
            this.sample = sample;
            ServerLevel level = context.getLevel();
            arena = new FistXbowMeasureArena(context, sample, kind);
            fighter = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            place(fighter, arena.fighterStart, identity("fighter"));
            HumanoidSpeciesBirths.birth(fighter, HumanoidTier.WANDERING_QI);
            add(level, fighter);
            fighter.setVillagerXp(1);
            fighter.setVillagerData(fighter.getVillagerData().withProfession(level.registryAccess(), FIST_MASTER));
            foe = EntityTypes.PILLAGER.create(level, EntitySpawnReason.COMMAND);
            place(foe, arena.foeStart, identity("foe"));
            HumanoidSpeciesBirths.birth(foe, HumanoidTier.MORTAL);
            foe.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            add(level, foe);
            crossbow = FistXbowFacts.crossbowGoal(foe);
            SCENES.put(fighter.getUUID(), this);
            SCENES.put(foe.getUUID(), this);
        }

        private String identity(String actor) {
            if (arena.kind == FistXbowMeasureArena.Kind.BELL) {
                return "fist-xbow-bell-" + actor + "-" + sample;
            }
            return "fist-xbow-" + actor + "-" + sample;
        }

        void start() {
            ServerLevel level = context.getLevel();
            arena.post(context, fighter);
            fighter.setNoAi(false /* aiDisabled */);
            foe.setNoAi(false /* aiDisabled */);
            foe.setTarget(fighter);
            startTick = level.getGameTime();
            fighterTicks = fighter.tickCount;
            foeTicks = foe.tickCount;
            started = true;
            JsonObject row = row("start");
            row.addProperty("fighter_uuid", fighter.getStringUUID());
            row.addProperty("foe_uuid", foe.getStringUUID());
            row.addProperty("world_seed", level.getSeed());
            row.addProperty("difficulty", level.getDifficulty().name());
            row.addProperty("fighter_tags", fighter.entityTags().toString());
            var book = TechniqueFabric.service().registry().books().stream()
                    .filter(value -> value.getId().equals("meridian_villager:sleeve_fist"))
                    .findFirst().orElseThrow();
            row.addProperty("base_damage", book.getArmRules().getBaseDamage().getValue());
            facts(row);
            emit(row);
        }

        long ticks() {
            return context.getLevel().getGameTime() - startTick;
        }

        JsonObject row(String event) {
            JsonObject row = new JsonObject();
            row.addProperty("event", event);
            row.addProperty("sample", sample);
            row.addProperty("arena", arena.kind.name());
            row.addProperty("tick", ticks());
            return row;
        }

        void facts(JsonObject row) {
            row.addProperty("distance", fighter.distanceTo(foe));
            row.addProperty("horizontal_distance", HumanoidMotion.distance(fighter, foe));
            row.addProperty("los", fighter.hasLineOfSight(foe));
            FistXbowFacts.entity(row, "f_", fighter);
            FistXbowFacts.entity(row, "p_", foe);
            FistXbowFacts.crossbow(row, crossbow, foe);
            arena.bell.ifPresent(position -> {
                Vec3 center = Vec3.atBottomCenterOf(position);
                row.addProperty("bell_x", center.x);
                row.addProperty("bell_y", center.y);
                row.addProperty("bell_z", center.z);
                row.addProperty("f_bell_distance", fighter.position().distanceTo(center));
                row.addProperty("p_bell_distance", foe.position().distanceTo(center));
                row.addProperty("f_posted", HumanoidGuards.posted(context.getLevel(), fighter));
            });
        }

        void state() {
            arena.check(context, fighter);
            // GameTest 回调与实体更新的先后可差一个 tick；累计更新不能继续落后。
            long minimumTicks = ticks() - 1;
            context.assertTrue(!fighter.isAlive() || fighter.tickCount - fighterTicks >= minimumTicks,
                    "拳师停止实体更新");
            context.assertTrue(!foe.isAlive() || foe.tickCount - foeTicks >= minimumTicks, "弩手停止实体更新");
            JsonObject row = row("tick");
            row.addProperty("fighter_ticks", fighter.tickCount - fighterTicks);
            row.addProperty("foe_ticks", foe.tickCount - foeTicks);
            facts(row);
            emit(row);
        }

        void finish() {
            JsonObject row = row("end");
            row.addProperty("hits", hits);
            row.addProperty("incoming_hits", incomingHits);
            row.addProperty("foreign_hits", foreignHits);
            facts(row);
            emit(row);
            arena.finish();
            started = false;
            SCENES.remove(fighter.getUUID());
            SCENES.remove(foe.getUUID());
            fighter.discard();
            foe.discard();
        }
    }
}
