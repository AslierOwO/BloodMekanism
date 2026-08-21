package dev.bloodmekanism;

import com.mojang.logging.LogUtils;
import dev.bloodmekanism.client.ClientSetup;
import dev.bloodmekanism.registry.ModContent;
import net.minecraftforge.api.distmarker.Dist;
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
    }

    private void clientSetup(FMLClientSetupEvent event) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
            event.enqueueWork(ClientSetup::registerScreens);
        }
    }
}
