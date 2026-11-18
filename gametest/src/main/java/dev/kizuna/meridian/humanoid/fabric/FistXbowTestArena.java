package dev.kizuna.meridian.humanoid.fabric;

import dev.kizuna.meridian.humanoid.server.HumanoidSpecies;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.pb.BodyMessages;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueFailure;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** 与其他用例隔开的石地；正常任职，伤势夹具只通过合法快照重新绑定。 */
final class FistXbowTestArena implements AutoCloseable {
    static final int READY_TICKS = 20;
    private static final int READY_TIMEOUT_TICKS = 100;
    static final String LONG_STRIKE = "meridian_villager:golem_lift";
    static final String THROW = "meridian_villager:shoulder_throw";
    static final String GRAPPLE = "meridian_villager:wrist_lock";
    private static final int SPACING = 160;
    private static final int OFFSET = 128;
    private static final int WIDTH = 8;
    private static final int LENGTH = 24;
    private static final int HEIGHT = 5;
    private static final int FIGHTER_X = 3;
    private static final int FIGHTER_Z = 5;
    private static final int CHUNK_SHIFT = 4;
    private static final double TARGET_DISTANCE = 6;
    private static final double INJURED_HP = 0.2;
    private static final double OVERALL_HP = 0.94;
    private static final String INJURED_PART = "left_foot";
    private static final String SECOND_INJURED_PART = "left_thigh";
    private static final ResourceKey<VillagerProfession> PROFESSION = ResourceKey.create(
            Registries.VILLAGER_PROFESSION, Identifier.parse("meridian_villager:fist_master"));
    final GameTestHelper context;
    final Villager fighter;
    final Pillager target;
    final BlockPos base;
    int hits;

    FistXbowTestArena(GameTestHelper context, int lane) {
        this(context, lane, HumanoidTier.WANDERING_QI, PROFESSION);
    }

    FistXbowTestArena(GameTestHelper context, int lane, HumanoidTier tier,
                     ResourceKey<VillagerProfession> profession) {
        this.context = context;
        base = arena(context, lane);
        var level = context.getLevel();
        fighter = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        place(fighter, Vec3.atBottomCenterOf(base.offset(FIGHTER_X, 0, FIGHTER_Z)));
        HumanoidSpeciesBirths.birth(fighter, tier);
        context.assertTrue(level.addFreshEntity(fighter), "拳师未加入世界");
        fighter.setVillagerXp(1);
        fighter.setVillagerData(fighter.getVillagerData().withProfession(level.registryAccess(), profession));
        target = EntityTypes.PILLAGER.create(level, EntitySpawnReason.COMMAND);
        place(target, fighter.position().add(0, 0, TARGET_DISTANCE));
        HumanoidSpeciesBirths.birth(target, HumanoidTier.MORTAL);
        target.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        context.assertTrue(level.addFreshEntity(target), "弩手未加入世界");
        VanillaDamageFabric.registerDamageObserver(event -> {
            if (event.attacker() == fighter && event.target() == target) {
                hits++;
            }
        });
    }

    void wake() {
        context.assertTrue(HumanoidMeleeGuard.enabled(fighter), "拳师尚未登记近身守卫策略");
        target.setTarget(fighter);
        fighter.setNoAi(false /* aiDisabled */);
    }

    void whenReady(Runnable action) {
        awaitReady(action, READY_TIMEOUT_TICKS);
    }

    private void awaitReady(Runnable action, int remaining) {
        context.runAfterDelay(1, () -> {
            var level = context.getLevel();
            boolean tracked = level.getEntity(fighter.getUUID()) == fighter
                    && level.getEntity(target.getUUID()) == target;
            if (tracked && HumanoidMeleeGuard.enabled(fighter)) {
                action.run();
                return;
            }
            context.assertTrue(remaining > 0, "实体未完成加载或拳师未任职");
            awaitReady(action, remaining - 1);
        });
    }

    void targetAt(double distance) {
        Vec3 position = fighter.position().add(0, 0, distance);
        target.snapTo(position.x, position.y, position.z, 0, 0);
        target.setDeltaMovement(Vec3.ZERO);
    }

    void start(String technique) {
        var body = HumanoidTechniqueHost.prepare(fighter).body();
        var reference = body.techniqueProgress().getKnownList().stream()
                .map(known -> known.getTechnique())
                .filter(known -> known.getId().equals(technique))
                .findFirst().orElseThrow();
        var request = CastRequest.newBuilder()
                .setBodyId(body.snapshot().getId())
                .setCastId(UUID.randomUUID().toString())
                .setTechnique(reference)
                .setTargetId(target.getStringUUID())
                .build();
        var outcome = TechniqueFabric.service().cast(fighter.getUUID(), request);
        context.assertTrue(outcome.getFailure() == TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED,
                "起手被拒绝：" + outcome);
    }

