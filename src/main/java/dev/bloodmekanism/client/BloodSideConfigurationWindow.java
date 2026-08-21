package dev.bloodmekanism.client;

import dev.bloodmekanism.machine.ConnectionMode;
import dev.bloodmekanism.machine.MachineResource;
import dev.bloodmekanism.machine.RelativeMachineSide;
import dev.bloodmekanism.machine.blockentity.BaseMachineBlockEntity;
import mekanism.api.text.EnumColor;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.gui.element.button.BasicColorButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

final class BloodSideConfigurationWindow extends GuiWindow {
    private final BiFunction<MachineResource, RelativeMachineSide, ConnectionMode> modeSupplier;
    private final BooleanSupplier autoOutput;
    private final IntConsumer clickHandler;
    private final List<ResourceTab> resourceTabs = new ArrayList<>();
    private final MekanismImageButton ejectButton;
    private MachineResource currentResource = MachineResource.ITEM;

    BloodSideConfigurationWindow(IGuiWrapper gui, int x, int y,
          BiFunction<MachineResource, RelativeMachineSide, ConnectionMode> modeSupplier,
          BooleanSupplier autoOutput, IntConsumer clickHandler, List<MachineResource> resources) {
        super(gui, x, y, 156, 135, WindowType.SIDE_CONFIG);
        this.modeSupplier = modeSupplier;
        this.autoOutput = autoOutput;
        this.clickHandler = clickHandler;
        interactionStrategy = InteractionStrategy.ALL;

        addChild(new GuiInnerScreen(gui, relativeX + 41, relativeY + 25, 74, 12, () -> List.of(
              currentResource == MachineResource.ENERGY
                    ? Component.translatable("gui.bloodmekanism.eject_unavailable")
                    : Component.translatable(autoOutput.getAsBoolean()
                          ? "gui.bloodmekanism.eject_on" : "gui.bloodmekanism.eject_off"))));

        currentResource = resources.get(0);
        for (int index = 0; index < resources.size(); index++) {
            MachineResource resource = resources.get(index);
            ResourceTab tab = addChild(new ResourceTab(gui, relativeX - 26, relativeY + 2 + 28 * index, resource, this));
            resourceTabs.add(tab);
        }

        ejectButton = addChild(new MekanismImageButton(gui, relativeX + 136, relativeY + 6, 14,
              getButtonLocation("auto_eject"), () -> clickHandler.accept(1),
              (element, graphics, mouseX, mouseY) -> displayTooltips(graphics, mouseX, mouseY,
                    Component.translatable("gui.bloodmekanism.auto_eject"),
                    Component.translatable(autoOutput.getAsBoolean() ? "gui.bloodmekanism.output_on" : "gui.bloodmekanism.output_off"))));
        addChild(new MekanismImageButton(gui, relativeX + 136, relativeY + 95, 14,
              getButtonLocation("clear_sides"), () -> clickHandler.accept(BaseMachineBlockEntity.clearSidesButtonId(currentResource)),
              (element, graphics, mouseX, mouseY) -> displayTooltips(graphics, mouseX, mouseY,
                    Component.translatable("gui.bloodmekanism.clear_sides"))));

        addSideButton(RelativeMachineSide.BOTTOM, 68, 92);
        addSideButton(RelativeMachineSide.TOP, 68, 46);
        addSideButton(RelativeMachineSide.FRONT, 68, 69);
        addSideButton(RelativeMachineSide.BACK, 45, 92);
        addSideButton(RelativeMachineSide.LEFT, 45, 69);
        addSideButton(RelativeMachineSide.RIGHT, 91, 69);
        updateTabs();
    }

    private void addSideButton(RelativeMachineSide side, int x, int y) {
        addChild(new BasicColorButton(gui(), relativeX + x, relativeY + y, 22,
              () -> colorFor(currentResource, modeSupplier.apply(currentResource, side)),
              () -> clickHandler.accept(BaseMachineBlockEntity.sideButtonId(currentResource, side)),
              () -> clickHandler.accept(BaseMachineBlockEntity.reverseSideButtonId(currentResource, side)),
              (element, graphics, mouseX, mouseY) -> displayTooltips(graphics, mouseX, mouseY,
                    side.title(), modeSupplier.apply(currentResource, side).title(),
                    Component.translatable("gui.bloodmekanism.side_click_help"))));
    }

    private static EnumColor colorFor(MachineResource resource, ConnectionMode mode) {
        if (resource == MachineResource.ENERGY && mode == ConnectionMode.INPUT) return EnumColor.DARK_GREEN;
        return switch (mode) {
            case NONE -> EnumColor.GRAY;
            case INPUT -> EnumColor.DARK_RED;
            case OUTPUT -> EnumColor.DARK_BLUE;
            case BOTH -> EnumColor.PURPLE;
        };
    }

    private void setCurrentResource(MachineResource resource) {
        currentResource = resource;
        updateTabs();
    }

    private void updateTabs() {
        for (ResourceTab tab : resourceTabs) tab.visible = currentResource != tab.resource;
        ejectButton.active = currentResource != MachineResource.ENERGY;
    }

    @Override
    public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderForeground(graphics, mouseX, mouseY);
        drawTitleText(graphics, Component.translatable("gui.bloodmekanism.config_type", currentResource.title()), 5);
        drawCenteredText(graphics, Component.translatable("gui.bloodmekanism.slots"), relativeX + 80, relativeY + 120, subheadingTextColor());
    }

    @Override
    protected int getTitlePadEnd() {
        return super.getTitlePadEnd() + 15;
    }

    private static final class ResourceTab extends GuiInsetElement<Void> {
        private final MachineResource resource;
        private final BloodSideConfigurationWindow window;

        private ResourceTab(IGuiWrapper gui, int x, int y, MachineResource resource, BloodSideConfigurationWindow window) {
            super(resource(resource), gui, null, x, y, 26, 18, true);
            this.resource = resource;
            this.window = window;
        }

        private static ResourceLocation resource(MachineResource resource) {
            String name = switch (resource) {
                case ITEM -> "items.png";
                case FLUID -> "fluids.png";
                case ENERGY -> "energy.png";
                case GAS -> "gases.png";
            };
            return MekanismUtils.getResource(ResourceType.GUI, name);
        }

        @Override
        protected void colorTab(GuiGraphics graphics) {
            switch (resource) {
                case ITEM -> MekanismRenderer.color(graphics, SpecialColors.TAB_ITEM_CONFIG);
                case FLUID -> MekanismRenderer.color(graphics, SpecialColors.TAB_FLUID_CONFIG);
                case ENERGY -> MekanismRenderer.color(graphics, SpecialColors.TAB_ENERGY_CONFIG);
                case GAS -> MekanismRenderer.color(graphics, SpecialColors.TAB_GAS_CONFIG);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            window.setCurrentResource(resource);
        }

        @Override
        public void renderToolTip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
            super.renderToolTip(graphics, mouseX, mouseY);
            displayTooltips(graphics, mouseX, mouseY, resource.title());
        }
    }
}
