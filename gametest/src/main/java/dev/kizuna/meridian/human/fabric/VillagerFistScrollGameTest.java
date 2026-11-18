package dev.kizuna.meridian.human.fabric;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

/** 上、中两卷的卷轴物品都注册了，并且都摆在袖手拳那一页创造栏里。 */
public final class VillagerFistScrollGameTest {
    private static final String NAMESPACE = "meridian_villager";
    private static final Identifier TAB = Identifier.fromNamespaceAndPath(NAMESPACE, "villager_fist");
    private static final Identifier OUTER_SCROLL = Identifier.fromNamespaceAndPath(NAMESPACE, "outer_scroll");
    private static final Identifier INNER_SCROLL = Identifier.fromNamespaceAndPath(NAMESPACE, "inner_scroll");
    private static final int TIMEOUT_TICKS = 20;
    private static final boolean WITHOUT_OPERATOR_ITEMS = false;

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void bothScrollsAreRegisteredAndListedInTheCreativeTab(GameTestHelper context) {
        // 创造栏的内容是打开时才列的，这里照客户端打开那一页的入口列一遍。
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(TAB);
        context.assertTrue(tab != null, "袖手拳的创造栏没有注册");
        var level = context.getLevel();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                level.enabledFeatures(), WITHOUT_OPERATOR_ITEMS, level.registryAccess()));
        List<Identifier> listed = tab.getDisplayItems().stream()
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .toList();
        for (Identifier scroll : List.of(OUTER_SCROLL, INNER_SCROLL)) {
            context.assertTrue(BuiltInRegistries.ITEM.containsKey(scroll), "卷轴物品没有注册：" + scroll);
            context.assertTrue(listed.contains(scroll), "创造栏里没有 " + scroll + "，实际：" + listed);
        }
        context.succeed();
    }
}
