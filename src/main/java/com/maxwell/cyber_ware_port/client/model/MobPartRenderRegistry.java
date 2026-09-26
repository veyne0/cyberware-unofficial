package com.maxwell.cyber_ware_port.client.model;

import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生物部位渲染注册表:mob id -> (原版模型层, 原版实体贴图)。
 * 部位物品直接复用原版生物模型和贴图,零新增贴图成本。
 *
 * ModelPart.children 是私有的,无法枚举子部件,因此这里改用
 * "候选名 + hasChild 过滤" 的方式定位部件。候选名覆盖了支持生物
 * (人形/苦力怕/蜘蛛)的全部根级部件命名:
 * - 人形: head / body / left_arm / right_arm / left_leg / right_leg
 * - 苦力怕: head / body / left_leg / right_leg / left_hind_leg / right_hind_leg
 * - 蜘蛛: head / body0 / body1 / left_front_leg / left_middle_front_leg /
 *          left_middle_hind_leg / left_hind_leg(右侧同理)
 */
public final class MobPartRenderRegistry {
    public record MobPartRenderData(ModelLayerLocation layer, ResourceLocation texture) {
    }

    private static final List<String> HEADS = List.of("head", "center_head", "cube");
    private static final List<String> TORSOS = List.of("body", "body0", "body1", "upper_body", "ribcage");
    private static final List<String> ARM_LEFTS = List.of("left_arm");
    private static final List<String> ARM_RIGHTS = List.of("right_arm");
    private static final List<String> LEG_LEFTS = List.of("left_leg", "left_hind_leg", "left_middle_hind_leg", "left_middle_front_leg", "left_front_leg");
    private static final List<String> LEG_RIGHTS = List.of("right_leg", "right_hind_leg", "right_middle_hind_leg", "right_middle_front_leg", "right_front_leg");

