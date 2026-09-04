package dev.bloodmekanism.client;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.VirtualSlotContainerScreen;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Upgrade slot rendered in the Mekanism-style upgrade window and backed by the machine slot. */
final class BloodGuiVirtualSlot extends GuiSlot {
    private final Slot slot;

    BloodGuiVirtualSlot(IGuiWrapper gui, int x, int y, Slot slot) {
        super(SlotType.NORMAL, gui, x, y);
        this.slot = slot;
        stored(slot::getItem);
        with(SlotOverlay.UPGRADE);
        setRenderHover(true);
    }

    @Override
    protected void drawContents(@NotNull GuiGraphics graphics) {
        if (!slot.getItem().isEmpty()) {
            gui().renderItemWithOverlay(graphics, slot.getItem(), getX() + 1, getY() + 1, 1, null);
        }
    }

    @Nullable
    @Override
    public GuiElement mouseClickedNested(double mouseX, double mouseY, int button) {
        if (mouseX >= getX() && mouseY >= getY() && mouseX < getX() + 18 && mouseY < getY() + 18
              && gui() instanceof VirtualSlotContainerScreen<?> screen) {
            screen.slotClicked(slot, button);
            return this;
        }
        return super.mouseClickedNested(mouseX, mouseY, button);
    }
}
