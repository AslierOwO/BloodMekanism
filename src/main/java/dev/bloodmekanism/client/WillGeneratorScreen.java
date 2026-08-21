package dev.bloodmekanism.client;

import dev.bloodmekanism.machine.RedstoneMode;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.menu.WillGeneratorMenu;
import dev.bloodmekanism.will.WillGas;
import java.util.List;
import java.util.Locale;
import mekanism.api.math.FloatingLong;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
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

public final class WillGeneratorScreen extends GuiMekanism<WillGeneratorMenu> {
    private final SyncedFluidTank lifeTank;
    private final SyncedGasTank willTank;
    private FactoryControlTab sideConfigTab;
    private FactoryControlTab upgradeTab;

    public WillGeneratorScreen(WillGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 238;
        imageHeight = 177;
        inventoryLabelX = 38;
        inventoryLabelY = 80;
        lifeTank = new SyncedFluidTank(
              () -> menu.fluid() <= 0 ? FluidStack.EMPTY : new FluidStack(BloodMagicFluids.LIFE_ESSENCE_FLUID.get(), menu.fluid()),
              menu::maxFluid);
        willTank = new SyncedGasTank(menu::gasStack, menu::gasCapacity);
    }

    @Override protected void addGuiElements() {
        addSlotFrames();
        addRenderableWidget(new GuiVerticalPowerBar(this, energyInfo(), imageWidth - 12, 17, 54));
        addRenderableWidget(new GuiEnergyTab(this, () -> List.of(
              MekanismLang.USING.translate(EnergyDisplay.of(FloatingLong.create(menu.lastEnergyUsed()))),
              MekanismLang.NEEDED.translate(EnergyDisplay.of(FloatingLong.create(Math.max(0L, (long) menu.maxEnergy() - menu.energy())))))));
        addRenderableWidget(new GuiFluidGauge(() -> lifeTank, () -> List.of(lifeTank), GaugeType.STANDARD, this, 105, 16)
              .setLabel(Component.translatable("gui.bloodmekanism.life_essence")));
        addRenderableWidget(new GuiGasGauge(() -> willTank, () -> List.of(willTank), GaugeType.STANDARD, this, 137, 16) {
            @Override public List<Component> getTooltipText() {
                if (willTank.isEmpty()) return List.of(MekanismLang.EMPTY.translate());
                return List.of(Component.translatable("gui.bloodmekanism.will_amount",
                      formatWill(willTank.getStored()), formatWill(willTank.getCapacity())));
            }
        }.setLabel(Component.translatable("gui.bloodmekanism.will")));
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
            Slot slot = menu.slots.get(index);
            addRenderableWidget(new GuiSlot(index == 0 ? SlotType.INPUT : index < 3 ? SlotType.EXTRA : SlotType.NORMAL, this, slot.x - 1, slot.y - 1));
        }
    }

    private IBarInfoHandler energyInfo() {
        return new IBarInfoHandler() {
            @Override public Component getTooltip() { return Component.translatable("gui.bloodmekanism.energy", menu.energy(), menu.maxEnergy()); }
            @Override public double getLevel() { return menu.maxEnergy() <= 0 ? 0 : (double) menu.energy() / menu.maxEnergy(); }
        };
    }

    private double progress() { return menu.maxProgress() <= 0 ? 0 : (double) menu.progress() / menu.maxProgress(); }
    private Component outputState() { return Component.translatable(menu.autoOutput() ? "gui.bloodmekanism.output_on" : "gui.bloodmekanism.output_off"); }
    private RedstoneMode redstoneMode() { return RedstoneMode.values()[Math.min(menu.redstoneMode(), RedstoneMode.values().length - 1)]; }
    private static String formatWill(long gas) { return String.format(Locale.ROOT, "%.2f", WillGas.toWill(gas)); }

    void clickControl(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void openSideConfiguration() {
        BloodSideConfigurationWindow window = new BloodSideConfigurationWindow(this, imageWidth / 2 - 78, 15,
              menu::sideMode, menu::autoOutput, this::clickControl,
              List.of(MachineResource.ITEM, MachineResource.FLUID, MachineResource.GAS, MachineResource.ENERGY));
        window.setTabListeners(closed -> sideConfigTab.active = true, reattached -> sideConfigTab.active = false);
        sideConfigTab.active = false;
        addWindow(window);
    }

    private void openUpgradeWindow() {
        BloodUpgradeWindow window = new BloodUpgradeWindow(this, imageWidth / 2 - 78, 15,
              menu::speedUpgrades, menu::energyUpgrades, this::clickControl);
        window.setTabListeners(closed -> upgradeTab.active = true, reattached -> upgradeTab.active = false);
        upgradeTab.active = false;
        addWindow(window);
    }

    @Override protected void drawForegroundText(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        renderTitleText(graphics);
        drawString(graphics, Component.translatable("gui.bloodmekanism.item_input"), 45, 19, titleTextColor());
        drawString(graphics, Component.literal("S"), 198, 27, titleTextColor());
        drawString(graphics, Component.literal("E"), 198, 53, titleTextColor());
        drawString(graphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
        super.drawForegroundText(graphics, mouseX, mouseY);
    }
}
