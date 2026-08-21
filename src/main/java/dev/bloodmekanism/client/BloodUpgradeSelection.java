package dev.bloodmekanism.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import mekanism.api.Upgrade;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

final class BloodUpgradeSelection extends GuiElement {
    private static final ResourceLocation SELECTION = MekanismUtils.getResource(ResourceType.GUI, "upgrade_selection.png");
    private static final int ROW_HEIGHT = 12;
    private final IntSupplier speedCount;
    private final IntSupplier energyCount;
    private Upgrade selected;

    BloodUpgradeSelection(IGuiWrapper gui, int x, int y, IntSupplier speedCount, IntSupplier energyCount) {
        super(gui, x, y, 66, 50);
        this.speedCount = speedCount;
        this.energyCount = energyCount;
        clickSound = SoundEvents.UI_BUTTON_CLICK;
    }

    private List<Upgrade> installed() {
        List<Upgrade> upgrades = new ArrayList<>(2);
        if (speedCount.getAsInt() > 0) upgrades.add(Upgrade.SPEED);
        if (energyCount.getAsInt() > 0) upgrades.add(Upgrade.ENERGY);
        return upgrades;
    }

    Upgrade selection() {
        if (selected != null && count(selected) <= 0) selected = null;
        return selected;
    }

    int count(Upgrade upgrade) {
        return upgrade == Upgrade.SPEED ? speedCount.getAsInt() : energyCount.getAsInt();
    }

    @Override
    public void drawBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackgroundTexture(graphics, GuiElementHolder.HOLDER, GuiElementHolder.HOLDER_SIZE, GuiElementHolder.HOLDER_SIZE);
        List<Upgrade> upgrades = installed();
        for (int index = 0; index < upgrades.size(); index++) {
            Upgrade upgrade = upgrades.get(index);
            int y = relativeY + 1 + index * ROW_HEIGHT;
            int state = upgrade == selection() ? 2 : isOverRow(mouseX, mouseY, index) ? 0 : 1;
            MekanismRenderer.color(graphics, upgrade.getColor());
            graphics.blit(SELECTION, relativeX + 1, y, 0, ROW_HEIGHT * state, 58, ROW_HEIGHT, 58, 36);
            MekanismRenderer.resetColor(graphics);
            gui().renderItem(graphics, UpgradeUtils.getStack(upgrade), relativeX + 3, y + 2, 0.5F);
        }
    }

    @Override
    public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Upgrade> upgrades = installed();
        for (int index = 0; index < upgrades.size(); index++) {
            Upgrade upgrade = upgrades.get(index);
            drawTextScaledBound(graphics, Component.translatable(upgrade.getTranslationKey()), relativeX + 13,
                  relativeY + 3 + index * ROW_HEIGHT, titleTextColor(), 44);
        }
    }

    @Override
    public void renderToolTip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        List<Upgrade> upgrades = installed();
        for (int index = 0; index < upgrades.size(); index++) {
            if (isOverRow(mouseX, mouseY, index)) {
                displayTooltips(graphics, mouseX, mouseY, upgrades.get(index).getDescription());
                return;
            }
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        int index = (int) ((mouseY - getY() - 1) / ROW_HEIGHT);
        List<Upgrade> upgrades = installed();
        if (index >= 0 && index < upgrades.size()) selected = upgrades.get(index);
    }

    @Override
    public boolean isValidClickButton(int button) {
        return button == 0;
    }

    private boolean isOverRow(double mouseX, double mouseY, int index) {
        int top = getY() + 1 + index * ROW_HEIGHT;
        return mouseX >= getX() + 1 && mouseX < getX() + 59 && mouseY >= top && mouseY < top + ROW_HEIGHT;
    }
}
