package com.maxwell.cyber_ware_port.client;

import com.maxwell.cyber_ware_port.CyberWare;
import com.maxwell.cyber_ware_port.api.json.CyberwareAPI;
import com.maxwell.cyber_ware_port.client.upgrades.cybereye.CyberwareMenuScreen;
import com.maxwell.cyber_ware_port.common.capability.CyberwareCapabilityProvider;
import com.maxwell.cyber_ware_port.common.capability.CyberwareUserData;
import com.maxwell.cyber_ware_port.common.block.robosurgeon.RobosurgeonBlockEntity;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.item.base.CyberwareSlotType;
import com.maxwell.cyber_ware_port.common.item.base.ICyberware;
import com.maxwell.cyber_ware_port.common.item.cyberware.MobPartItem;
import com.maxwell.cyber_ware_port.common.network.ClientPacketHandler;
import com.maxwell.cyber_ware_port.common.network.DoubleJumpPacket;
import com.maxwell.cyber_ware_port.init.ModBlocks;
import com.maxwell.cyber_ware_port.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = CyberWare.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ForgeClientEvents {
    private static final String NBT_DOUBLE_JUMPED = "cyberware_double_jumped";

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        List<Component> tooltip = event.getToolTip();
        if (item == ModBlocks.RADIO_KIT_BLOCK.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.radio_kit").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.COMPONENT_BOX.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.component_box").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.RADIO_TOWER_CORE.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.radio_tower_core").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.SCANNER.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.scanner").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.RADIO_TOWER_COMPONENT.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.radio_component").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.radio_component2").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.radio_component3").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.CHARGER.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.charger").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.charger2").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.charger3").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.SURGERY_CHAMBER.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.surgery_chamber").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.surgery_chamber2").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.BLUEPRINT_CHEST.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.blueprint_chest").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.CYBERWARE_WORKBENCH.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.cyberware_workbench").withStyle(ChatFormatting.GRAY));
        } else if (item == ModBlocks.ROBO_SURGEON.get().asItem()) {
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.robo_surgeon").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.cyber_ware_port.robo_surgeon2").withStyle(ChatFormatting.GRAY));
        }
        if (stack.getOrDefault(CyberWare.GHOST_COMPONENT.get(), false)) {
            Component name = tooltip.isEmpty() ? stack.getHoverName() : tooltip.get(0);
            tooltip.clear();
            tooltip.add(name);
            tooltip.add(Component.translatable("cyberware.tooltip.ghost.remove").withStyle(ChatFormatting.RED));
            return;
        }
        ICyberware cyberware = CyberwareAPI.getCyberware(stack);
        if (cyberware != null) {
            ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(item);
            if (registryName.getPath().contains("body_part")) return;
            if (!Screen.hasShiftDown()) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("cyberware.tooltip.shiftPrompt", Component.translatable("key.keyboard.shift")).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                return;
            }
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("cyberware.tooltip." + registryName.getPath()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.empty());
            if (cyberware.canToggle(stack)) {
                boolean isActive = cyberware.isActive(stack);
                tooltip.add(Component.translatable("cyberware.tooltip.status", Component.translatable(isActive ? "cyberware.gui.active.enable" : "cyberware.gui.active.disable").withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.RED)).withStyle(ChatFormatting.WHITE));
            }
            if (cyberware.hasEnergyProperties(stack)) {
                int consumption = cyberware.getEnergyConsumption(stack);
                if (consumption > 0)
                    tooltip.add(Component.translatable("cyberware.tooltip.powerConsumption", consumption).withStyle(ChatFormatting.RED));
                int generation = cyberware.getEnergyGeneration(stack);
                if (generation > 0)
                    tooltip.add(Component.translatable("cyberware.tooltip.powerProduction", generation).withStyle(ChatFormatting.GREEN));
                int storage = cyberware.getEnergyStorage(stack);
                if (storage > 0)
                    tooltip.add(Component.translatable("cyberware.tooltip.capacity", storage).withStyle(ChatFormatting.AQUA));
                int eventCost = cyberware.getEventConsumption(stack);
                if (eventCost > 0)
                    tooltip.add(Component.translatable("cyberware.tooltip.eventCost", eventCost).withStyle(ChatFormatting.RED));
            }
            if (cyberware.getMaxInstallAmount(stack) > 1)
                tooltip.add(Component.translatable("cyberware.tooltip.maxInstall", cyberware.getMaxInstallAmount(stack)).withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("cyberware.tooltip.essence", cyberware.getEssenceCost(stack)).withStyle(ChatFormatting.DARK_PURPLE));
            Set<Item> reqs = cyberware.getPrerequisites(stack);
            if (!reqs.isEmpty()) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("cyberware.tooltip.requires").withStyle(ChatFormatting.AQUA));
                for (Item req : reqs)
                    tooltip.add(Component.literal(" - ").append(req.getName(new ItemStack(req))).withStyle(ChatFormatting.GRAY));
            }
            Set<Item> incompatibles = cyberware.getIncompatibleItems(stack);
            if (!incompatibles.isEmpty()) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("cyberware.tooltip.incompatible").withStyle(ChatFormatting.RED));
                for (Item incompatible : incompatibles)
                    tooltip.add(Component.literal(" - ").append(incompatible.getName(new ItemStack(incompatible))).withStyle(ChatFormatting.GRAY));
            }
            CyberwareSlotType slotType = CyberwareSlotType.fromId(cyberware.getSlot(stack));
            if (slotType != null)
                tooltip.add(Component.translatable("cyberware.tooltip.slot", slotType.getDisplayName()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(cyberware.isPristine(stack) ? "cyberware.quality.manufactured" : "cyberware.quality.scavenged").withStyle(cyberware.isPristine(stack) ? ChatFormatting.AQUA : ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onClientLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPacketHandler.reset();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (KeyInit.MENU_KEY.consumeClick()) {
            CyberwareUserData data = player.getData(CyberwareCapabilityProvider.CYBERWARE_DATA.get());
            if (data.isCyberwareInstalled(ModItems.CYBER_EYE.get())) {
                if (mc.screen == null) mc.setScreen(new CyberwareMenuScreen());
            } else {
                player.displayClientMessage(Component.translatable("message.cyber_ware_port.no_hud_installed"), true);
            }
        }
        if (event.getKey() == mc.options.keyJump.getKey().getValue() && event.getAction() == GLFW.GLFW_PRESS) {
            if (!player.onGround() && !player.isCreative() && !player.isSpectator()) {
                CyberwareUserData data = player.getData(CyberwareCapabilityProvider.CYBERWARE_DATA.get());
                ItemStack actuatorStack = getInstalledStack(data, ModItems.LINEAR_ACTUATORS.get());
                if (!actuatorStack.isEmpty()) {
                    ICyberware cw = CyberwareAPI.getCyberware(actuatorStack);
                    if (cw != null && cw.isActive(actuatorStack) && !player.getPersistentData().getBoolean(NBT_DOUBLE_JUMPED)) {
                        PacketDistributor.sendToServer(new DoubleJumpPacket());
                    }
                }
            }
        }
    }

    private static ItemStack getInstalledStack(CyberwareUserData data, Item item) {
        ItemStackHandler handler = data.getInstalledCyberware();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
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
        CyberwareUserData data = player.getData(CyberwareCapabilityProvider.CYBERWARE_DATA.get());
        ItemStackHandler handler = data.getInstalledCyberware();
        // 生物部位隐藏(最高优先级,必须在 hasSkinUpgrade 提前 return 之前,
        // 否则装了合成皮肤时生物部位会一直叠加在原版身体上)
        if (hasMobPart(handler, BodyPartType.HEAD)) {
            model.head.visible = false;
            model.hat.visible = false;
        }
        if (hasMobPart(handler, BodyPartType.TORSO)) {
            model.body.visible = false;
            model.jacket.visible = false;
        }
        if (hasMobPart(handler, BodyPartType.ARM_LEFT)) {
            model.leftArm.visible = false;
            model.leftSleeve.visible = false;
        }
        if (hasMobPart(handler, BodyPartType.ARM_RIGHT)) {
            model.rightArm.visible = false;
            model.rightSleeve.visible = false;
        }
        if (hasMobPart(handler, BodyPartType.LEG_LEFT)) {
            model.leftLeg.visible = false;
            model.leftPants.visible = false;
        }
        if (hasMobPart(handler, BodyPartType.LEG_RIGHT)) {
            model.rightLeg.visible = false;
            model.rightPants.visible = false;
        }
        if (player.tickCount % 200 == 0) {
            CyberWare.LOGGER.info("[CW-DBG] Pre: skin={} mobHead={} mobTorso={} => headVis={} bodyVis={}",
                    hasSkinUpgrade(data), hasMobPart(handler, BodyPartType.HEAD), hasMobPart(handler, BodyPartType.TORSO),
                    model.head.visible, model.body.visible);
        }
        if (hasSkinUpgrade(data)) return;
        // 四肢:装了任意义体(非空)就隐藏原版肢体,生物肢体由 MobPartPlayerLayer 接管
        if (hasAnyPart(handler, BodyPartType.ARM_LEFT)) {
            model.leftArm.visible = false;
            model.leftSleeve.visible = false;
        }
        if (hasAnyPart(handler, BodyPartType.ARM_RIGHT)) {
            model.rightArm.visible = false;
            model.rightSleeve.visible = false;
        }
        if (hasAnyPart(handler, BodyPartType.LEG_LEFT)) {
            model.leftLeg.visible = false;
            model.leftPants.visible = false;
        }
        if (hasAnyPart(handler, BodyPartType.LEG_RIGHT)) {
            model.rightLeg.visible = false;
            model.rightPants.visible = false;
        }
        // 头/躯干:装着人类对应部位且没有生物部位覆盖才显示原版外观
        if (!hasHumanPart(handler, RobosurgeonBlockEntity.SLOT_HEAD)
                || hasMobPart(handler, BodyPartType.HEAD)) {
            model.head.visible = false;
            model.hat.visible = false;
        }
        if (!hasHumanPart(handler, RobosurgeonBlockEntity.SLOT_TORSO)
                || hasMobPart(handler, BodyPartType.TORSO)) {
            model.body.visible = false;
            model.jacket.visible = false;
        }
    }

    /** 指定槽位首格是否装着人类对应部位 */
    private static boolean hasHumanPart(ItemStackHandler handler, int slotStart) {
        ItemStack stack = handler.getStackInSlot(slotStart);
        Item item = stack.getItem();
        return item == ModItems.HUMAN_HEAD.get() || item == ModItems.HUMAN_TORSO.get()
                || item == ModItems.HUMAN_LEFT_ARM.get() || item == ModItems.HUMAN_RIGHT_ARM.get()
                || item == ModItems.HUMAN_LEFT_LEG.get() || item == ModItems.HUMAN_RIGHT_LEG.get();
    }

    /** 玩家身上是否装了指定身体类型的义体 */
    private static boolean hasAnyPart(ItemStackHandler handler, BodyPartType part) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof MobPartItem && MobPartItem.getPart(stack) == part) return true;
            if (part == BodyPartType.ARM_LEFT || part == BodyPartType.ARM_RIGHT
                    || part == BodyPartType.LEG_LEFT || part == BodyPartType.LEG_RIGHT) {
                // 旧的人类义体也走 hide 原版 + 由 CyberwarePlayerLayer 接管(原逻辑)
                Item item = stack.getItem();
                if (item == ModItems.HUMAN_LEFT_ARM.get() || item == ModItems.HUMAN_RIGHT_ARM.get()
                        || item == ModItems.HUMAN_LEFT_LEG.get() || item == ModItems.HUMAN_RIGHT_LEG.get()) return true;
            }
        }
        return false;
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

    private static boolean hasSkinUpgrade(CyberwareUserData data) {


        if (data.isCyberwareInstalled(ModItems.SYNTHETIC_SKIN.get())) {
            return true;
        }

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