package dev.kizuna.meridian.villager.fabric;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/** 木人桩方块与方块物品：一格占地、碰撞高 1.5 格，斧头挖得快，能烧。 */
public final class VillagerFistBlocks {
    public static final Identifier WOODEN_DUMMY_ID = VillagerFistFabric.id("wooden_dummy");
    // 硬度与原木相同。
    private static final float WOODEN_DUMMY_STRENGTH = 2.0F;
    // 引燃与烧毁几率与原木相同。
    private static final int IGNITE_ODDS = 5;
    private static final int BURN_ODDS = 5;
    private static Block woodenDummy;

    private VillagerFistBlocks() {
        // 方块和方块物品只在模组初始化时注册一次。
    }

    static void register() {
        // 方块物品取方块的翻译键，语言文件只写 block.meridian_villager.wooden_dummy 一份。
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, WOODEN_DUMMY_ID);
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .instrument(NoteBlockInstrument.BASS)
                .strength(WOODEN_DUMMY_STRENGTH)
                .sound(SoundType.WOOD)
                .ignitedByLava()
                .noOcclusion()
                .setId(blockKey);
        woodenDummy = Registry.register(BuiltInRegistries.BLOCK, WOODEN_DUMMY_ID, new WoodenDummyBlock(properties));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, WOODEN_DUMMY_ID);
        Item.Properties itemProperties = new Item.Properties()
                .setId(itemKey)
                .useBlockDescriptionPrefix();
        Registry.register(BuiltInRegistries.ITEM, WOODEN_DUMMY_ID, new BlockItem(woodenDummy, itemProperties));
        FlammableBlockRegistry.getDefaultInstance().add(woodenDummy, IGNITE_ODDS, BURN_ODDS);
    }

    /** 已注册的木人桩方块。 */
    public static Block woodenDummy() {
        // 注册前取用说明初始化顺序错了，直接失败。
        if (woodenDummy == null) {
            throw new IllegalStateException("木人桩还没注册");
        }
        return woodenDummy;
    }
}
