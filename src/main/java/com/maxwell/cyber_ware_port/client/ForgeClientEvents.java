package com.maxwell.cyber_ware_port.client;

import com.maxwell.cyber_ware_port.CyberWare;
import com.maxwell.cyber_ware_port.api.json.CyberwareAPI;
import com.maxwell.cyber_ware_port.client.screen.roboSurgeon.RobosurgeonScreen;
import com.maxwell.cyber_ware_port.client.upgrades.cybereye.CyberwareMenuScreen;
import com.maxwell.cyber_ware_port.common.block.robosurgeon.RobosurgeonBlockEntity;
import com.maxwell.cyber_ware_port.common.capability.CyberwareCapabilityProvider;
import com.maxwell.cyber_ware_port.common.capability.CyberwareUserData;
import com.maxwell.cyber_ware_port.common.effect.MobPartEffects;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.item.base.CyberwareSlotType;
import com.maxwell.cyber_ware_port.common.item.base.ICyberware;
import com.maxwell.cyber_ware_port.common.item.cyberware.MobPartItem;
import com.maxwell.cyber_ware_port.common.network.A_PacketHandler;
import com.maxwell.cyber_ware_port.common.network.ClientPacketHandler;
import com.maxwell.cyber_ware_port.common.network.DoubleJumpPacket;
import com.maxwell.cyber_ware_port.common.network.UseMobPartAbilityPacket;
import com.maxwell.cyber_ware_port.init.ModBlocks;
import com.maxwell.cyber_ware_port.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = CyberWare.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ForgeClientEvents {
    private static final String NBT_DOUBLE_JUMPED = "cyberware_double_jumped";

    // ==================== 生物部位技能客户端状态(V 键) ====================
    /** 当前技能列表(与 MobPartEffects.getAbilities 双端一致扫描) */
    private static List<MobPartEffects.MobAbility> mobAbilities = List.of();
    /** 当前选中的技能下标,↑/↓ 切换 */
    private static int mobAbilityIndex = 0;
    /** 技能冷却结束时刻(gameTime),由服务端 MobAbilityCooldownPacket 同步 */
    private static final Map<String, Long> MOB_ABILITY_COOLDOWNS = new HashMap<>();

    /** 收到服务端冷却同步(MobAbilityCooldownPacket 回调) */
    public static void onMobAbilityCooldown(String abilityId, long cooldownEnd) {
        MOB_ABILITY_COOLDOWNS.put(abilityId, cooldownEnd);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            mobAbilities = List.of();
            mobAbilityIndex = 0;
            MOB_ABILITY_COOLDOWNS.clear();
            return;
        }
        mobAbilities = MobPartEffects.getAbilities(player);
        if (mobAbilityIndex >= mobAbilities.size()) mobAbilityIndex = 0;
        if (mc.screen == null && !mobAbilities.isEmpty()) {
            while (KeyInit.ABILITY_KEY.consumeClick()) {
                A_PacketHandler.INSTANCE.sendToServer(new UseMobPartAbilityPacket(mobAbilityIndex));
            }
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        List<Component> tooltip = event.getToolTip();
        if (item == ModBlocks.RADIO_KIT_BLOCK.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyberware_unofficial.radio_kit").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.COMPONENT_BOX.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyberware_unofficial.component_box").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.RADIO_TOWER_CORE.get().asItem()) {
            tooltip.add(
                    Component.translatable("tooltip.cyberware_unofficial.radio_tower_core").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.SCANNER.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyberware_unofficial.scanner").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.RADIO_TOWER_COMPONENT.get().asItem()) {
            tooltip.add(
                    Component.translatable("tooltip.cyberware_unofficial.radio_component").withStyle(ChatFormatting.GRAY));
            tooltip.add(
                    Component.translatable("tooltip.cyberware_unofficial.radio_component2").withStyle(ChatFormatting.GRAY));
            tooltip.add(
                    Component.translatable("tooltip.cyberware_unofficial.radio_component3").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.CHARGER.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyberware_unofficial.charger").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.cyberware_unofficial.charger2").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.cyberware_unofficial.charger3").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.SURGERY_CHAMBER.get().asItem()) {
            tooltip.add(
                    Component.translatable("tooltip.cyberware_unofficial.surgery_chamber").withStyle(ChatFormatting.GRAY));
            tooltip.add(
                    Component.translatable("tooltip.cyberware_unofficial.surgery_chamber2").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.BLUEPRINT_CHEST.get().asItem()) {
            tooltip.add(
                    Component.translatable("tooltip.cyberware_unofficial.blueprint_chest").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.CYBERWARE_WORKBENCH.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyberware_unofficial.cyberware_workbench")
                    .withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.ROBO_SURGEON.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyberware_unofficial.robo_surgeon").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.cyberware_unofficial.robo_surgeon2").withStyle(ChatFormatting.GRAY));
        }
        if (stack.hasTag() && stack.getTag().getBoolean("cyberware_ghost")) {
            Component name = tooltip.isEmpty() ? stack.getHoverName() : tooltip.get(0);
            tooltip.clear();
            tooltip.add(name);
            tooltip.add(Component.translatable("cyberware.tooltip.ghost.remove").withStyle(ChatFormatting.RED));
            // 生物部位:即使是 GUI 中的 ghost 显示,也列出特性/技能
            if (stack.getItem() instanceof MobPartItem) {
                addEffectTooltipLines(tooltip, MobPartItem.getMobId(stack), MobPartItem.getPart(stack));
                ICyberware ghostCyberware = CyberwareAPI.getCyberware(stack);
                if (ghostCyberware != null) {
                    tooltip.add(Component.translatable("cyberware.tooltip.essence", ghostCyberware.getEssenceCost(stack))
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            return;
        }
        ICyberware cyberware = CyberwareAPI.getCyberware(stack);
        if (cyberware != null) {
            ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(item);
            if (registryName != null && registryName.getPath().contains("body_part")) {
                return;
            }
            // 生物部位:直接显示特性/技能,不走通用义体 tooltip
            if (item instanceof MobPartItem) {
                tooltip.add(Component.empty());
                addEffectTooltipLines(tooltip, MobPartItem.getMobId(stack), MobPartItem.getPart(stack));
                tooltip.add(Component.translatable("cyberware.tooltip.essence", cyberware.getEssenceCost(stack))
                        .withStyle(ChatFormatting.GRAY));
                if (cyberware.isPristine(stack)) {
                    tooltip.add(Component.translatable("cyberware.quality.manufactured").withStyle(ChatFormatting.AQUA));
                } else {
                    tooltip.add(Component.translatable("cyberware.quality.scavenged").withStyle(ChatFormatting.RED));
                }
                return;
            }
            boolean isShiftDown = Screen.hasShiftDown();
            if (!isShiftDown) {
                tooltip.add(Component.empty());
                Component keyName = Component.translatable("key.keyboard.shift");
                tooltip.add(Component.translatable("cyberware.tooltip.shiftPrompt", keyName)
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                return;
            }
            if (registryName != null) {
                String key = "cyberware.tooltip." + registryName.getPath();
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.empty());
            if (cyberware.canToggle(stack)) {
                boolean isActive = cyberware.isActive(stack);
                Component statusText = Component
                        .translatable(isActive ? "cyberware.gui.active.enable" : "cyberware.gui.active.disable")
                        .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.RED);
                tooltip.add(
                        Component.translatable("cyberware.tooltip.status", statusText).withStyle(ChatFormatting.WHITE));
            }
            if (cyberware.hasEnergyProperties(stack)) {
                int consumption = cyberware.getEnergyConsumption(stack);
                if (consumption > 0) {
                    tooltip.add(Component.translatable("cyberware.tooltip.powerConsumption", consumption)
                            .withStyle(ChatFormatting.RED));
                }
                int generation = cyberware.getEnergyGeneration(stack);
                if (generation > 0) {
                    tooltip.add(Component.translatable("cyberware.tooltip.powerProduction", generation)
                            .withStyle(ChatFormatting.GREEN));
                }
                int storage = cyberware.getEnergyStorage(stack);
                if (storage > 0) {
                    tooltip.add(Component.translatable("cyberware.tooltip.capacity", storage)
                            .withStyle(ChatFormatting.AQUA));
                }
                int eventCost = cyberware.getEventConsumption(stack);
                if (eventCost > 0) {
                    tooltip.add(Component.translatable("cyberware.tooltip.eventCost", eventCost)
                            .withStyle(ChatFormatting.RED));
                }
            }
            if (cyberware.getMaxInstallAmount(stack) > 1) {
                tooltip.add(Component.translatable("cyberware.tooltip.maxInstall", cyberware.getMaxInstallAmount(stack))
                        .withStyle(ChatFormatting.BLUE));
            }
            tooltip.add(Component.translatable("cyberware.tooltip.essence", cyberware.getEssenceCost(stack))
                    .withStyle(ChatFormatting.DARK_PURPLE));
            Set<Item> reqs = cyberware.getPrerequisites(stack);
            if (!reqs.isEmpty()) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("cyberware.tooltip.requires").withStyle(ChatFormatting.AQUA));
                for (Item req : reqs) {
                    tooltip.add(Component.literal(" - ").append(req.getName(new ItemStack(req)))
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            Set<Item> incompatibles = cyberware.getIncompatibleItems(stack);
            if (!incompatibles.isEmpty()) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("cyberware.tooltip.incompatible").withStyle(ChatFormatting.RED));
                for (Item incompatible : incompatibles) {
                    tooltip.add(Component.literal(" - ").append(incompatible.getName(new ItemStack(incompatible)))
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            CyberwareSlotType slotType = CyberwareSlotType.fromId(cyberware.getSlot(stack));
            if (slotType != null) {
                tooltip.add(Component.translatable("cyberware.tooltip.slot", slotType.getDisplayName())
                        .withStyle(ChatFormatting.GRAY));
            }
            if (cyberware.isPristine(stack)) {
                tooltip.add(Component.translatable("cyberware.quality.manufactured").withStyle(ChatFormatting.AQUA));
            } else {
                tooltip.add(Component.translatable("cyberware.quality.scavenged").withStyle(ChatFormatting.RED));
            }
        }
    }

    @SubscribeEvent
    public static void onClientLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPacketHandler.reset();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null)
            return;
        if (KeyInit.MENU_KEY.consumeClick()) {
            player.getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY).ifPresent(userData -> {
                if (userData.isCyberwareInstalled(ModItems.CYBER_EYE.get())) {
                    if (mc.screen == null) {
                        mc.setScreen(new CyberwareMenuScreen());
                    }
                } else {
                    player.displayClientMessage(Component.translatable("message.cyberware_unofficial.no_hud_installed"),
                            true);
                }
            });
        }
        if (event.getKey() == mc.options.keyJump.getKey().getValue() && event.getAction() == GLFW.GLFW_PRESS) {
            if (!player.onGround() && !player.isCreative() && !player.isSpectator()) {
                player.getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY).ifPresent(data -> {
                    if (data.isCyberwareActive(ModItems.LINEAR_ACTUATORS.get())) {
                        if (!player.getPersistentData().getBoolean(NBT_DOUBLE_JUMPED)) {
                            A_PacketHandler.INSTANCE.sendToServer(new DoubleJumpPacket());
                        }
                    }
                });
            }
        }
        // 生物部位技能:↑/↓ 切换当前选中技能(仅游戏内、有两个及以上技能时生效)
        if (mc.screen == null && mobAbilities.size() >= 2 && event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getKey() == GLFW.GLFW_KEY_UP) {
                mobAbilityIndex = (mobAbilityIndex - 1 + mobAbilities.size()) % mobAbilities.size();
            } else if (event.getKey() == GLFW.GLFW_KEY_DOWN) {
                mobAbilityIndex = (mobAbilityIndex + 1) % mobAbilities.size();
            }
        }
    }

    /** 左下角生物部位技能 HUD(画在原版 HOTBAR 层之后) */
    @SubscribeEvent
    public static void onRenderMobAbilityHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mobAbilities.isEmpty()) return;
        if (mobAbilityIndex >= mobAbilities.size()) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int x = 4;
        int y = mc.getWindow().getGuiScaledHeight() - 46;

        MobPartEffects.MobAbility ability = mobAbilities.get(mobAbilityIndex);
        String keyName = KeyInit.ABILITY_KEY.getTranslatedKeyMessage().getString();
        // 冷却中显示剩余秒数并变灰
        long now = mc.player.level().getGameTime();
        Long end = MOB_ABILITY_COOLDOWNS.get(ability.id());
        long remain = (end == null) ? 0 : Math.max(0, end - now);
        int textColor = (remain > 0) ? 0x888888 : 0xFFFFFF;
        String text = "[" + keyName + "] " + Component.translatable(ability.nameKey()).getString();
        if (remain > 0) {
            text = text + String.format(" (%.1fs)", remain / 20.0);
        }

        int bgWidth = Math.max(font.width(text) + 6, 40);
        g.fill(x - 2, y - 3, x + bgWidth, y + (mobAbilities.size() > 1 ? 20 : 10), 0x77000000);
        g.drawString(font, text, x, y, textColor, true);
        if (mobAbilities.size() > 1) {
            String switchText = "↑↓ " + (mobAbilityIndex + 1) + "/" + mobAbilities.size();
            g.drawString(font, switchText, x, y + 11, 0xAAAAAA, true);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.leftArm.visible = model.leftSleeve.visible = true;
        model.rightArm.visible = model.rightSleeve.visible = true;
        model.leftLeg.visible = model.leftPants.visible = true;
        model.rightLeg.visible = model.rightPants.visible = true;
        model.head.visible = model.hat.visible = true;
        model.body.visible = model.jacket.visible = true;
        player.getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY).ifPresent(data -> {
            ItemStackHandler handler = data.getInstalledCyberware();
            // 生物部位隐藏(最高优先级,必须在 hasSkinUpgrade 提前 return 之前,
            // 否则装了合成皮肤时生物部位会一直叠加在原版身体上)
            if (hasMobPartShown(player, handler, BodyPartType.HEAD)) {
                model.head.visible = false;
                model.hat.visible = false;
            }
            if (hasMobPartShown(player, handler, BodyPartType.TORSO)) {
                model.body.visible = false;
                model.jacket.visible = false;
            }
            if (hasMobPartShown(player, handler, BodyPartType.ARM_LEFT)) {
                model.leftArm.visible = false;
                model.leftSleeve.visible = false;
            }
            if (hasMobPartShown(player, handler, BodyPartType.ARM_RIGHT)) {
                model.rightArm.visible = false;
                model.rightSleeve.visible = false;
            }
            if (hasMobPartShown(player, handler, BodyPartType.LEG_LEFT)) {
                model.leftLeg.visible = false;
                model.leftPants.visible = false;
            }
            if (hasMobPartShown(player, handler, BodyPartType.LEG_RIGHT)) {
                model.rightLeg.visible = false;
                model.rightPants.visible = false;
            }
            // 缺肢:手术后槽位为空且原生肉体已切除,原版肢体真的空了
            if (isAmputated(player, data, handler, RobosurgeonBlockEntity.SLOT_ARMS)) {
                model.leftArm.visible = false;
                model.leftSleeve.visible = false;
            }
            if (isAmputated(player, data, handler, RobosurgeonBlockEntity.SLOT_ARMS + 1)) {
                model.rightArm.visible = false;
                model.rightSleeve.visible = false;
            }
            if (isAmputated(player, data, handler, RobosurgeonBlockEntity.SLOT_LEGS)) {
                model.leftLeg.visible = false;
                model.leftPants.visible = false;
            }
            if (isAmputated(player, data, handler, RobosurgeonBlockEntity.SLOT_LEGS + 1)) {
                model.rightLeg.visible = false;
                model.rightPants.visible = false;
            }
            if (hasSkinUpgrade(data))
                return;
            if (data.hasCyberLeftArm())
                model.leftArm.visible = model.leftSleeve.visible = false;
            if (data.hasCyberRightArm())
                model.rightArm.visible = model.rightSleeve.visible = false;
            if (data.hasCyberLeftLeg())
                model.leftLeg.visible = model.leftPants.visible = false;
            if (data.hasCyberRightLeg())
                model.rightLeg.visible = model.rightPants.visible = false;
        });
    }

    /**
     * 缺肢判断:身体槽位为空且该槽位的原生肉体已被手术切除。
     * 手术台 GUI 打开时优先用投射状态,实时预览"取下部位 → 缺肢"的效果;
     * 仍要求 fleshRemoved,避免从未手术过的空槽在预览里被误判成缺肢。
     */
    private static boolean isAmputated(Player player, CyberwareUserData data, ItemStackHandler handler, int slot) {
        if (player == Minecraft.getInstance().player
                && Minecraft.getInstance().screen instanceof RobosurgeonScreen rs) {
            return rs.isProjectedSlotEmpty(slot) && data.isFleshRemoved(slot);
        }
        return handler.getStackInSlot(slot).isEmpty() && data.isFleshRemoved(slot);
    }

    /** 玩家身上是否安装了指定类型的生物部位(可能在区域内的任意槽位) */
    private static boolean hasMobPart(ItemStackHandler handler, BodyPartType part) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.getItem() instanceof MobPartItem && MobPartItem.getPart(stack) == part) {
                return true;
            }
        }
        return false;
    }

    /** 生物部位可见性:手术台 GUI 打开时用投射状态(桌面待装+虚影)实时预览,否则用已安装数据 */
    private static boolean hasMobPartShown(Player player, ItemStackHandler handler, BodyPartType part) {
        if (player == Minecraft.getInstance().player
                && Minecraft.getInstance().screen instanceof RobosurgeonScreen rs) {
            return rs.projectedMobParts().containsKey(part);
        }
        return hasMobPart(handler, part);
    }

    /** 生物部位效果 tooltip:◆ 特性/技能名 + 详细描述(存在 .desc 翻译时) */
    private static void addEffectTooltipLines(List<Component> tooltip, ResourceLocation mobId, BodyPartType part) {
        for (String key : MobPartEffects.getEffectKeys(mobId, part)) {
            tooltip.add(Component.literal("◆ ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
            if (!key.endsWith(".desc") && I18n.exists(key + ".desc")) {
                tooltip.add(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                        .append(Component.translatable(key + ".desc").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
            }
        }
    }

    private static boolean hasSkinUpgrade(CyberwareUserData data) {
        ItemStackHandler handler = data.getInstalledCyberware();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            ICyberware cw = CyberwareAPI.getCyberware(stack);
            if (cw != null && cw.getBodyPartType(stack) == BodyPartType.SKIN) {
                return true;
            }
        }
        return false;
    }
}