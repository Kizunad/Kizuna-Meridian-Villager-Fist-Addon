package dev.kizuna.meridian.villager.fabric;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;

/**
 * 拳师职业与木人桩工作站点。无业村民认领木人桩就成了拳师，工作声是木头受击。
 * 交易是 26.2 的数据：每级一份 {@code trade_set/fist_master/level_N}。
 */
public final class FistMasterProfession {
    public static final Identifier ID = VillagerFistFabric.id("fist_master");
    public static final ResourceKey<VillagerProfession> KEY = ResourceKey.create(Registries.VILLAGER_PROFESSION, ID);
    public static final ResourceKey<PoiType> JOB_SITE = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ID);
    // 与原版职业工作站点相同：一根桩只给一个村民，站点范围 1 格。
    private static final int MAX_TICKETS = 1;
    private static final int VALID_RANGE = 1;
    private static final int MAX_LEVEL = 5;

    private FistMasterProfession() {
        // 工作站点和职业只在模组初始化时注册一次。
    }

    static void register() {
        // 工作站点按木人桩的全部朝向登记；职业的持有和认领都只认这个站点。
        PoiHelper.register(ID, MAX_TICKETS, VALID_RANGE, VillagerFistBlocks.woodenDummy());
        Predicate<Holder<PoiType>> jobSite = holder -> holder.is(JOB_SITE);
        Component name = Component.translatable("entity." + ID.getNamespace() + ".villager." + ID.getPath());
        VillagerProfession profession = new VillagerProfession(name, jobSite, jobSite,
                ImmutableSet.of(), ImmutableSet.of(), SoundEvents.WOOD_HIT, tradeSets());
        Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, ID, profession);
    }

    /** 村民现在是不是拳师。 */
    public static boolean is(Villager villager) {
        // 只看职业，不看等级。
        return villager.getVillagerData().profession().is(KEY);
    }

    private static Int2ObjectMap<ResourceKey<TradeSet>> tradeSets() {
        // 新手到大师五级，每级一份交易表。
        Int2ObjectMap<ResourceKey<TradeSet>> sets = new Int2ObjectOpenHashMap<>();
        for (int level = 1; level <= MAX_LEVEL; level++) {
            Identifier set = VillagerFistFabric.id(ID.getPath() + "/level_" + level);
            sets.put(level, ResourceKey.create(Registries.TRADE_SET, set));
        }
        return sets;
    }
}
