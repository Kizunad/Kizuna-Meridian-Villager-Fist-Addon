package dev.kizuna.meridian.villager.verification;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueCatalog;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 卷轴物品图、阅读背景、每式的战斗图标和木人桩的模型贴图都在正式包里，尺寸与功法库、原版的约定一致。 */
public final class VillagerFistArtTest {
    private static final String PREFIX = VillagerFistContent.NAMESPACE + ":";
    private static final String ASSETS = "assets/" + VillagerFistContent.NAMESPACE + "/";
    private static final String TEXTURES = ASSETS + "textures/";
    private static final int SCROLL_PIXELS = 32;
    private static final int BACKGROUND_WIDTH = 1536;
    private static final int BACKGROUND_HEIGHT = 1024;
    private static final int TECHNIQUE_ICON_PIXELS = 32;
    private static final int LARGE_TECHNIQUE_ICON_PIXELS = 64;
    private static final int DUMMY_TEXTURE_PIXELS = 64;
    /** 原版方块模型元素的坐标范围，和每个元素允许的旋转角度。 */
    private static final double ELEMENT_MIN = -16;
    private static final double ELEMENT_MAX = 32;
    private static final Set<Double> ELEMENT_ANGLES = Set.of(-45.0, -22.5, 0.0, 22.5, 45.0);
    /** 立柱、三只木臂、大腿和小腿、两根木脚。 */
    private static final int DUMMY_ELEMENTS = 10;
    private static final List<String> CORNERS = List.of("from", "to");
    /** 面上引用贴图表里的键时带的前缀。 */
    private static final String TEXTURE_REFERENCE_PREFIX = "#";

    @Test
    public void scrollsBackgroundsAndTechniqueIconsCoverTheCatalog() throws IOException {
        // 卷轴物品 ID 是 <卷>_scroll（不生成卷轴的卷没有这张图），背景按卷路径取；
        // 每式 icon_id 与式 ID 同名，带 32 和 64 两档。
        TechniqueCatalog catalog = VillagerFistContent.catalog();
        assertTrue(catalog.getVolumesCount() > 0);
        for (var volume : catalog.getVolumesList()) {
            String path = volume.getId().substring(PREFIX.length());
            if (!volume.hasScrollItem() || volume.getScrollItem()) {
                assertImage(TEXTURES + "item/" + path + "_scroll.png", SCROLL_PIXELS, SCROLL_PIXELS);
            }
            assertImage(TEXTURES + "gui/scroll/" + path + ".png", BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        }
        for (TechniqueDefinition definition : catalog.getDefinitionsList()) {
            String icon = definition.getPresentation().getIconId();
            assertEquals(definition.getId(), definition.getId(), icon);
            String path = TEXTURES + "gui/technique/" + icon.substring(PREFIX.length());
            assertImage(path + ".png", TECHNIQUE_ICON_PIXELS, TECHNIQUE_ICON_PIXELS);
            assertImage(path + "_64.png", LARGE_TECHNIQUE_ICON_PIXELS, LARGE_TECHNIQUE_ICON_PIXELS);
        }
    }

    @Test
    public void woodenDummyModelUsesItsOwnTextureAndStaysInsideVanillaLimits() throws IOException {
        // 模型只引用本仓自己的那张木纹图；元素个数、坐标范围和旋转角度都在原版方块模型允许的范围里。
        JsonObject model = json(ASSETS + "models/block/wooden_dummy.json");
        JsonObject textures = model.getAsJsonObject("textures");
        for (String key : textures.keySet()) {
            String reference = textures.get(key).getAsString();
            assertTrue(key + " 指向 " + reference, reference.startsWith(PREFIX));
            assertImage(TEXTURES + reference.substring(PREFIX.length()) + ".png",
                    DUMMY_TEXTURE_PIXELS, DUMMY_TEXTURE_PIXELS);
        }
        assertTrue(textures.has("particle"));
        var elements = model.getAsJsonArray("elements");
        assertEquals(DUMMY_ELEMENTS, elements.size());
        for (JsonElement element : elements) {
            assertElement(element.getAsJsonObject(), textures);
        }
        // 物品沿用方块模型，方块状态四个朝向都指向这一个模型。
        JsonObject states = json(ASSETS + "blockstates/wooden_dummy.json").getAsJsonObject("variants");
        assertEquals(Set.of("facing=north", "facing=east", "facing=south", "facing=west"), states.keySet());
        for (String state : states.keySet()) {
            assertEquals(PREFIX + "block/wooden_dummy", states.getAsJsonObject(state).get("model").getAsString());
        }
    }

    private static void assertElement(JsonObject element, JsonObject textures) {
        // 一个元素：两个角点在范围里，转角是允许的那几档，每个面贴的图都在模型的贴图表里。
        String name = element.get("name").getAsString();
        for (String corner : CORNERS) {
            for (JsonElement value : element.getAsJsonArray(corner)) {
                double coordinate = value.getAsDouble();
                assertTrue(name, coordinate >= ELEMENT_MIN && coordinate <= ELEMENT_MAX);
            }
        }
        if (element.has("rotation")) {
            double angle = element.getAsJsonObject("rotation").get("angle").getAsDouble();
            assertTrue(name + " 转了 " + angle, ELEMENT_ANGLES.contains(angle));
        }
        JsonObject faces = element.getAsJsonObject("faces");
        for (String face : faces.keySet()) {
            String texture = faces.getAsJsonObject(face).get("texture").getAsString();
            assertTrue(name + "." + face, textures.has(texture.substring(TEXTURE_REFERENCE_PREFIX.length())));
        }
    }

    private static JsonObject json(String path) throws IOException {
        // 读正式包里的一个 JSON 对象。
        Path file = VillagerFistContent.fabricResources().resolve(path);
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void assertImage(String path, int width, int height) throws IOException {
        // 贴图能被 ImageIO 解码，尺寸与约定一致。
        Path file = VillagerFistContent.fabricResources().resolve(path);
        assertTrue(file.toString(), Files.isRegularFile(file));
        BufferedImage image = ImageIO.read(file.toFile());
        assertNotNull(file.toString(), image);
        assertEquals(path, width, image.getWidth());
        assertEquals(path, height, image.getHeight());
    }
}