    void woundFoot() {
        var body = HumanoidTechniqueHost.prepare(fighter).body().snapshot().toBuilder();
        var parts = body.getStructure().getPartsList();
        double totalHp = parts.stream().mapToDouble(BodyMessages.PartDefinition::getMaxHp).sum();
        double footHp = parts.stream().filter(part -> part.getId().equals(INJURED_PART))
                .mapToDouble(BodyMessages.PartDefinition::getMaxHp).findFirst().orElseThrow();
        double thighHp = parts.stream().filter(part -> part.getId().equals(SECOND_INJURED_PART))
                .mapToDouble(BodyMessages.PartDefinition::getMaxHp).findFirst().orElseThrow();
        double thighRatio = 1 - (totalHp * (1 - OVERALL_HP) - footHp * (1 - INJURED_HP)) / thighHp;
        context.assertTrue(thighRatio <= 1 && thighRatio > INJURED_HP, "无法构造总体九成四、足部两成的身体");
        for (int index = 0; index < body.getPartStatesCount(); index++) {
            var definition = parts.get(index);
            String partId = definition.getId();
            double ratio = 1;
            if (partId.equals(INJURED_PART)) {
                ratio = INJURED_HP;
            } else if (partId.equals(SECOND_INJURED_PART)) {
                ratio = thighRatio;
            }
            var part = body.getPartStates(index).toBuilder()
                    .setHp(definition.getMaxHp() * ratio)
                    .build();
            body.setPartStates(index, part);
        }
        restore(body.build());
    }

    Villager reloadFighter() {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, fighter.registryAccess());
        fighter.saveWithoutId(output);
        var saved = output.buildResult();
        fighter.discard();
        var level = context.getLevel();
        var reloaded = EntityTypes.VILLAGER.create(level, EntitySpawnReason.LOAD);
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, reloaded.registryAccess(), saved));
        reloaded.setNoAi(true /* aiDisabled */);
        context.assertTrue(level.addFreshEntity(reloaded), "重载村民未加入世界");
        return reloaded;
    }

    private void restore(BodyMessages.BodySnapshot snapshot) {
        HumanoidTechniqueHost.removed(fighter);
        var saved = new HumanoidBodyState.SavedBody(fighter.getUUID(), snapshot.toByteArray(), new byte[0][],
                HumanoidFabric.region(fighter).sourceId());
        var body = HumanoidBodyState.restore(fighter.getUUID(), saved, HumanoidFabric.now(), HumanoidSpecies.VILLAGER);
        ((HumanoidEntityAccess) fighter).meridian$humanoidBody(body);
        HumanoidTechniqueHost.prepare(fighter);
    }

    private static void place(Mob mob, Vec3 position) {
        mob.snapTo(position.x, position.y, position.z, 0, 0);
        mob.setNoAi(true /* aiDisabled */);
        mob.setPersistenceRequired();
    }

    private static BlockPos arena(GameTestHelper context, int lane) {
        var level = context.getLevel();
        BlockPos origin = context.absolutePos(BlockPos.ZERO);
        int x = origin.getX() + OFFSET + lane * SPACING;
        int z = origin.getZ() + OFFSET;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        var base = new BlockPos(x, Math.max(y, origin.getY()), z);
        int minChunkX = ((x - 1) >> CHUNK_SHIFT) - 1;
        int maxChunkX = ((x + WIDTH) >> CHUNK_SHIFT) + 1;
        int minChunkZ = ((z - 1) >> CHUNK_SHIFT) - 1;
        int maxChunkZ = ((z + LENGTH) >> CHUNK_SHIFT) + 1;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, true /* forced */);
            }
        }
        floor(context, base);
        return base;
    }

    private static void floor(GameTestHelper context, BlockPos base) {
        var level = context.getLevel();
        for (int x = -1; x <= WIDTH; x++) {
            for (int z = -1; z <= LENGTH; z++) {
                level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y < HEIGHT; y++) {
                    var block = Blocks.AIR;
                    if (x == -1 || x == WIDTH || z == -1 || z == LENGTH) {
                        block = Blocks.BARRIER;
                    }
                    level.setBlock(base.offset(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @Override
    public void close() {
        fighter.discard();
        target.discard();
    }
}
