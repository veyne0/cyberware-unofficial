package com.maxwell.cyber_ware_port.client.screen.roboSurgeon;

import com.maxwell.cyber_ware_port.common.effect.MobPartEffects;
import com.maxwell.cyber_ware_port.common.item.base.BodyPartType;
import com.maxwell.cyber_ware_port.common.util.MobSurfaceParts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * 可拆解生物查询界面(手术台右上角按钮进入):
 * 生物列表 -> 部位列表 -> 部位详情(技能/特性 + 获取概率)。
 * 纯 Screen 无 Menu,ESC 逐级返回。
 */
public class MobQueryScreen extends Screen {
    private static final int W = 220, H = 170;
    private static final int LINE = 10;
    private static final int LIST_X = 10, LIST_Y = 20, LIST_W = W - 20;

    private EntityType<?> selectedMob;
    private BodyPartType selectedPart;
    private float scroll = 0f;

    public MobQueryScreen() {
        super(Component.translatable("gui.cyberware_unofficial.query.title"));
    }

    // ==================== 列表数据 ====================

    private record Row(List<FormattedCharSequence> text, int color, Runnable onClick) {}

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        if (selectedMob == null) {
            for (EntityType<?> type : MobSurfaceParts.supportedTypes()) {
                rows.add(new Row(this.font.split(Component.translatable(type.getDescriptionId()), LIST_W - 4),
                        0xFFB0F0FF, () -> {
                            this.selectedMob = type;
                            this.scroll = 0f;
                        }));
            }
        } else if (selectedPart == null) {
            for (BodyPartType part : MobSurfaceParts.uniqueParts(selectedMob)) {
                Component name = Component.translatable("item.cyberware_unofficial.mob_part.part." + part.getSerializedName());
                rows.add(new Row(this.font.split(name, LIST_W - 4),
                        0xFFB0F0FF, () -> {
                            this.selectedPart = part;
                            this.scroll = 0f;
                        }));
            }
        } else {
            // 部位详情:概率 + 特性 + 技能(名称彩色,描述灰色)
            rows.addAll(detailRows());
        }
        return rows;
    }

    private List<Row> detailRows() {
        List<Row> rows = new ArrayList<>();
        // 获取方式
        for (FormattedCharSequence line : this.font.split(
                Component.translatable("gui.cyberware_unofficial.query.obtain_method").withStyle(ChatFormatting.WHITE),
                LIST_W - 4)) {
            rows.add(new Row(List.of(line), 0xFFFFFFFF, null));
        }
        // 获取概率(%号在代码里拼好,避免翻译串里的字面 %)
        if (selectedMob == EntityType.WITHER) {
            for (FormattedCharSequence line : this.font.split(
                    Component.translatable("gui.cyberware_unofficial.query.chance_wither").withStyle(ChatFormatting.WHITE),
                    LIST_W - 4)) {
                rows.add(new Row(List.of(line), 0xFFFFFFFF, null));
            }
        } else {
            String chance = String.format("%.0f%%", MobSurfaceParts.dropChance(selectedMob) * 100);
            for (FormattedCharSequence line : this.font.split(
                    Component.translatable("gui.cyberware_unofficial.query.chance", chance).withStyle(ChatFormatting.WHITE),
                    LIST_W - 4)) {
                rows.add(new Row(List.of(line), 0xFFFFFFFF, null));
            }
        }
        rows.add(new Row(this.font.split(Component.literal(""), LIST_W), 0, null));
        ResourceLocation mobId = new ResourceLocation("minecraft", EntityType.getKey(selectedMob).getPath());
        for (String key : MobPartEffects.getEffectKeys(mobId, selectedPart)) {
            boolean isAbilityDesc = key.startsWith("ability.") && key.endsWith(".desc");
            int color = isAbilityDesc ? 0xFFFFE38A : 0xFF9FEFFF;
            Component name = isAbilityDesc
                    ? Component.translatable(key.substring(0, key.length() - 5))
                    : Component.translatable(key);
            for (FormattedCharSequence line : this.font.split(name, LIST_W - 4)) {
                rows.add(new Row(List.of(line), color, null));
            }
            Component desc = Component.translatable(isAbilityDesc ? key : key + ".desc").withStyle(ChatFormatting.GRAY);
            for (FormattedCharSequence line : this.font.split(desc, LIST_W - 4)) {
                rows.add(new Row(List.of(line), 0xFFC8C8C8, null));
            }
        }
        return rows;
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 返回按钮
        if (mouseX >= this.leftPos() + 4 && mouseX < this.leftPos() + 16
                && mouseY >= this.topPos() + 4 && mouseY < this.topPos() + 15 && goBack()) {
            return true;
        }
        List<Row> rows = buildRows();
        int idx = (int) ((mouseY - (this.topPos() + LIST_Y) + this.scroll) / LINE);
        if (mouseX >= this.leftPos() + LIST_X && mouseX < this.leftPos() + LIST_X + LIST_W
                && idx >= 0 && idx < rows.size()) {
            Row row = rows.get(idx);
            if (row.onClick() != null) {
                row.onClick().run();
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int leftPos() {
        return (this.width - W) / 2;
    }

    private int topPos() {
        return (this.height - H) / 2;
    }

    /** 返回上一级;已在顶层返回 false。保留滚动位置,回到之前浏览的地方 */
    private boolean goBack() {
        if (this.selectedPart != null) {
            this.selectedPart = null;
        } else if (this.selectedMob != null) {
            this.selectedMob = null;
        } else {
            return false;
        }
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8F));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && goBack()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scroll = Math.max(0, this.scroll - (float) delta * LINE);
        return true;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int x = leftPos(), y = topPos();
        g.fill(x, y, x + W, y + H, 0xF40D1012);
        g.renderOutline(x, y, W, H, 0xFF2E6E6E);
        // 返回按钮
        if (selectedMob != null) {
            g.drawString(this.font, "<", x + 4, y + 4, 0xFF3FBFBF, true);
        }
        // 标题
        Component title;
        if (selectedMob == null) {
            title = Component.translatable("gui.cyberware_unofficial.query.title");
        } else if (selectedPart == null) {
            title = Component.translatable(selectedMob.getDescriptionId());
        } else {
            title = Component.translatable(selectedMob.getDescriptionId())
                    .append(Component.translatable("item.cyberware_unofficial.mob_part.part." + selectedPart.getSerializedName()));
        }
        g.drawString(this.font, this.font.plainSubstrByWidth(title.getString(), W - 30), x + 18, y + 5, 0xFF3FBFBF, true);

        List<Row> rows = buildRows();
        int listTop = y + LIST_Y;
        int viewH = H - LIST_Y - 8;
        int contentH = 0;
        for (Row row : rows) contentH += row.text().size() * LINE;
        int maxScroll = Math.max(0, contentH - viewH);
        this.scroll = Math.min(this.scroll, maxScroll);
        g.enableScissor(x + LIST_X, listTop, x + LIST_X + LIST_W, listTop + viewH);
        int dy = listTop - (int) this.scroll;
        for (Row row : rows) {
            for (FormattedCharSequence line : row.text()) {
                // 文字全部带阴影,避免暗色背景下看不清
                g.drawString(this.font, line, x + LIST_X + 2, dy, row.color(), true);
                dy += LINE;
            }
            if (row.onClick() != null) {
                // 可点击行下画细分隔线
                g.fill(x + LIST_X + 1, dy - 1, x + LIST_X + LIST_W - 1, dy, 0xFF1E3A3A);
            }
        }
        g.disableScissor();
        // 悬停高亮可点击行
        if (mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W && mouseY >= listTop && mouseY < listTop + viewH) {
            int idx = (int) ((mouseY - listTop + this.scroll) / LINE);
            if (idx >= 0 && idx < rows.size() && rows.get(idx).onClick() != null) {
                g.fill(x + LIST_X, listTop + idx * LINE - (int) this.scroll,
                        x + LIST_X + LIST_W, listTop + idx * LINE + LINE - (int) this.scroll, 0x30FFFFFF);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
