package dev.bloodmekanism.client;

import java.util.List;
import java.util.Locale;
import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.RedstoneMode;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.will.WillGas;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.menu.UniversalFactoryMenu;
import mekanism.api.math.FloatingLong;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiFluidBar;
import mekanism.client.gui.element.bar.GuiDigitalBar;
import mekanism.client.gui.element.bar.GuiChemicalBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.GuiElement;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UniversalFactoryScreen extends GuiMekanism<UniversalFactoryMenu> {
    private final SyncedFluidTank inputTank;
    private final SyncedFluidTank outputTank;
    private final SyncedFluidTank recipeWaterTank;
    private final SyncedGasTank willTank;
    private FactoryControlTab sideConfigTab;
    private FactoryControlTab upgradeTab;

    public UniversalFactoryScreen(UniversalFactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 238;
        imageHeight = 198;
        inventoryLabelX = 38;
        inventoryLabelY = 101;
        titleLabelY = 4;
        inputTank = new SyncedFluidTank(menu::inputFluidStack, menu::inputFluidMax);
        outputTank = new SyncedFluidTank(menu::outputFluidStack, menu::outputFluidMax);
        recipeWaterTank = new SyncedFluidTank(menu::recipeWaterStack, menu::recipeWaterMax);
        willTank = new SyncedGasTank(menu::willGasStack, menu::willGasCapacity);
    }

    @Override
    protected void addGuiElements() {
        addSlotFrames();
        addRenderableWidget(new AltarTargetSlot());
        addRenderableWidget(new GuiVerticalPowerBar(this, energyInfo(), imageWidth - 12, 18, 63));
        addRenderableWidget(new GuiEnergyTab(this, () -> List.of(
              MekanismLang.USING.translate(EnergyDisplay.of(FloatingLong.create(menu.lastEnergyUsed()))),
              MekanismLang.NEEDED.translate(EnergyDisplay.of(FloatingLong.create(Math.max(0L, (long) menu.maxEnergy() - menu.energy())))))));

        List<mekanism.api.fluid.IExtendedFluidTank> tanks = List.of(inputTank, outputTank, recipeWaterTank);
        addRenderableWidget(new GuiFluidBar(this, GuiFluidBar.getProvider(inputTank, tanks), 20, 82, 83, 4, true));
        addRenderableWidget(new GuiFluidBar(this, GuiFluidBar.getProvider(outputTank, tanks), 107, 82, 83, 4, true));
        addRenderableWidget(new GuiFluidBar(this, GuiFluidBar.getProvider(recipeWaterTank, tanks), 78, 91, 80, 4, true) {
            @Override public void drawBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
                if (menu.mode() == FactoryMode.ALCHEMY_TABLE) super.drawBackground(graphics, mouseX, mouseY, partialTicks);
            }

            @Override public void renderToolTip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
                if (menu.mode() == FactoryMode.ALCHEMY_TABLE) super.renderToolTip(graphics, mouseX, mouseY);
            }
        });
        addRenderableWidget(new GuiChemicalBar<Gas, GasStack>(this,
              GuiChemicalBar.getProvider(willTank, List.of(willTank)), 78, 91, 80, 4, true) {
            @Override public void drawBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
                if (menu.mode() == FactoryMode.SOUL_FORGE) super.drawBackground(graphics, mouseX, mouseY, partialTicks);
            }

            @Override public void renderToolTip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
                if (menu.mode() == FactoryMode.SOUL_FORGE) {
                    displayTooltips(graphics, mouseX, mouseY, Component.translatable("gui.bloodmekanism.will_amount",
                          formatWill(menu.willGasStack().getAmount()), formatWill(menu.willGasCapacity())));
                }
            }
        });
        addRenderableWidget(new GuiDigitalBar(this, mechanicalLpInfo(), 78, 91, 80) {
            @Override public void drawBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
                if (menu.mechanicalLpCapacity() > 0) super.drawBackground(graphics, mouseX, mouseY, partialTicks);
            }

            @Override public void renderToolTip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
                if (menu.mechanicalLpCapacity() > 0) {
                    displayTooltips(graphics, mouseX, mouseY, List.of(
                          Component.translatable("gui.bloodmekanism.mechanical_lp", menu.mechanicalLp(), menu.mechanicalLpCapacity()),
                          mechanicalLpState()));
                }
            }
        });

        addRenderableWidget(new GuiProgress(this::recipeProgress, ProgressType.SMALL_RIGHT, this, 109, 43) {
            @Override public void renderToolTip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
                super.renderToolTip(graphics, mouseX, mouseY);
                displayTooltips(graphics, mouseX, mouseY, menu.processingStatus().title());
            }
        });
        sideConfigTab = addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.INPUT, 6, true,
              () -> List.of(Component.translatable("gui.bloodmekanism.side_config")), null, this::openSideConfiguration));
        addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.OUTPUT, 34, true,
              () -> List.of(Component.translatable("gui.bloodmekanism.transporter_config"), outputState()), null, () -> clickControl(1)));
        upgradeTab = addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.TIER, 6, false,
              () -> List.of(MekanismLang.UPGRADES.translate(),
                    Component.translatable("gui.bloodmekanism.factory_tier", menu.tier().bloodTier()),
                    Component.translatable("gui.bloodmekanism.batch", menu.tier().batchSize()),
                    Component.translatable("gui.bloodmekanism.speed_upgrades", menu.speedUpgrades(), 8),
                    Component.translatable("gui.bloodmekanism.energy_upgrades", menu.energyUpgrades(), 8)), null, this::openUpgradeWindow));
        addRenderableWidget(new FactoryControlTab(this, FactoryControlTab.Type.REDSTONE, 154, false,
              () -> List.of(redstoneMode().title()), this::redstoneMode, () -> clickControl(2)));
    }

    private void addSlotFrames() {
        for (int index = 0; index < menu.slots.size(); index++) {
            if (index == UniversalFactoryBlockEntity.UPGRADE_INPUT_SLOT || index == UniversalFactoryBlockEntity.UPGRADE_OUTPUT_SLOT) continue;
            Slot slot = menu.slots.get(index);
            SlotType type = index < UniversalFactoryBlockEntity.INPUT_COUNT ? SlotType.INPUT
                  : index == UniversalFactoryBlockEntity.CATALYST_SLOT ? SlotType.EXTRA
                  : index < UniversalFactoryBlockEntity.UPGRADE_INPUT_SLOT ? SlotType.OUTPUT
                  : index < UniversalFactoryBlockEntity.SLOT_COUNT ? SlotType.EXTRA : SlotType.NORMAL;
            GuiSlot frame = new GuiSlot(type, this, slot.x - 1, slot.y - 1);
            addRenderableWidget(frame);
        }
    }

    private final class AltarTargetSlot extends GuiSlot {
        private AltarTargetSlot() {
            super(SlotType.EXTRA, UniversalFactoryScreen.this, 89, 20);
            stored(menu::altarTargetStack);
            setRenderHover(true);
            hover((element, graphics, mouseX, mouseY) -> displayTooltips(graphics, mouseX, mouseY,
                  menu.altarTarget() == 0
                        ? List.of(Component.translatable("gui.bloodmekanism.altar_target_off"),
                              Component.translatable("gui.bloodmekanism.altar_target_help"))
                        : List.of(Component.translatable("gui.bloodmekanism.altar_target", menu.altarTargetStack().getHoverName()),
                              Component.translatable("gui.bloodmekanism.altar_target_chain"))));
            click((element, mouseX, mouseY) -> {
                if (menu.mode() != FactoryMode.ALTAR) return false;
                clickControl(UniversalFactoryBlockEntity.ALTAR_TARGET_BUTTON);
                return true;
            });
        }

        @Override public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (menu.mode() == FactoryMode.ALTAR) super.renderWidget(graphics, mouseX, mouseY, partialTicks);
        }

        @Override public void renderForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
            if (menu.mode() == FactoryMode.ALTAR) super.renderForeground(graphics, mouseX, mouseY);
        }

        @Nullable @Override public GuiElement mouseClickedNested(double mouseX, double mouseY, int button) {
            return menu.mode() == FactoryMode.ALTAR ? super.mouseClickedNested(mouseX, mouseY, button) : null;
        }
    }

    private double recipeProgress() {
        if (menu.activeInputMask() == 0 || menu.maxProgress() <= 0) return 0;
        return (double) menu.progress() / menu.maxProgress();
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

    private IBarInfoHandler mechanicalLpInfo() {
        return new IBarInfoHandler() {
            @Override public double getLevel() {
                return menu.mechanicalLpCapacity() <= 0 ? 0 : (double) menu.mechanicalLp() / menu.mechanicalLpCapacity();
            }
        };
    }

    private Component mechanicalLpState() {
        if (menu.mechanicalLpRate() > 0) {
            return Component.translatable("gui.bloodmekanism.mechanical_lp_charging", menu.mechanicalLpRate());
        }
        if (menu.mechanicalLp() >= menu.mechanicalLpCapacity()) {
            return Component.translatable("gui.bloodmekanism.mechanical_lp_full");
        }
        return switch (menu.processingStatus()) {
            case LIFE_ESSENCE_LOW -> Component.translatable("gui.bloodmekanism.mechanical_lp_no_essence");
            case ENERGY_LOW -> Component.translatable("gui.bloodmekanism.mechanical_lp_no_energy");
            case REDSTONE_DISABLED -> menu.processingStatus().title();
            default -> Component.translatable("gui.bloodmekanism.mechanical_lp_waiting");
        };
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
        List<MachineResource> resources = menu.mode() == FactoryMode.SOUL_FORGE
              ? List.of(MachineResource.ITEM, MachineResource.FLUID, MachineResource.GAS, MachineResource.ENERGY)
              : List.of(MachineResource.ITEM, MachineResource.FLUID, MachineResource.ENERGY);
        BloodSideConfigurationWindow window = new BloodSideConfigurationWindow(this, imageWidth / 2 - 78, 15,
              menu::sideMode, menu::autoOutput, this::clickControl, resources);
        window.setTabListeners(closed -> sideConfigTab.active = true, reattached -> sideConfigTab.active = false);
        sideConfigTab.active = false;
        addWindow(window);
    }

    private void openUpgradeWindow() {
        BloodUpgradeWindow window = new BloodUpgradeWindow(this, imageWidth / 2 - 78, 15,
              menu::speedUpgrades, menu::energyUpgrades, menu::upgradeTicks, this::clickControl,
              UniversalFactoryBlockEntity.UPGRADE_INPUT_SLOT, UniversalFactoryBlockEntity.UPGRADE_OUTPUT_SLOT);
        window.setTabListeners(closed -> upgradeTab.active = true, reattached -> upgradeTab.active = false);
        upgradeTab.active = false;
        addWindow(window);
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        renderTitleText(graphics);
        drawScaledCenteredTextScaledBound(graphics, Component.translatable("gui.bloodmekanism.item_inputs"),
              48, 12, titleTextColor(), 56, 1);
        drawScaledCenteredTextScaledBound(graphics, Component.translatable("gui.bloodmekanism.item_outputs"),
              166, 12, titleTextColor(), 56, 1);
        drawScaledCenteredTextScaledBound(graphics, menu.mode().title(),
              105, 68, titleTextColor(), 80, 1);
        if (menu.mechanicalLpCapacity() > 0) drawString(graphics, Component.literal("LP"), 62, 91, titleTextColor());
        if (menu.mode() == FactoryMode.ALCHEMY_TABLE) drawString(graphics,
              Component.translatable("gui.bloodmekanism.recipe_water"), 47, 91, titleTextColor());
        if (menu.mode() == FactoryMode.SOUL_FORGE) drawString(graphics, Component.literal("Will"), 54, 91, titleTextColor());
        drawString(graphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
        super.drawForegroundText(graphics, mouseX, mouseY);
    }

    private static String formatWill(long gas) {
        return String.format(Locale.ROOT, "%.2f", WillGas.toWill(gas));
    }
}
