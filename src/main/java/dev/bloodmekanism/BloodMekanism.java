package dev.bloodmekanism;

import com.mojang.logging.LogUtils;
import dev.bloodmekanism.client.ClientSetup;
import dev.bloodmekanism.machine.blockentity.BaseMachineBlockEntity;
import dev.bloodmekanism.registry.ModContent;
import mekanism.api.Upgrade;
import mekanism.common.item.interfaces.IUpgradeItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BloodMekanism.MOD_ID)
public final class BloodMekanism {
    public static final String MOD_ID = "bloodmekanism";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BloodMekanism() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModContent.register(bus);
        bus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.addListener(this::installUpgrade);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
            event.enqueueWork(ClientSetup::registerScreens);
        }
    }

    private void installUpgrade(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof IUpgradeItem upgradeItem)) return;
        Upgrade upgrade = upgradeItem.getUpgradeType(stack);
        if (upgrade != Upgrade.SPEED && upgrade != Upgrade.ENERGY) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof BaseMachineBlockEntity machine)
              || !machine.supportsUpgrades()) return;
        if (!event.getLevel().isClientSide) {
            int added = machine.addUpgrades(upgrade, stack.getCount());
            if (added > 0 && !event.getEntity().getAbilities().instabuild) stack.shrink(added);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }
}
