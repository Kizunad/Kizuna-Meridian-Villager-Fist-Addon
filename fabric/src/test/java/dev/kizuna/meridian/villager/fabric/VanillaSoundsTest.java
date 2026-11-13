package dev.kizuna.meridian.villager.fabric;

import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueCatalog;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import dev.kizuna.meridian.technique.server.TechniqueCatalogManifest;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 目录里的声音都借原版事件，本仓不带 sounds.json；引导原版注册表逐个查它们真的存在。 */
public final class VanillaSoundsTest {
    private static final String CATALOG = "data/meridian_villager/technique_catalog.txtpb";

    @BeforeClass
    public static void bootstrap() {
        // 原版声音注册表在引导时填好，不需要服务器实例。
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void everyCatalogSoundIsAVanillaEvent() throws IOException {
        // 声音 ID 都在 minecraft 命名空间，并且已在原版注册表里。
        Set<String> sounds = soundIds(catalog());
        assertFalse(sounds.isEmpty());
        for (String sound : sounds) {
            Identifier id = Identifier.parse(sound);
            assertEquals(sound, Identifier.DEFAULT_NAMESPACE, id.getNamespace());
            assertTrue(sound, BuiltInRegistries.SOUND_EVENT.containsKey(id));
        }
    }

    private static Set<String> soundIds(TechniqueCatalog catalog) {
        // 收集所有声音效果的事件 ID。
        return catalog.getDefinitionsList().stream()
                .flatMap(definition -> definition.getEffectsList().stream())
                .filter(TechniqueEffect::hasSound)
                .map(effect -> effect.getSound().getSoundId())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static TechniqueCatalog catalog() throws IOException {
        // 目录由 shared 的资源打进本模块，和正式发现用同一个解析器。
        try (InputStream stream = VanillaSoundsTest.class.getClassLoader().getResourceAsStream(CATALOG)) {
            assertNotNull(CATALOG, stream);
            byte[] bytes = stream.readNBytes(TechniqueCatalogManifest.MAX_BYTES + 1);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return TechniqueCatalogManifest.parseText(VillagerFistFabric.MOD_ID + ":" + CATALOG, text);
        }
    }
}
