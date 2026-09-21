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
 * "候选名 + hasChild 过滤" 的方式定位部件。候选名覆盖了 8 种支持生物
 * (人形/苦力怕/蜘蛛)的全部根级部件命名:
 * - 人形: head / body / left_arm / right_arm / left_leg / right_leg
 * - 苦力怕: head / body / left_leg / right_leg / left_hind_leg / right_hind_leg
 * - 蜘蛛: head / body0 / body1 / left_front_leg / left_middle_front_leg /
 *          left_middle_hind_leg / left_hind_leg(右侧同理)
 */
public final class MobPartRenderRegistry {
    public record MobPartRenderData(ModelLayerLocation layer, ResourceLocation texture) {
    }

    private static final List<String> HEADS = List.of("head");
    private static final List<String> TORSOS = List.of("body", "body0", "body1");
    private static final List<String> ARM_LEFTS = List.of("left_arm");
    private static final List<String> ARM_RIGHTS = List.of("right_arm");
    private static final List<String> LEG_LEFTS = List.of("left_leg", "left_hind_leg", "left_middle_hind_leg", "left_middle_front_leg", "left_front_leg");
    private static final List<String> LEG_RIGHTS = List.of("right_leg", "right_hind_leg", "right_middle_hind_leg", "right_middle_front_leg", "right_front_leg");

    /** 所有可能出现的根级部件名,渲染器用它先把整个模型隐藏 */
    public static final List<String> ALL_CANDIDATE_NAMES = java.util.stream.Stream.of(
            HEADS, TORSOS, ARM_LEFTS, ARM_RIGHTS, LEG_LEFTS, LEG_RIGHTS)
            .flatMap(List::stream)
            .toList();

    private static final Map<ResourceLocation, MobPartRenderData> REGISTRY = new HashMap<>();

    private static void register(String mobPath, ModelLayerLocation layer, String texturePath) {
        REGISTRY.put(ResourceLocation.withDefaultNamespace(mobPath),
                new MobPartRenderData(layer, ResourceLocation.withDefaultNamespace(texturePath)));
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
    }

    public static MobPartRenderData get(ResourceLocation mobId) {
        return REGISTRY.get(mobId);
    }

    /**
     * 部位 -> 需要显示的模型部件名列表(候选名里真实存在的子部件)
     */
    public static List<String> partNames(BodyPartType part, ModelPart root) {
        return candidatesFor(part).stream()
                .filter(root::hasChild)
                .toList();
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
