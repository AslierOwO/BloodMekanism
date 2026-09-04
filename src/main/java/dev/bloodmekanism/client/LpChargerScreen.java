package dev.bloodmekanism.client;

import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.RedstoneMode;
import dev.bloodmekanism.menu.LpChargerMenu;
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

import java.util.List;

public final class LpChargerScreen extends GuiMekanism<LpChargerMenu> {
    private final SyncedFluidTank lifeTank;
    private FactoryControlTab sideConfigTab;

    public LpChargerScreen(LpChargerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 238;
        imageHeight = 177;
        inventoryLabelX = 38;
        inventoryLabelY = 80;
        lifeTank = new SyncedFluidTank(
              () -> menu.fluid() <= 0 ? FluidStack.EMPTY
                    : new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), menu.fluid()),
              menu::maxFluid);
    }

    @Override protected void addGuiElements() {
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            SlotType type = index == 0 ? SlotType.INPUT : index == 1 ? SlotType.OUTPUT : SlotType.NORMAL;
            addRenderableWidget(new GuiSlot(type, this, slot.x - 1, slot.y - 1));
        }
        addRenderableWidget(new GuiVerticalPowerBar(this, energyInfo(), imageWidth - 12, 17, 54));
        addRenderableWidget(new GuiEnergyTab(this, () -> List.of(
              MekanismLang.USING.translate(EnergyDisplay.of(FloatingLong.create(menu.lastEnergyUsed()))),
              MekanismLang.NEEDED.translate(EnergyDisplay.of(FloatingLong.create(
                    Math.max(0L, (long) menu.maxEnergy() - menu.energy())))))));
        addRenderableWidget(new GuiFluidGauge(() -> lifeTank, () -> List.of(lifeTank), GaugeType.STANDARD,
              this, 130, 16).setLabel(Component.translatable("gui.bloodmekanism.life_essence")));
        addRenderableWidget(new GuiProgress(() -> menu.lastTransfer() > 0 ? 1 : 0,
              ProgressType.RIGHT, this, 76, 43) {
            @Override public void renderToolTip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
                super.renderToolTip(graphics, mouseX, mouseY);
                displayTooltips(graphics, mouseX, mouseY, menu.status().title());
            }
        });
        sideConfigTab = addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.INPUT, 6, true,
              () -> List.of(Component.translatable("gui.bloodmekanism.side_config")), null,
              this::openSideConfiguration));
        addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.OUTPUT, 34, true,
              () -> List.of(Component.translatable("gui.bloodmekanism.transporter_config"), outputState()),
              null, () -> clickControl(1)));
        addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.REDSTONE, 133, false,
              () -> List.of(redstoneMode().title()), this::redstoneMode, () -> clickControl(2)));
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

    private Component outputState() {
        return Component.translatable(menu.autoOutput()
              ? "gui.bloodmekanism.output_on" : "gui.bloodmekanism.output_off");
    }

    private RedstoneMode redstoneMode() {
        return RedstoneMode.values()[Math.min(menu.redstoneMode(), RedstoneMode.values().length - 1)];
    }

    private void clickControl(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void openSideConfiguration() {
        BloodSideConfigurationWindow window = new BloodSideConfigurationWindow(this, imageWidth / 2 - 78, 15,
              menu::sideMode, menu::autoOutput, this::clickControl,
              List.of(MachineResource.ITEM, MachineResource.FLUID, MachineResource.ENERGY));
        window.setTabListeners(closed -> sideConfigTab.active = true, reattached -> sideConfigTab.active = false);
        sideConfigTab.active = false;
        addWindow(window);
    }

    @Override protected void drawForegroundText(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        renderTitleText(graphics);
        drawString(graphics, Component.translatable("gui.bloodmekanism.orb_input"), 48, 19, titleTextColor());
        drawString(graphics, Component.translatable("gui.bloodmekanism.orb_output"), 86, 19, titleTextColor());
        drawTextScaledBound(graphics, menu.status().title(), 48, 59, screenTextColor(), 136);
        if (menu.orbCapacity() > 0) {
            drawString(graphics, Component.translatable("gui.bloodmekanism.orb_lp",
                  menu.orbLp(), menu.orbCapacity()), 48, 68, titleTextColor());
        }
        drawString(graphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
        super.drawForegroundText(graphics, mouseX, mouseY);
    }
}
