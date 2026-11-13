package dev.kizuna.meridian.villager.verification;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueCatalog;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 正式包的伤害类型、伤害标签、打桩目标标签和语言条目逐项对上目录；所有 JSON 都能解析。 */
public final class VillagerFistResourcesTest {
    private static final String DATA = "data/" + VillagerFistContent.NAMESPACE + "/";
    private static final String ASSETS = "assets/" + VillagerFistContent.NAMESPACE + "/";
    private static final String PREFIX = VillagerFistContent.NAMESPACE + ":";
    private static final List<String> LANGUAGES = List.of("zh_cn", "en_us");
    /** 拳掌伤害进 Damage 的钝击、近战、打躯干三条标签；双手的另进 two_handed。 */
    private static final List<String> STRIKE_TAGS = List.of("kind/blunt", "melee", "aim/torso");
    private static final String TWO_HANDED_TAG = "two_handed";
    private static final String BOTH_ARMS_TYPE = PREFIX + "fist_both_torso";
    /** 背摔落地和一靠：双手、打躯干。 */
    private static final String THROW_TYPE = PREFIX + "throw";
    private static final Set<String> UNTHROWABLE = Set.of("minecraft:iron_golem", "minecraft:ravager",
            "minecraft:warden", "minecraft:wither", "minecraft:ender_dragon", "minecraft:elder_guardian",
            "minecraft:creaking");
    private static final Set<String> DRILL_BLOCKS = Set.of(PREFIX + "wooden_dummy", "#minecraft:logs",
            "minecraft:hay_block");
    private static final Set<String> DRILL_ENTITIES = Set.of("minecraft:armor_stand", "minecraft:mannequin",
            "meridian_humanoid_dummy:dummy");

    @Test
    public void everyJsonFileParses() throws IOException {
        // 数据包和资源包里每个 JSON 都是对象，打包前就把手误挡住。
        List<Path> files;
        try (Stream<Path> walk = Files.walk(VillagerFistContent.fabricResources())) {
            files = walk.filter(path -> path.toString().endsWith(".json")).toList();
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            assertTrue(file.toString(), parsed.isJsonObject());
        }
    }

    @Test
    public void catalogDamageTypesAreRegisteredAndTagged() throws IOException {
        // 目录引用的每个伤害类型在本仓注册，进钝击、近战、躯干标签；双手出拳和背摔进 two_handed。
        // 背摔落地砸在原地，原版受伤时的击退不要（一靠的推开另走 knockback），所以只有它进原版 no_knockback。
        Set<String> used = damageTypes(VillagerFistContent.catalog());
        assertEquals(Set.of(PREFIX + "fist_rear_torso", BOTH_ARMS_TYPE, THROW_TYPE), used);
        for (String type : used) {
            assertTrue(type, Files.isRegularFile(resource(DATA + "damage_type/" + local(type) + ".json")));
            for (String tag : STRIKE_TAGS) {
                assertTrue(type + " 缺 " + tag, damageTag(tag).contains(type));
            }
        }
        assertEquals(Set.of(BOTH_ARMS_TYPE, THROW_TYPE), damageTag(TWO_HANDED_TAG));
        JsonObject noKnockback = json("data/minecraft/tags/damage_type/no_knockback.json");
        assertFalse(noKnockback.get("replace").getAsBoolean());
        assertEquals(Set.of(THROW_TYPE), values(noKnockback));
    }

    @Test
    public void unthrowableTagListsTheHeavyMobsFromTheSpec() throws IOException {
        // 摔不动的默认名单：铁傀儡、劫掠兽、监守者、凋灵、末影龙、远古守卫者、嘎枝。
        assertEquals(UNTHROWABLE, tagValues(DATA + "tags/entity_type/unthrowable.json"));
    }

    @Test
    public void drillTargetTagsListThePostsFromTheSpec() throws IOException {
        // 方块桩：木人桩、原木、草垛；实体桩：盔甲架、玩家模型、训练假人。
        assertEquals(DRILL_BLOCKS, tagValues(DATA + "tags/block/drill_targets.json"));
        assertEquals(DRILL_ENTITIES, tagValues(DATA + "tags/entity_type/drill_targets.json"));
    }

    @Test
    public void namesAndNotesCoverTheCatalogInEveryLanguage() throws IOException {
        // 书、卷、式名和每式卷轴补充说明在中英语言包里都有，两份语言包的键完全一致。
        TechniqueCatalog catalog = VillagerFistContent.catalog();
        Set<String> keys = new TreeSet<>();
        catalog.getBooksList().forEach(book -> keys.add("book." + translationPath(book.getId())));
        catalog.getVolumesList().forEach(volume -> keys.add("volume." + translationPath(volume.getId())));
        for (TechniqueDefinition definition : catalog.getDefinitionsList()) {
            keys.add("technique." + translationPath(definition.getId()));
            keys.add("technique." + translationPath(definition.getId()) + ".note");
        }
        Set<String> chinese = language(LANGUAGES.getFirst()).keySet();
        for (String language : LANGUAGES) {
            Set<String> present = language(language).keySet();
            assertTrue(language, present.containsAll(keys));
            assertEquals(language, chinese, present);
        }
    }

    private static Set<String> damageTypes(TechniqueCatalog catalog) {
        // 收集肢击、一靠、背摔落地引用的伤害类型。
        return catalog.getDefinitionsList().stream()
                .flatMap(definition -> definition.getEffectsList().stream())
                .map(VillagerFistResourcesTest::damageType)
                .filter(type -> !type.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String damageType(TechniqueEffect effect) {
        // 不带伤害的效果返回空串。
        return switch (effect.getOperationCase()) {
            case LIMB_STRIKE -> effect.getLimbStrike().getDamageTypeId();
            case GRAPPLE -> effect.getGrapple().getLean().getDamageTypeId();
            case THROW -> effect.getThrow().getDamageTypeId();
            default -> "";
        };
    }

    private static Set<String> damageTag(String tag) throws IOException {
        // Damage 的伤害标签在 meridian 命名空间下，本仓只追加不替换。
        JsonObject json = json("data/meridian/tags/damage_type/" + tag + ".json");
        assertFalse(tag, json.get("replace").getAsBoolean());
        return values(json);
    }

    private static Set<String> tagValues(String path) throws IOException {
        // 读一个标签文件的条目。
        return values(json(path));
    }

    private static Set<String> values(JsonObject tag) {
        // 标签条目是字符串数组。
        Set<String> values = new TreeSet<>();
        tag.getAsJsonArray("values").forEach(element -> values.add(element.getAsString()));
        return values;
    }

    private static JsonObject language(String language) throws IOException {
        // 语言包路径固定在本仓 assets 下。
        return json(ASSETS + "lang/" + language + ".json");
    }

    private static JsonObject json(String path) throws IOException {
        // 按资源根下的相对路径读 JSON 对象。
        return JsonParser.parseString(Files.readString(resource(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Path resource(String path) {
        // 资源都从 fabric 模块源目录取。
        return VillagerFistContent.fabricResources().resolve(path);
    }

    private static String local(String id) {
        // 去掉本仓命名空间前缀。
        return id.substring(PREFIX.length());
    }

    private static String translationPath(String id) {
        // 语言键把 ID 的冒号和斜杠换成点。
        return id.replace(':', '.').replace('/', '.');
    }
}
