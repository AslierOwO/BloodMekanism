package dev.bloodmekanism.client;

import java.util.List;
import java.util.function.Supplier;
import dev.bloodmekanism.machine.RedstoneMode;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

final class FactoryControlTab extends GuiInsetElement<Void> {
    enum Type { INPUT, OUTPUT, TIER, REDSTONE }

    private static final ResourceLocation CONFIGURATION = resource("configuration.png");
    private static final ResourceLocation TRANSPORTER = resource("transporter_config.png");
    private static final ResourceLocation UPGRADE = resource("upgrade.png");
    private static final ResourceLocation REDSTONE_DISABLED = resource("redstone_control_disabled.png");
    private static final ResourceLocation REDSTONE_HIGH = resource("redstone_control_high.png");
    private static final ResourceLocation REDSTONE_LOW = resource("redstone_control_low.png");

    private final Type type;
    private final Supplier<List<Component>> tooltip;
    private final Supplier<RedstoneMode> redstoneMode;
    private final Runnable action;

    FactoryControlTab(IGuiWrapper gui, Type type, int y, boolean left, Supplier<List<Component>> tooltip,
          Supplier<RedstoneMode> redstoneMode, Runnable action) {
        super(overlay(type), gui, null, left ? -26 : gui.getWidth(), y, 26, 18, left);
        this.type = type;
        this.tooltip = tooltip;
        this.redstoneMode = redstoneMode;
        this.action = action;
    }

    private static ResourceLocation resource(String name) {
        return MekanismUtils.getResource(ResourceType.GUI, name);
    }

    private static ResourceLocation overlay(Type type) {
        return switch (type) {
            case INPUT -> CONFIGURATION;
            case OUTPUT -> TRANSPORTER;
            case TIER -> UPGRADE;
            case REDSTONE -> REDSTONE_DISABLED;
        };
    }

    @Override
    protected ResourceLocation getOverlay() {
        if (type != Type.REDSTONE || redstoneMode == null) return super.getOverlay();
        return switch (redstoneMode.get()) {
            case HIGH -> REDSTONE_HIGH;
            case LOW -> REDSTONE_LOW;
            default -> REDSTONE_DISABLED;
        };
    }

    @Override
    protected void colorTab(GuiGraphics graphics) {
        switch (type) {
            case INPUT -> MekanismRenderer.color(graphics, SpecialColors.TAB_CONFIGURATION);
            case OUTPUT -> MekanismRenderer.color(graphics, SpecialColors.TAB_TRANSPORTER);
            case TIER -> MekanismRenderer.color(graphics, SpecialColors.TAB_UPGRADE);
            case REDSTONE -> MekanismRenderer.color(graphics, SpecialColors.TAB_REDSTONE_CONTROL);
        }
    }

    @Override
    public void renderToolTip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderToolTip(graphics, mouseX, mouseY);
        displayTooltips(graphics, mouseX, mouseY, tooltip.get());
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (action != null) action.run();
    }

    @Override
    public boolean isValidClickButton(int button) {
        return action != null && button == 0;
    }
}
