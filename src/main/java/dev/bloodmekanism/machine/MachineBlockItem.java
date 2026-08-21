package dev.bloodmekanism.machine;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class MachineBlockItem extends BlockItem {
    private final FactoryTier tier;
    private final FactoryMode mode;

    public MachineBlockItem(MachineBlock block, Item.Properties properties) {
        super(block, properties);
        tier = block.tier();
        mode = block.fixedMode();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MachineBlock block = (MachineBlock) getBlock();
        if (block.kind() == MachineBlock.Kind.HEMOGENIC) {
            tooltip.add(Component.translatable("tooltip.bloodmekanism.hemogenic.1").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.bloodmekanism.hemogenic.2").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (block.kind() == MachineBlock.Kind.WILL_GENERATOR) {
            tooltip.add(Component.translatable("tooltip.bloodmekanism.will_generator.1").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.bloodmekanism.will_generator.2").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.bloodmekanism." + modeKey() + ".1").withStyle(ChatFormatting.GRAY));
        if (mode == FactoryMode.ALTAR) {
            tooltip.add(Component.translatable("tooltip.bloodmekanism.altar.2", tier.bloodTier(), tier.batchSize()).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.bloodmekanism." + modeKey() + ".2", tier.batchSize()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private String modeKey() {
        return switch (mode) {
            case ALTAR -> "altar";
            case ALCHEMY_TABLE -> "alchemy";
            case ALCHEMY_ARRAY -> "array";
            case SOUL_FORGE -> "soul_forge";
            case ARC -> "arc";
        };
    }
}
