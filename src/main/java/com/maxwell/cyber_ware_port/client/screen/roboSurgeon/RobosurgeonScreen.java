package com.maxwell.cyber_ware_port.client.screen.roboSurgeon;

import com.maxwell.cyber_ware_port.CyberWare;
import com.maxwell.cyber_ware_port.api.json.CyberwareAPI;
import com.maxwell.cyber_ware_port.common.block.robosurgeon.RobosurgeonBlockEntity;
import com.maxwell.cyber_ware_port.common.capability.CyberwareCapabilityProvider;
import com.maxwell.cyber_ware_port.common.capability.CyberwareUserData;
import com.maxwell.cyber_ware_port.common.container.RobosurgeonMenu;
import com.maxwell.cyber_ware_port.common.effect.MobPartEffects;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.item.base.ICyberware;
import com.maxwell.cyber_ware_port.common.item.cyberware.MobPartItem;
import com.maxwell.cyber_ware_port.common.network.A_PacketHandler;
import com.maxwell.cyber_ware_port.common.network.SurgeryGhostTogglePacket;
import com.maxwell.cyber_ware_port.common.risk.SurgeryAlert;
import com.maxwell.cyber_ware_port.common.risk.SurgeryAnalyzer;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 机械手术核心 GUI(重制版):
 * - 左上:部位清单(只读,点击进入该部位的插槽详细界面才能安装/移除)
 * - 右上:术后预览(玩家当前外观 + 桌面待装部位实时替换)
 * - 右下:技能与特性列表(随桌面部位变化实时刷新,滚轮滚动)
 * - 下方:玩家物品栏
 */