    /** 所有可能出现的根级部件名,渲染器用它先把整个模型隐藏 */
    public static final List<String> ALL_CANDIDATE_NAMES = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(HEADS, TORSOS, ARM_LEFTS, ARM_RIGHTS, LEG_LEFTS, LEG_RIGHTS)
                    .flatMap(List::stream),
            // 凋零模型的无主部件(不映射到玩家任何部位,但需要隐藏)
            java.util.stream.Stream.of("shoulders", "tail", "right_head", "left_head")
    ).toList();

    private static final Map<ResourceLocation, MobPartRenderData> REGISTRY = new HashMap<>();

    private static void register(String mobPath, ModelLayerLocation layer, String texturePath) {
        REGISTRY.put(new ResourceLocation("minecraft", mobPath),
                new MobPartRenderData(layer, new ResourceLocation("minecraft", texturePath)));
    }

    static {
        register("zombie", ModelLayers.ZOMBIE, "textures/entity/zombie/zombie.png");
        register("husk", ModelLayers.HUSK, "textures/entity/zombie/husk.png");
        register("drowned", ModelLayers.DROWNED, "textures/entity/zombie/drowned.png");
        register("skeleton", ModelLayers.SKELETON, "textures/entity/skeleton/skeleton.png");
        register("stray", ModelLayers.STRAY, "textures/entity/skeleton/stray.png");
        register("wither_skeleton", ModelLayers.WITHER_SKELETON, "textures/entity/skeleton/wither_skeleton.png");
        register("creeper", ModelLayers.CREEPER, "textures/entity/creeper/creeper.png");
        register("spider", ModelLayers.SPIDER, "textures/entity/spider/spider.png");
        register("zombified_piglin", ModelLayers.ZOMBIFIED_PIGLIN, "textures/entity/piglin/zombified_piglin.png");
        register("piglin", ModelLayers.PIGLIN, "textures/entity/piglin/piglin.png");
        register("piglin_brute", ModelLayers.PIGLIN_BRUTE, "textures/entity/piglin/piglin_brute.png");
        register("witch", ModelLayers.WITCH, "textures/entity/witch.png");
        register("enderman", ModelLayers.ENDERMAN, "textures/entity/enderman/enderman.png");
        register("pig", ModelLayers.PIG, "textures/entity/pig/pig.png");
        register("cow", ModelLayers.COW, "textures/entity/cow/cow.png");
        register("sheep", ModelLayers.SHEEP, "textures/entity/sheep/sheep.png");
        register("goat", ModelLayers.GOAT, "textures/entity/goat/goat.png");
        register("wolf", ModelLayers.WOLF, "textures/entity/wolf/wolf.png");
        register("chicken", ModelLayers.CHICKEN, "textures/entity/chicken.png");
        register("iron_golem", ModelLayers.IRON_GOLEM, "textures/entity/iron_golem/iron_golem.png");
        register("snow_golem", ModelLayers.SNOW_GOLEM, "textures/entity/snow_golem.png");
        register("evoker", ModelLayers.EVOKER, "textures/entity/illager/evoker.png");
        register("warden", ModelLayers.WARDEN, "textures/entity/warden/warden.png");
        register("guardian", ModelLayers.GUARDIAN, "textures/entity/guardian.png");
        // 注意:1.20.1 没有专门的 ELDER_GUARDIAN 模型层(远古守卫者复用守卫者模型+缩放),
        // 所以这里用 GUARDIAN 层;原版远古守卫者贴图文件名是 guardian_elder(不是 elder_guardian)
        register("elder_guardian", ModelLayers.GUARDIAN, "textures/entity/guardian_elder.png");
        register("slime", ModelLayers.SLIME, "textures/entity/slime/slime.png");
        register("strider", ModelLayers.STRIDER, "textures/entity/strider/strider.png");
        register("wither", ModelLayers.WITHER, "textures/entity/wither/wither.png");
        // 闪电苦力怕:苦力怕模型 + 充能变体贴图
        register("lightning_creeper", ModelLayers.CREEPER, "textures/entity/creeper/creeper_armor.png");
    }

    public static MobPartRenderData get(ResourceLocation mobId) {
        return REGISTRY.get(mobId);
    }

    /** 部位 -> 需要显示的模型部件名列表(候选名里真实存在的子部件) */
    public static List<String> partNames(BodyPartType part, ModelPart root) {
        return candidatesFor(part).stream()
                .filter(name -> findPart(root, name) != null)
                .toList();
    }

    /**
     * 需要与主部件一起渲染的附加子部件:
     * 1.21.1 的史莱姆内核层眼睛/嘴是根级子部件;1.20.1 的史莱姆模型
     * 只有 cube 一个部件(脸画在内核贴图上),这些名字查不到会自动跳过。
     */
    private static final Map<ResourceLocation, List<String>> EXTRA_PARTS = Map.of(
            new ResourceLocation("minecraft", "slime"), List.of("right_eye", "left_eye", "mouth"));

    /** 指定生物在渲染主部件外还需一并渲染的附加子部件名 */
    public static List<String> extraParts(ResourceLocation mobId) {
        return EXTRA_PARTS.getOrDefault(mobId, List.of());
    }

    /** 可能作为部件父级的中间节点(监守者等模型部件嵌套在 bone/body 下) */
    private static final List<String> NEST_INTERMEDIATES = List.of(
            "root", "bone", "body", "body0", "body1", "upper_body", "lower_body",
            "left_ribcage", "right_ribcage");

    /** 递归查找指定名称的部件(先查当前层,再下钻嵌套层级) */
    public static ModelPart findPart(ModelPart root, String name) {
        if (root.hasChild(name)) return root.getChild(name);
        for (String mid : NEST_INTERMEDIATES) {
            if (root.hasChild(mid)) {
                ModelPart found = findPart(root.getChild(mid), name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<String> candidatesFor(BodyPartType part) {
        return switch (part) {
            case HEAD -> HEADS;
            case TORSO -> TORSOS;
            case ARM_LEFT -> ARM_LEFTS;
            case ARM_RIGHT -> ARM_RIGHTS;
            case LEG_LEFT -> LEG_LEFTS;
            case LEG_RIGHT -> LEG_RIGHTS;
            default -> List.of();
        };
    }

    private MobPartRenderRegistry() {
    }
}
