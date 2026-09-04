package dev.bloodmekanism.client;

import java.util.List;
import dev.bloodmekanism.machine.RedstoneMode;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.blockentity.HemogenicBlockEntity;
import dev.bloodmekanism.menu.HemogenicMenu;
import mekanism.api.math.FloatingLong;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.MekanismLang;
import mekanism.common.util.text.EnergyDisplay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import wayoftime.bloodmagic.common.fluid.BloodMagicFluids;

public final class HemogenicScreen extends GuiMekanism<HemogenicMenu> {
    private final SyncedFluidTank outputTank;
    private FactoryControlTab sideConfigTab;
    private FactoryControlTab upgradeTab;

    public HemogenicScreen(HemogenicMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 238;
        imageHeight = 177;
        inventoryLabelX = 38;
        inventoryLabelY = 80;
        outputTank = new SyncedFluidTank(
              () -> menu.fluid() <= 0 ? FluidStack.EMPTY : new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), menu.fluid()),
              menu::maxFluid);
    }

    @Override
    protected void addGuiElements() {
        addSlotFrames();
        addRenderableWidget(new GuiVerticalPowerBar(this, energyInfo(), imageWidth - 12, 17, 54));
        addRenderableWidget(new GuiEnergyTab(this, () -> List.of(
              MekanismLang.USING.translate(EnergyDisplay.of(FloatingLong.create(menu.lastEnergyUsed()))),
              MekanismLang.NEEDED.translate(EnergyDisplay.of(FloatingLong.create(Math.max(0L, (long) menu.maxEnergy() - menu.energy())))))));
        addRenderableWidget(new GuiFluidGauge(() -> outputTank, () -> List.of(outputTank), GaugeType.STANDARD, this, 116, 16)
              .setLabel(Component.translatable("gui.bloodmekanism.life_essence")));
        addRenderableWidget(new GuiProgress(this::progress, ProgressType.RIGHT, this, 78, 43));
        sideConfigTab = addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.INPUT, 6, true,
              () -> List.of(Component.translatable("gui.bloodmekanism.side_config")), null, this::openSideConfiguration));
        addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.OUTPUT, 34, true,
              () -> List.of(Component.translatable("gui.bloodmekanism.transporter_config"), outputState()), null, () -> clickControl(1)));
        upgradeTab = addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.TIER, 6, false,
              () -> List.of(MekanismLang.UPGRADES.translate(),
                    Component.translatable("gui.bloodmekanism.speed_upgrades", menu.speedUpgrades(), 8),
                    Component.translatable("gui.bloodmekanism.energy_upgrades", menu.energyUpgrades(), 8)), null, this::openUpgradeWindow));
        addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.REDSTONE, 133, false,
              () -> List.of(redstoneMode().title()), this::redstoneMode, () -> clickControl(2)));
    }

    private void addSlotFrames() {
        for (int index = 0; index < menu.slots.size(); index++) {
            if (index == HemogenicBlockEntity.UPGRADE_INPUT_SLOT || index == HemogenicBlockEntity.UPGRADE_OUTPUT_SLOT) continue;
            Slot slot = menu.slots.get(index);
            GuiSlot frame = new GuiSlot(index == 0 ? SlotType.INPUT : SlotType.EXTRA, this, slot.x - 1, slot.y - 1);
            addRenderableWidget(frame);
        }
    }

    private IBarInfoHandler energyInfo() {
        return new IBarInfoHandler() {
            @Override public Component getTooltip() {
                return Component.translatable("gui.bloodmekanism.energy", menu.energy(), menu.maxEnergy());
            }
            @Override public double getLevel() {
                return menu.maxEnergy() <= 0 ? 0 : (double) menu.energy() / menu.maxEnergy();
            }
        };
    }

    private double progress() {
        return menu.maxProgress() <= 0 ? 0 : (double) menu.progress() / menu.maxProgress();
    }

    private Component outputState() {
        return Component.translatable(menu.autoOutput() ? "gui.bloodmekanism.output_on" : "gui.bloodmekanism.output_off");
    }

    private RedstoneMode redstoneMode() {
        return RedstoneMode.values()[Math.min(menu.redstoneMode(), RedstoneMode.values().length - 1)];
    }

    void clickControl(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void openSideConfiguration() {
        BloodSideConfigurationWindow window = new BloodSideConfigurationWindow(this, imageWidth / 2 - 78, 15,
              menu::sideMode, menu::autoOutput, this::clickControl,
              List.of(MachineResource.ITEM, MachineResource.FLUID, MachineResource.ENERGY));
        window.setTabListeners(closed -> sideConfigTab.active = true, reattached -> sideConfigTab.active = false);
        sideConfigTab.active = false;
        addWindow(window);
    }

    private void openUpgradeWindow() {
        BloodUpgradeWindow window = new BloodUpgradeWindow(this, imageWidth / 2 - 78, 15,
              menu::speedUpgrades, menu::energyUpgrades, menu::upgradeTicks, this::clickControl,
              HemogenicBlockEntity.UPGRADE_INPUT_SLOT, HemogenicBlockEntity.UPGRADE_OUTPUT_SLOT);
        window.setTabListeners(closed -> upgradeTab.active = true, reattached -> upgradeTab.active = false);
        upgradeTab.active = false;
        addWindow(window);
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        renderTitleText(graphics);
        drawTextScaledBound(graphics, Component.translatable("gui.bloodmekanism.item_input"), 51, 24, titleTextColor(), 72);
        drawString(graphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
        super.drawForegroundText(graphics, mouseX, mouseY);
    }
}