public class RobosurgeonScreen extends AbstractContainerScreen<RobosurgeonMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(CyberWare.MODID, "textures/gui/surgery.png");
    private static final ResourceLocation ALERT_ICON = new ResourceLocation(CyberWare.MODID, "textures/gui/risk_icons.png");
    private static final int GUI_WIDTH = 280;
    private static final int TOP_HEIGHT = 131;
    private static final int BOTTOM_HEIGHT = 91;
    private static final int TEXTURE_INVENTORY_START_Y = 131;
    /** 旧贴图面板宽度(175),右侧条带用纯色绘制 */
    private static final int TEXTURE_PANEL_WIDTH = 175;
    /** 左上部位面板(相对 GUI 左上角) */
    private static final int PANEL_X = 15, PANEL_Y = 14, PANEL_W = 155, PANEL_H = 112;
    /** 右上术后预览面板 */
    private static final int PREVIEW_X = 177, PREVIEW_Y = 2, PREVIEW_W = 101, PREVIEW_H = 94;
    private static final float PREVIEW_SCALE = 38f;
    /** 右下技能与特性面板 */
    private static final int EFFECTS_X = 177, EFFECTS_Y = 98, EFFECTS_W = 101, EFFECTS_H = 122;
    /** 每行效果文本高度 */
    private static final int LINE_HEIGHT = 9;

    /** 左上部位清单条目:区域起始槽位 + 名称翻译 key */
    private record OrganEntry(int start, String nameKey) {}

    private static final OrganEntry[] INTERNAL_ORGANS = {
            new OrganEntry(RobosurgeonBlockEntity.SLOT_EYES, "eyes"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_BRAIN, "brain"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_HEART, "heart"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_LUNGS, "lungs"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_STOMACH, "stomach"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_SKIN, "skin"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_MUSCLE, "muscle"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_BONES, "bones")
    };

    private static final OrganEntry[] EXTERNAL_ORGANS = {
            new OrganEntry(RobosurgeonBlockEntity.SLOT_HEAD, "head"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_TORSO, "torso"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_ARMS, "arms"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_HANDS, "hands"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_LEGS, "legs"),
            new OrganEntry(RobosurgeonBlockEntity.SLOT_BOOTS, "boots")
    };

    /** false=内部器官模式(默认),true=外部表面部位模式(头/躯干/四肢) */
    private boolean externalMode = false;
    /** 当前打开的部位详细界面区域起始槽位,-1 表示部位清单 */
    private int expandedOrgan = -1;
    private boolean potentialDrag = false;
    private boolean isDraggingModel = false;
    private float viewRotation = 0f;
    private double dragStartX = 0;
    private float rotationStart = 0f;
    /** 右下效果列表滚动偏移 */
    private float effectsScroll = 0f;

    public RobosurgeonScreen(RobosurgeonMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = TOP_HEIGHT + BOTTOM_HEIGHT;
        this.inventoryLabelY = 133;
        this.titleLabelY = 6;
    }

    // ==================== 初始化 ====================

    @Override
    protected void init() {
        super.init();
        // 预览面板右上角:查询按钮(放大镜,放玩家名左侧) + 内/外模式切换按钮(上下箭头) + 已装列表按钮
        int btnY = this.topPos + 3;
        this.addRenderableWidget(makeIconWidget(this.leftPos + GUI_WIDTH - 12, btnY, ICON_TEXTURE,
                "gui.cyberware_unofficial.button.view_installed", () ->
                        Minecraft.getInstance().setScreen(new InstalledCyberwareScreen(RobosurgeonScreen.this))));
        this.addRenderableWidget(makeIconWidget(this.leftPos + GUI_WIDTH - 24, btnY, ICON_ARROWS,
                "gui.cyberware_unofficial.button.toggle_parts", () -> {
                    RobosurgeonScreen.this.externalMode = !RobosurgeonScreen.this.externalMode;
                    RobosurgeonScreen.this.expandedOrgan = -1;
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }));
        // 放大镜按钮:沿用原定位(按预览名宽度计算),预览区文本已不再渲染
        int queryX = this.leftPos + GUI_WIDTH - 36;
        if (this.minecraft != null && this.minecraft.player != null) {
            String name = "_" + this.minecraft.player.getName().getString().toUpperCase();
            int nameX = this.leftPos + PREVIEW_X + PREVIEW_W / 2 - this.font.width(name) / 2;
            queryX = Math.min(nameX - 12, this.leftPos + GUI_WIDTH - 48);
        }
        this.addRenderableWidget(makeIconWidget(queryX, btnY, ICON_MAGNIFIER, "gui.cyberware_unofficial.button.query",
                () -> Minecraft.getInstance().setScreen(new MobQueryScreen())));
    }

    /** 图标绘制类型:贴图 / 放大镜 / 上下箭头 */
    private static final int ICON_TEXTURE = 0, ICON_MAGNIFIER = 1, ICON_ARROWS = 2;

    private AbstractWidget makeIconWidget(int x, int y, int iconType, String tooltipKey, Runnable onClick) {
        return new AbstractWidget(x, y, 10, 10, Component.empty()) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                switch (iconType) {
                    case ICON_MAGNIFIER -> drawMagnifierIcon(guiGraphics, this.getX(), this.getY());
                    case ICON_ARROWS -> drawArrowsIcon(guiGraphics, this.getX(), this.getY());
                    default -> guiGraphics.blit(TEXTURE, this.getX(), this.getY(), 176, 122, 10, 10, 256, 256);
                }
                if (this.isHovered()) {
                    guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,
                            0x50FFFFFF);
                    guiGraphics.renderTooltip(font, Component.translatable(tooltipKey), mouseX, mouseY);
                }
            }

            @Override
            public void onClick(double mouseX, double mouseY) {
                onClick.run();
            }

            @Override
            protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
                this.defaultButtonNarrationText(narrationElementOutput);
            }
        };
    }

    /** 放大镜图标:程序化像素绘制(青色),镜框 + 右下斜手柄 */
    private void drawMagnifierIcon(GuiGraphics g, int x, int y) {
        int c = 0xFF00FFFF;
        g.fill(x + 2, y + 1, x + 6, y + 2, c);   // 镜框上边
        g.fill(x + 1, y + 2, x + 2, y + 5, c);   // 镜框左边
        g.fill(x + 6, y + 2, x + 7, y + 5, c);   // 镜框右边
        g.fill(x + 2, y + 5, x + 6, y + 6, c);   // 镜框下边
        g.fill(x + 6, y + 5, x + 7, y + 6, c);   // 右下角连接
        g.fill(x + 7, y + 6, x + 8, y + 7, c);   // 手柄(斜线)
        g.fill(x + 8, y + 7, x + 9, y + 8, c);
        g.fill(x + 9, y + 8, x + 10, y + 9, c);
    }

    /** 上下双箭头图标:上箭头尖端朝上,下箭头尖端朝下,表示内/外模式切换 */
    private void drawArrowsIcon(GuiGraphics g, int x, int y) {
        int c = 0xFF00FFFF;
        g.fill(x + 4, y + 1, x + 6, y + 2, c);   // 上箭头尖端
        g.fill(x + 3, y + 2, x + 7, y + 3, c);   // 上箭头箭身
        g.fill(x + 4, y + 3, x + 6, y + 5, c);   // 上箭头杆
        g.fill(x + 4, y + 5, x + 6, y + 7, c);   // 下箭头杆
        g.fill(x + 3, y + 7, x + 7, y + 8, c);   // 下箭头箭身
        g.fill(x + 4, y + 8, x + 6, y + 9, c);   // 下箭头尖端
    }

    // ==================== 槽位布局 ====================

    /** 主界面(清单模式)下所有手术台槽位都摆到屏幕外,禁止直接操作 */
    private void updateSlotPositions() {
        for (int i = 0; i < RobosurgeonBlockEntity.TOTAL_SLOTS; i++) {
            Slot slot = this.menu.slots.get(i);
            slot.x = 20000;
            slot.y = 20000;
        }
        if (this.expandedOrgan >= 0) {
            int n = displayCountFor(this.expandedOrgan);
            List<Integer> visible = visibleSlotsFor(this.expandedOrgan, n);
            for (int k = 0; k < visible.size(); k++) {
                int fx, fy;
                if (useGridLayout(this.expandedOrgan)) {
                    fx = PANEL_X + (PANEL_W - 58) / 2 + (k % 3) * 20;
                    fy = PANEL_Y + 16 + (k / 3) * 20;
                } else {
                    fx = PANEL_X + (PANEL_W - (n * 20 - 2)) / 2 + k * 20;
                    fy = PANEL_Y + 20;
                }
                Slot slot = this.menu.slots.get(visible.get(k));
                slot.x = fx + 1;
                slot.y = fy + 1;
            }
        }
    }

    private static boolean isInternalRegion(int start) {
        return start < RobosurgeonBlockEntity.SLOT_ARMS;
    }

    /** 详细界面显示的槽位数:内部器官 3x3 全显,外部部位 头/躯干 1 格、臂/腿 2 格、手/脚 3x3 共 9 格 */
    private static int displayCountFor(int start) {
        if (isInternalRegion(start)) return RobosurgeonBlockEntity.SLOTS_PER_PART;
        if (start == RobosurgeonBlockEntity.SLOT_HANDS || start == RobosurgeonBlockEntity.SLOT_BOOTS) {
            return RobosurgeonBlockEntity.SLOTS_PER_PART;
        }
        return (start == RobosurgeonBlockEntity.SLOT_HEAD || start == RobosurgeonBlockEntity.SLOT_TORSO) ? 1 : 2;
    }

    /** 手部/脚部与内部器官一样用 3x3 网格布局显示 9 格 */
    private static boolean useGridLayout(int start) {
        return isInternalRegion(start)
                || start == RobosurgeonBlockEntity.SLOT_HANDS
                || start == RobosurgeonBlockEntity.SLOT_BOOTS;
    }

    /**
     * 详细界面显示的槽位索引:区域内的真实物品优先,其次虚影,最后空槽,升序排列。
     * 这样旧数据里不在区域首格的物品也能显示出来。
     */
    private List<Integer> visibleSlotsFor(int start, int n) {
        List<Integer> result = new ArrayList<>();
        for (int i = start; i < start + RobosurgeonBlockEntity.SLOTS_PER_PART; i++) {
            ItemStack s = this.menu.getSlot(i).getItem();
            if (!s.isEmpty()) result.add(i);
        }
        for (int i = start; i < start + RobosurgeonBlockEntity.SLOTS_PER_PART && result.size() < n; i++) {
            if (this.menu.getSlot(i).getItem().isEmpty()) result.add(i);
        }
        result.sort(null);
        return result.size() > n ? result.subList(0, n) : result;
    }

    // ==================== 投射状态(桌面待装 + 虚影保留) ====================

    /** 虚影判定(1.20.1 用 NBT 标记,无数据组件) */
    private static boolean isGhost(ItemStack stack) {
        return stack.hasTag() && stack.getTag() != null && stack.getTag().getBoolean("cyberware_ghost");
    }

    /** 槽位 i 手术后的最终状态:虚影 = 保留已安装,否则为桌面物品 */
    private ItemStack projectedStack(int i) {
        ItemStack table = this.menu.getSlot(i).getItem();
        if (!table.isEmpty() && isGhost(table) && this.minecraft.player != null) {
            CyberwareUserData data = this.minecraft.player
                    .getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY).orElse(null);
            if (data != null) {
                return data.getInstalledCyberware().getStackInSlot(i);
            }
        }
        return table;
    }

    /** 投射后的外部生物部位:部位 -> 生物 id(每个部位取找到的第一个) */
    public Map<BodyPartType, ResourceLocation> projectedMobParts() {
        Map<BodyPartType, ResourceLocation> map = new EnumMap<>(BodyPartType.class);
        for (int i = 0; i < RobosurgeonBlockEntity.TOTAL_SLOTS; i++) {
            ItemStack stack = projectedStack(i);
            if (!(stack.getItem() instanceof MobPartItem)) continue;
            // 臂/腿区域按槽位决定左右侧(+0=左、+1=右),与实际渲染保持一致
            BodyPartType part = MobPartItem.sidedPartForSlot(i, MobPartItem.getPart(stack));
            if (!map.containsKey(part)) {
                map.put(part, MobPartItem.getMobId(stack));
            }
        }
        return map;
    }

    /** 预览中该槽位手术后是否为空(缺肢):手术台上非虚影且为空,即该部位将被移除 */
    public boolean isProjectedSlotEmpty(int slot) {
        return projectedStack(slot).isEmpty();
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        updateSlotPositions();
        // 手上有物品时交给标准容器逻辑(放入详情槽位/放回物品栏)
        if (!this.menu.getCarried().isEmpty()) {
            return super.mouseClicked(pMouseX, pMouseY, pButton);
        }
        if (pButton == 0) {
            if (this.expandedOrgan >= 0) {
                // 详细界面:点击 "<" 返回部位清单
                int backX = this.leftPos + PANEL_X + 4;
                int backY = this.topPos + PANEL_Y + 3;
                if (pMouseX >= backX && pMouseX < backX + 12 && pMouseY >= backY - 1 && pMouseY < backY + 10) {
                    this.expandedOrgan = -1;
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8F));
                    return true;
                }
            } else {
                // 清单模式:点击部位名称进入该部位的详细界面
                OrganEntry[] organs = currentOrgans();
                for (int idx = 0; idx < organs.length; idx++) {
                    int cx = this.leftPos + organCellX(idx);
                    int cy = this.topPos + organCellY(idx);
                    if (pMouseX >= cx && pMouseX < cx + PANEL_W / 2 - 4 && pMouseY >= cy && pMouseY < cy + 23) {
                        this.expandedOrgan = organs[idx].start();
                        this.effectsScroll = 0f;
                        Minecraft.getInstance().getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }
            }
            // 详细界面内:虚影槽点击 = 取消保留;空槽点击 = 建议保留已安装部位
            Slot hoveredSlot = null;
            for (Slot slot : this.menu.slots) {
                if (slot.x > 10000 || slot.y > 10000) continue;
                int slotLeft = this.leftPos + slot.x;
                int slotTop = this.topPos + slot.y;
                if (pMouseX >= slotLeft && pMouseX < slotLeft + 16 && pMouseY >= slotTop && pMouseY < slotTop + 16) {
                    hoveredSlot = slot;
                    break;
                }
            }
            if (hoveredSlot != null && hoveredSlot.index < RobosurgeonBlockEntity.TOTAL_SLOTS) {
                if (hoveredSlot.hasItem() && isGhost(hoveredSlot.getItem())) {
                    A_PacketHandler.INSTANCE.sendToServer(new SurgeryGhostTogglePacket(
                            this.menu.blockEntity.getBlockPos(), hoveredSlot.index));
                    hoveredSlot.set(ItemStack.EMPTY);
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                } else if (!hoveredSlot.hasItem()) {
                    A_PacketHandler.INSTANCE.sendToServer(new SurgeryGhostTogglePacket(
                            this.menu.blockEntity.getBlockPos(), hoveredSlot.index));
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
            // 右上预览区域支持拖拽旋转
            if (inRect(pMouseX, pMouseY, this.leftPos + PREVIEW_X, this.topPos + PREVIEW_Y, PREVIEW_W, PREVIEW_H)) {
                this.potentialDrag = true;
                this.dragStartX = pMouseX;
                super.mouseClicked(pMouseX, pMouseY, pButton);
                return true;
            }
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        if (this.potentialDrag && pButton == 0) {
            if (!this.isDraggingModel) {
                this.isDraggingModel = true;
                this.rotationStart = this.viewRotation;
            }
            this.viewRotation = this.rotationStart + (float) (pMouseX - this.dragStartX);
            return true;
        }
        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0 && (this.isDraggingModel || this.potentialDrag)) {
            this.isDraggingModel = false;
            this.potentialDrag = false;
            return true;
        }
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        if (inRect(pMouseX, pMouseY, this.leftPos + EFFECTS_X, this.topPos + EFFECTS_Y, EFFECTS_W, EFFECTS_H)) {
            this.effectsScroll = Math.max(0f, this.effectsScroll - (float) pDelta * LINE_HEIGHT);
            return true;
        }
        return super.mouseScrolled(pMouseX, pMouseY, pDelta);
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private OrganEntry[] currentOrgans() {
        return this.externalMode ? EXTERNAL_ORGANS : INTERNAL_ORGANS;
    }

    private static int organCellX(int idx) {
        return PANEL_X + 3 + (idx % 2) * (PANEL_W / 2);
    }

    private static int organCellY(int idx) {
        return PANEL_Y + 13 + (idx / 2) * 24;
    }

    private static Component organName(int startSlot) {
        for (OrganEntry e : INTERNAL_ORGANS) {
            if (e.start() == startSlot) return Component.translatable("cyberware_slot.cyberware_unofficial." + e.nameKey());
        }
        for (OrganEntry e : EXTERNAL_ORGANS) {
            if (e.start() == startSlot) return Component.translatable("cyberware_slot.cyberware_unofficial." + e.nameKey());
        }
        return Component.literal("?");
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        updateSlotPositions();
        // 1.20.1 的容器界面不会自动画全屏遮罩(1.21.1 的 renderBackground 自带变暗+模糊),
        // 这里显式补原版等价的全屏渐变遮罩,否则世界背景无变暗
        pGuiGraphics.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        int maxTolerance = 100;
        if (this.minecraft != null && this.minecraft.player != null) {
            CyberwareUserData data = this.minecraft.player
                    .getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY).orElse(null);
            if (data != null) {
                maxTolerance = data.getMaxTolerance(this.minecraft.player);
            }
        }
        SurgeryAlert alert = SurgeryAnalyzer.check(this.menu.slots, maxTolerance);
        if (alert != null) {
            int iconX = this.leftPos + PANEL_X + PANEL_W - 17;
            int iconY = this.topPos + PANEL_Y + 1;
            pGuiGraphics.blit(ALERT_ICON, iconX, iconY, 0, 0, 16, 16, 16, 16);
            if (pMouseX >= iconX && pMouseX < iconX + 16 && pMouseY >= iconY && pMouseY < iconY + 16) {
                pGuiGraphics.renderTooltip(this.font, alert.message(), pMouseX, pMouseY);
            }
        }
        // 悬停高亮:返回按钮 / 部位清单行
        RenderSystem.enableBlend();
        if (this.expandedOrgan >= 0) {
            int backX = this.leftPos + PANEL_X + 4;
            int backY = this.topPos + PANEL_Y + 3;
            if (pMouseX >= backX && pMouseX < backX + 12 && pMouseY >= backY - 1 && pMouseY < backY + 10) {
                pGuiGraphics.fill(backX - 1, backY - 1, backX + 11, backY + 9, 0x40FFFFFF);
            }
        } else {
            OrganEntry[] organs = currentOrgans();
            for (int idx = 0; idx < organs.length; idx++) {
                int cx = this.leftPos + organCellX(idx);
                int cy = this.topPos + organCellY(idx);
                if (pMouseX >= cx && pMouseX < cx + PANEL_W / 2 - 4 && pMouseY >= cy && pMouseY < cy + 23) {
                    pGuiGraphics.fill(cx - 2, cy - 2, cx + PANEL_W / 2 - 4, cy + 21, 0x30FFFFFF);
                }
            }
        }
        RenderSystem.disableBlend();
        // ghost 槽位 tooltip:改用玩家身上真实安装的堆栈组装(ghost 副本个别链路可能丢 NBT,
        // 生物部位的 MobId/PartType 一旦丢失,特性/技能/插槽信息全为空),再补"点击移除"行;
        // 非 ghost 槽位走原版 tooltip
        Slot hoveredSlot = this.hoveredSlot;
        boolean ghostHandled = false;
        if (hoveredSlot != null && hoveredSlot.index < RobosurgeonBlockEntity.TOTAL_SLOTS
                && hoveredSlot.hasItem() && isGhost(hoveredSlot.getItem())
                && this.minecraft != null && this.minecraft.player != null) {
            ItemStack installed = this.minecraft.player
                    .getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY)
                    .map(data -> data.getInstalledCyberware().getStackInSlot(hoveredSlot.index))
                    .orElse(ItemStack.EMPTY);
            if (!installed.isEmpty()) {
                List<Component> lines = new ArrayList<>(getTooltipFromItem(this.minecraft, installed));
                lines.add(1, Component.translatable("cyberware.tooltip.ghost.remove").withStyle(ChatFormatting.RED));
                pGuiGraphics.renderTooltip(this.font, lines, Optional.empty(), pMouseX, pMouseY);
                ghostHandled = true;
            }
        }
        if (!ghostHandled) {
            this.renderTooltip(pGuiGraphics, pMouseX, pMouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // 主体沿用旧贴图,右侧条带(预览+效果)用纯色绘制
        g.blit(TEXTURE, x, y, 0, 0, TEXTURE_PANEL_WIDTH, TOP_HEIGHT);
        g.blit(TEXTURE, x, y + TOP_HEIGHT, 0, TEXTURE_INVENTORY_START_Y, TEXTURE_PANEL_WIDTH, BOTTOM_HEIGHT);
        g.fill(x + TEXTURE_PANEL_WIDTH, y, x + GUI_WIDTH, y + imageHeight, 0xFF101418);
        g.fill(x, y + TOP_HEIGHT - 1, x + GUI_WIDTH, y + TOP_HEIGHT, 0xFF3A3A3A);
        g.fill(x + TEXTURE_PANEL_WIDTH, y, x + TEXTURE_PANEL_WIDTH + 1, y + imageHeight, 0xFF3A3A3A);
        g.renderOutline(x + PREVIEW_X, y + PREVIEW_Y, PREVIEW_W, PREVIEW_H, 0xFF3A3A3A);
        g.renderOutline(x + EFFECTS_X, y + EFFECTS_Y, EFFECTS_W, EFFECTS_H, 0xFF3A3A3A);
        drawOrganPanel(g, x, y);
        drawEssenceBar(g, x, y);
        drawPreview(g, x, y);
        drawEffectsPanel(g, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        this.minecraft.player.getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY).ifPresent(data -> {
            int maxTolerance = data.getMaxTolerance(this.minecraft.player);
            int currentCost = 0;
            for (int i = 0; i < RobosurgeonBlockEntity.TOTAL_SLOTS; i++) {
                ItemStack stack = this.menu.getSlot(i).getItem();
                ICyberware cw = CyberwareAPI.getCyberware(stack);
                if (cw != null) currentCost += cw.getEssenceCost(stack) * stack.getCount();
            }
            int remaining = maxTolerance - currentCost;
            pGuiGraphics.drawString(this.font, remaining + " / " + maxTolerance, 18, 4,
                    (remaining < 0) ? 0xAA0000 : (remaining < 25 ? 0xFF5555 : 0x00FFFF), true);
        });
    }

    /** 左上面板:部位清单(名称+状态条,点击进入详情)或插槽详细界面 */
    private void drawOrganPanel(GuiGraphics g, int x, int y) {
        int px = x + PANEL_X, py = y + PANEL_Y;
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xE80D1012);
        g.renderOutline(px, py, PANEL_W, PANEL_H, 0xFF2E6E6E);
        if (this.expandedOrgan >= 0) {
            g.drawString(this.font, "<", px + 4, py + 3, 0xFF3FBFBF, true);
            g.drawString(this.font, organName(this.expandedOrgan), px + 18, py + 3, 0xFF3FBFBF, true);
            int n = displayCountFor(this.expandedOrgan);
            List<Integer> visible = visibleSlotsFor(this.expandedOrgan, n);
            for (int k = 0; k < visible.size(); k++) {
                int fx, fy;
                if (useGridLayout(this.expandedOrgan)) {
                    fx = px + (PANEL_W - 58) / 2 + (k % 3) * 20;
                    fy = py + 16 + (k / 3) * 20;
                } else {
                    fx = px + (PANEL_W - (n * 20 - 2)) / 2 + k * 20;
                    fy = py + 20;
                }
                drawSlotFrame(g, fx, fy);
            }
            if (!isInternalRegion(this.expandedOrgan)) {
                // 手/脚 3x3 网格占到 y+76,提示文字下移避免重叠
                int hintY = useGridLayout(this.expandedOrgan) ? py + 80 : py + 48;
                drawWrapped(g, Component.translatable("gui.cyberware_unofficial.surgery.hint"),
                        px + 5, hintY, PANEL_W - 10, 0xFF8A8A8A);
            }
        } else {
            g.drawCenteredString(this.font, Component.translatable("gui.cyberware_unofficial.panel.parts"),
                    px + PANEL_W / 2, py + 3, 0xFF3FBFBF);
            OrganEntry[] organs = currentOrgans();
            for (int idx = 0; idx < organs.length; idx++) {
                int cx = x + organCellX(idx);
                int cy = y + organCellY(idx);
                g.drawString(this.font, organName(organs[idx].start()), cx, cy, 0xFF9ADDDD, true);
                // 状态条:绿=桌面待装,青=虚影保留,暗=空
                int status = organStatus(organs[idx].start());
                int chipW = 30, chipX = cx + (PANEL_W / 2 - chipW) / 2, chipY = cy + 12;
                g.fill(chipX, chipY, chipX + chipW, chipY + 4, status);
                g.renderOutline(chipX - 1, chipY - 1, chipW + 2, 6, 0xFF2E6E6E);
            }
        }
    }

    /** 区域状态颜色:绿=有真实物品,青=有虚影,暗=空 */
    private int organStatus(int start) {
        boolean hasGhost = false;
        for (int i = start; i < start + RobosurgeonBlockEntity.SLOTS_PER_PART; i++) {
            ItemStack s = this.menu.getSlot(i).getItem();
            if (s.isEmpty()) continue;
            if (isGhost(s)) hasGhost = true;
            else return 0xFF44DD44;
        }
        return hasGhost ? 0xFF2ED9D9 : 0xFF232B30;
    }

    private void drawSlotFrame(GuiGraphics g, int fx, int fy) {
        g.fill(fx, fy, fx + 18, fy + 18, 0xFF0A0C0E);
        g.renderOutline(fx, fy, 18, 18, 0xFF2E6E6E);
    }

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int color) {
        int dy = y;
        for (FormattedCharSequence line : this.font.split(text, width)) {
            g.drawString(this.font, line, x, dy, color, false);
            dy += LINE_HEIGHT;
        }
    }

    // ==================== 右上:术后预览 ====================

    /** 原版 Tab 头像同款的实体渲染:走完整实体管线,MobPartPlayerLayer 自动生效 */
    private static void renderEntityWithRotation(GuiGraphics g, int x, int y, int scale, float rotationYaw,
            LivingEntity entity) {
        g.pose().pushPose();
        g.pose().translate((float) x, (float) y, 50.0F);
        g.pose().mulPoseMatrix((new Matrix4f()).scaling((float) scale, (float) scale, (float) (-scale)));
        Quaternionf quaternionf = Axis.ZP.rotationDegrees(180.0F);
        Quaternionf rotation = Axis.YP.rotationDegrees(rotationYaw + 180.0F);
        quaternionf.mul(rotation);
        g.pose().mulPose(quaternionf);
        float yBodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float yHeadRotO = entity.yHeadRotO;
        float yHeadRot = entity.yHeadRot;
        entity.yBodyRot = 0.0F;
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
        entity.yHeadRotO = 0.0F;
        entity.yHeadRot = 0.0F;
        float speed = entity.walkAnimation.speed();
        entity.walkAnimation.setSpeed(0.0f);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotation.conjugate();
        dispatcher.overrideCameraOrientation(rotation);
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, g.pose(),
                g.bufferSource(), 15728880));
        g.flush();
        dispatcher.setRenderShadow(true);
        entity.yBodyRot = yBodyRot;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRotO = yHeadRotO;
        entity.yHeadRot = yHeadRot;
        entity.walkAnimation.setSpeed(speed);
        g.pose().popPose();
        Lighting.setupFor3DItems();
    }

    private void drawPreview(GuiGraphics g, int x, int y) {
        // 1.20.1 中 minecraft.player 本身就是 LocalPlayer(AbstractClientPlayer 子类),无需 instanceof 判定
        if (this.minecraft.player == null) return;
        AbstractClientPlayer player = (AbstractClientPlayer) this.minecraft.player;
        int cx = x + PREVIEW_X + PREVIEW_W / 2;
        int feetY = y + PREVIEW_Y + PREVIEW_H - 3;
        double guiScale = this.minecraft.getWindow().getGuiScale();
        RenderSystem.enableScissor((int) ((x + PREVIEW_X + 1) * guiScale),
                (int) ((this.height - (y + PREVIEW_Y + PREVIEW_H - 1)) * guiScale),
                (int) ((PREVIEW_W - 2) * guiScale), (int) ((PREVIEW_H - 2) * guiScale));
        // 直接走实体渲染管线(原版 Tab 头像同款):与第三人称渲染完全一致,
        // MobPartPlayerLayer 的部位渲染与原版部件隐藏自动生效,不再手动拼矩阵
        renderEntityWithRotation(g, cx, feetY, (int) PREVIEW_SCALE, this.viewRotation, player);
        RenderSystem.disableScissor();
    }

    // ==================== 右下:技能与特性 ====================

    /** key = 技能/特性翻译 key(用于悬浮 tooltip),包装行也带 key,"暂无"行 为 null */
    private record EffectLine(FormattedCharSequence text, int color, String key) {}

    /** 汇总投射状态下所有生物部位的技能与特性翻译 key(去重保序) */
    private void collectEffectKeys(List<String> traits, List<String> abilities) {
        Set<String> traitSet = new LinkedHashSet<>(), abilitySet = new LinkedHashSet<>();
        for (int i = 0; i < RobosurgeonBlockEntity.TOTAL_SLOTS; i++) {
            ItemStack stack = projectedStack(i);
            if (!(stack.getItem() instanceof MobPartItem)) continue;
            for (String key : MobPartEffects.getEffectKeys(MobPartItem.getMobId(stack), MobPartItem.getPart(stack))) {
                if (key.startsWith("ability.") && key.endsWith(".desc")) {
                    abilitySet.add(key.substring(0, key.length() - 5));
                } else if (key.startsWith("trait.")) {
                    traitSet.add(key);
                }
            }
        }
        traits.addAll(traitSet);
        abilities.addAll(abilitySet);
    }

    private List<EffectLine> buildEffectLines(int width) {
        List<EffectLine> out = new ArrayList<>();
        List<String> traits = new ArrayList<>(), abilities = new ArrayList<>();
        collectEffectKeys(traits, abilities);
        if (traits.isEmpty() && abilities.isEmpty()) {
            for (FormattedCharSequence line : this.font.split(Component.translatable("gui.cyberware_unofficial.effects.none"),
                    width)) {
                out.add(new EffectLine(line, 0xFF6A7A80, null));
            }
            return out;
        }
        for (String key : traits) {
            for (FormattedCharSequence line : this.font.split(Component.translatable(key), width)) {
                out.add(new EffectLine(line, 0xFF7FDBFF, key));
            }
        }
        for (String key : abilities) {
            for (FormattedCharSequence line : this.font.split(Component.translatable(key), width)) {
                out.add(new EffectLine(line, 0xFFFFD75A, key));
            }
        }
        return out;
    }

    private void drawEffectsPanel(GuiGraphics g, int x, int y) {
        int px = x + EFFECTS_X, py = y + EFFECTS_Y;
        g.drawString(this.font, Component.translatable("gui.cyberware_unofficial.panel.effects"), px + 3, py + 3,
                0xFF3FBFBF, true);
        List<EffectLine> lines = buildEffectLines(EFFECTS_W - 8);
        int viewH = EFFECTS_H - 16;
        int contentH = lines.size() * LINE_HEIGHT;
        int maxScroll = Math.max(0, contentH - viewH);
        this.effectsScroll = Math.min(this.effectsScroll, maxScroll);
        double guiScale = this.minecraft.getWindow().getGuiScale();
        RenderSystem.enableScissor((int) ((px + 1) * guiScale), (int) ((this.height - (py + 13 + viewH)) * guiScale),
                (int) ((EFFECTS_W - 2) * guiScale), (int) (viewH * guiScale));
        int dy = py + 14 - (int) this.effectsScroll;
        for (EffectLine line : lines) {
            if (dy > py + EFFECTS_H) break;
            if (dy + LINE_HEIGHT > py + 13) {
                g.drawString(this.font, line.text(), px + 4, dy, line.color(), false);
            }
            dy += LINE_HEIGHT;
        }
        RenderSystem.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(8, viewH * viewH / contentH);
            int barY = py + 13 + (int) (((float) this.effectsScroll / maxScroll) * (viewH - barH));
            g.fill(px + EFFECTS_W - 4, barY, px + EFFECTS_W - 2, barY + barH, 0xFF2E6E6E);
        }
        // 悬浮在某条技能/特性上时显示描述 tooltip
        double mwx = this.minecraft.mouseHandler.xpos() / guiScale;
        double mwy = this.minecraft.mouseHandler.ypos() / guiScale;
        if (mwx >= px + 4 && mwx < px + EFFECTS_W - 4 && mwy >= py + 13 && mwy < py + 13 + viewH && !lines.isEmpty()) {
            int idx = (int) ((mwy - (py + 14) + this.effectsScroll) / LINE_HEIGHT);
            if (idx >= 0 && idx < lines.size()) {
                String key = lines.get(idx).key();
                if (key != null) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(Component.translatable(key));
                    tip.add(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY));
                    g.renderComponentTooltip(this.font, tip, (int) mwx, (int) mwy + 10);
                }
            }
        }
    }

    // ==================== 容量条 ====================

    private void drawEssenceBar(GuiGraphics g, int x, int y) {
        int maxEssence = 100, currentEssence = 0;
        CyberwareUserData data = null;
        if (this.minecraft.player != null) {
            data = this.minecraft.player.getCapability(CyberwareCapabilityProvider.CYBERWARE_CAPABILITY).orElse(null);
            if (data != null) {
                maxEssence = data.getMaxTolerance(this.minecraft.player);
                currentEssence = data.getTolerance(this.minecraft.player);
            }
        }
        int barX = x + 5, barY = y + 4, barW = 8, barH = 48;
        g.blit(TEXTURE, barX, barY, 211.0F, 61.0F, barW, barH, 256, 256);
        if (data == null) return;
        int futureEssence = getProjectedFutureEssence(data);
        if (futureEssence < currentEssence) {
            float flash = 0.5f + 0.3f * (float) Math.sin((System.currentTimeMillis() % 1000) / 1000f * 2 * Math.PI);
            g.setColor(1.0f, 0.6f, 0.0f, flash);
            drawEssenceBar(g, currentEssence, maxEssence, barX, barY, barW, barH);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            drawEssenceBar(g, futureEssence, maxEssence, barX, barY, barW, barH);
        } else if (futureEssence > currentEssence) {
            float flash = 0.5f + 0.3f * (float) Math.sin((System.currentTimeMillis() % 1000) / 1000f * 2 * Math.PI);
            g.setColor(0.2f, 1.0f, 1.0f, flash);
            drawEssenceBar(g, futureEssence, maxEssence, barX, barY, barW, barH);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            drawEssenceBar(g, currentEssence, maxEssence, barX, barY, barW, barH);
        } else {
            drawEssenceBar(g, currentEssence, maxEssence, barX, barY, barW, barH);
        }
    }

    private void drawEssenceBar(GuiGraphics g, int essence, int maxEssence, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int danger = (int) (maxEssence * 0.25f);
        int rH = (int) (h * ((float) Math.min(Math.max(0, essence), danger) / maxEssence));
        int bH = (int) (h * ((float) Math.max(0, essence - danger) / maxEssence));
        if (rH > 0) {
            g.blit(TEXTURE, x, y + (h - rH), w, rH, 220, 61 + (48 - rH), w, rH, 256, 256);
        }
        if (bH > 0) {
            g.blit(TEXTURE, x, y + (h - rH - bH), w, bH, 176, 61 + (48 - (rH + bH)), w, bH, 256, 256);
        }
        RenderSystem.disableBlend();
    }

    private int getProjectedFutureEssence(CyberwareUserData data) {
        if (this.minecraft == null || this.minecraft.player == null) return 100;
        int futureCost = 0;
        for (int i = 0; i < RobosurgeonBlockEntity.TOTAL_SLOTS; i++) {
            ItemStack finalStack = projectedStack(i);
            ICyberware cw = CyberwareAPI.getCyberware(finalStack);
            if (cw != null) {
                futureCost += cw.getEssenceCost(finalStack) * finalStack.getCount();
            }
        }
        int maxTolerance = data.getMaxTolerance(this.minecraft.player);
        return Math.max(0, maxTolerance - futureCost);
    }
}
