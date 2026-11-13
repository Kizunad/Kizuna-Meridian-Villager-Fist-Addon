package dev.kizuna.meridian.human.fabric;

import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.CENTER;
import static dev.kizuna.meridian.human.fabric.VillagerFistTestPlayer.HEIGHT;

import dev.kizuna.playeranimation.mobs.MobAnimationServer;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.EntityAnimationRequest;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/** 原版村民大脑：无业村民认领木人桩成为拳师，等级和类型不变；上班时间在桩前循环打桩动作。 */
public final class FistMasterGameTest {
    private static final ResourceKey<VillagerProfession> FIST_MASTER = ResourceKey.create(
            Registries.VILLAGER_PROFESSION, Identifier.parse("meridian_villager:fist_master"));
    private static final ResourceKey<PoiType> FIST_MASTER_SITE = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE, Identifier.parse("meridian_villager:fist_master"));
    private static final Identifier WOODEN_DUMMY = Identifier.parse("meridian_villager:wooden_dummy");
    private static final String ANIMATION = "villager_dummy_drill";
    /** 桩在结构一头，村民从另一头走过去认领。 */
    private static final BlockPos POST = new BlockPos(CENTER, HEIGHT, CENTER + 2);
    private static final BlockPos VILLAGER = new BlockPos(CENTER, HEIGHT, CENTER - 2);
    /** 默认空结构 8×8×8 全是空气：在桩脚下铺一层石地板，村民和桩站在同一层，站点范围 1 格才走得到。 */
    private static final int STRUCTURE_SIZE = 8;
    private static final int FLOOR_Y = HEIGHT - 1;
    /** 原版工作行为过了 300 tick 冷却后每次 50% 起手，留足两分钟。 */
    private static final int TIMEOUT_TICKS = 2400;

    @GameTest(environment = "meridian_villager_test:work_hours", maxTicks = TIMEOUT_TICKS)
    public void unemployedVillagerBecomesAFistMasterAndDrillsAtWork(GameTestHelper context) {
        // 时钟停在 3000（村民日程 2000–9000 是上班）；先确认桩是可认领站点，成为拳师后原版工作行为换成打桩，PA Mobs 记下循环动作。
        floor(context);
        context.setBlock(POST, BuiltInRegistries.BLOCK.getValue(WOODEN_DUMMY));
        var site = context.getLevel().getPoiManager().getType(context.absolutePos(POST));
        boolean acquirable = site.isPresent() && site.get().is(FIST_MASTER_SITE)
                && site.get().is(PoiTypeTags.ACQUIRABLE_JOB_SITE);
        context.assertTrue(acquirable, "木人桩没有登记成可认领的拳师工作站点：" + site);
        Villager villager = context.spawn(EntityTypes.VILLAGER, VILLAGER);
        VillagerData before = villager.getVillagerData();
        context.assertTrue(before.profession().is(VillagerProfession.NONE), "测试村民一开始就有职业");
        context.succeedWhen(() -> {
            VillagerData data = villager.getVillagerData();
            context.assertTrue(data.profession().is(FIST_MASTER), "村民还没认领木人桩：" + data.profession());
            context.assertTrue(data.level() == before.level(), "成为拳师后等级变了：" + data.level());
            context.assertTrue(data.type().equals(before.type()), "成为拳师后类型变了：" + data.type());
            boolean drilling = MobAnimationServer.snapshot(villager).stream()
                    .anyMatch(FistMasterGameTest::isDrillLoop);
            context.assertTrue(drilling, "上班时间拳师没有循环打桩动作");
        });
    }

    private static void floor(GameTestHelper context) {
        // 整个结构底下铺满一层石头。
        for (int x = 0; x < STRUCTURE_SIZE; x++) {
            for (int z = 0; z < STRUCTURE_SIZE; z++) {
                context.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
            }
        }
    }

    private static boolean isDrillLoop(EntityAnimationRequest request) {
        // 打桩动作按循环播放登记。
        return request.hasPlay()
                && request.getPlay().getAnimationId().equals(ANIMATION)
                && request.getPlay().getLoop();
    }
}
