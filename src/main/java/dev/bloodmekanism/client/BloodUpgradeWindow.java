package dev.bloodmekanism.client;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import dev.bloodmekanism.machine.blockentity.BaseMachineBlockEntity;
import mekanism.api.Upgrade;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.DigitalButton;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.custom.GuiSupportedUpgrades;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

final class BloodUpgradeWindow extends GuiWindow {
    private final Map<Upgrade, WrappedTextRenderer> typeRenderers = new EnumMap<>(Upgrade.class);
    private final WrappedTextRenderer noSelection = new WrappedTextRenderer(this, MekanismLang.UPGRADE_NO_SELECTION.translate());
    private final BloodUpgradeSelection selection;
    private final MekanismButton removeButton;
    private final IntConsumer clickHandler;

    BloodUpgradeWindow(IGuiWrapper gui, int x, int y, IntSupplier speedCount, IntSupplier energyCount, IntConsumer clickHandler) {
        super(gui, x, y, 156, 76 + 12 * GuiSupportedUpgrades.calculateNeededRows(), WindowType.UPGRADE);
        this.clickHandler = clickHandler;
        interactionStrategy = InteractionStrategy.ALL;
        selection = addChild(new BloodUpgradeSelection(gui, relativeX + 6, relativeY + 18, speedCount, energyCount));
        addChild(new GuiSupportedUpgrades(gui, relativeX + 6, relativeY + 68, Set.of(Upgrade.SPEED, Upgrade.ENERGY)));
        addChild(new GuiInnerScreen(gui, relativeX + 72, relativeY + 18, 59, 50));
        removeButton = addChild(new DigitalButton(gui, relativeX + 73, relativeY + 54, 56, 12,
              MekanismLang.UPGRADE_UNINSTALL, this::removeSelected, getOnHover(MekanismLang.UPGRADE_UNINSTALL_TOOLTIP)));
        removeButton.active = false;
    }

    private void removeSelected() {
        Upgrade selected = selection.selection();
        if (selected != null) {
            clickHandler.accept(BaseMachineBlockEntity.removeUpgradeButtonId(selected, Screen.hasShiftDown()));
        }
    }

    @Override
    public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderForeground(graphics, mouseX, mouseY);
        drawTitleText(graphics, MekanismLang.UPGRADES.translate(), 5);
        Upgrade selected = selection.selection();
        removeButton.active = selected != null && selection.count(selected) > 0;
        if (selected == null) {
            noSelection.renderWithScale(graphics, relativeX + 74, relativeY + 20, screenTextColor(), 56, 0.8F);
            return;
        }
        int count = selection.count(selected);
        int textY = relativeY + 20;
        WrappedTextRenderer renderer = typeRenderers.computeIfAbsent(selected,
              type -> new WrappedTextRenderer(this, MekanismLang.UPGRADE_TYPE.translate(type)));
        int lines = renderer.renderWithScale(graphics, relativeX + 74, textY, screenTextColor(), 56, 0.6F);
        textY += 6 * lines + 2;
        drawTextWithScale(graphics, MekanismLang.UPGRADE_COUNT.translate(count, selected.getMax()), relativeX + 74, textY, screenTextColor(), 0.6F);
        double effect = Math.round(Math.pow(10, count / 8.0) * 100) / 100.0;
        drawTextWithScale(graphics, MekanismLang.UPGRADES_EFFECT.translate(effect), relativeX + 74, textY + 6, screenTextColor(), 0.6F);
    }
}
