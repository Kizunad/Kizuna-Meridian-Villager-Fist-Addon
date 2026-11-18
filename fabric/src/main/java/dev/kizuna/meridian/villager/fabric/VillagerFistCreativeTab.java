package dev.kizuna.meridian.villager.fabric;

import dev.kizuna.meridian.technique.fabric.TechniqueScrollItems;
import java.util.Objects;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** 袖手拳独占一页创造栏：木人桩和上、中两卷的卷轴。卷轴是功法库按卷生成的物品，这里放同一个物品。 */
public final class VillagerFistCreativeTab {
    public static final Identifier ID = VillagerFistFabric.id("villager_fist");
    public static final Identifier OUTER_SCROLL = VillagerFistFabric.id("outer_scroll");
    public static final Identifier INNER_SCROLL = VillagerFistFabric.id("inner_scroll");

    private VillagerFistCreativeTab() {
        // 创造栏只在模组初始化时注册一次。
    }

    static void register() {
        // 条目在打开栏时才取，功法库的卷轴那时一定已经注册。
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.meridian_villager.villager_fist"))
                .icon(() -> new ItemStack(VillagerFistBlocks.woodenDummy()))
                .displayItems((context, output) -> {
                    output.accept(VillagerFistBlocks.woodenDummy());
                    output.accept(scroll(OUTER_SCROLL));
                    output.accept(scroll(INNER_SCROLL));
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ID, tab);
    }

    private static ItemStack scroll(Identifier id) {
        // 这两卷有卷轴是目录声明的，缺了就是目录或功法库出错，直接失败。
        var scroll = Objects.requireNonNull(TechniqueScrollItems.scrolls().get(id), "卷轴没有注册：" + id);
        return new ItemStack(scroll);
    }
}
