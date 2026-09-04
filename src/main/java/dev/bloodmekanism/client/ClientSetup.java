package dev.bloodmekanism.client;

import dev.bloodmekanism.registry.ModContent;
import net.minecraft.client.gui.screens.MenuScreens;

public final class ClientSetup {
    private ClientSetup() { }

    public static void registerScreens() {
        MenuScreens.register(ModContent.HEMOGENIC_MENU.get(), HemogenicScreen::new);
        MenuScreens.register(ModContent.UNIVERSAL_FACTORY_MENU.get(), UniversalFactoryScreen::new);
        MenuScreens.register(ModContent.WILL_GENERATOR_MENU.get(), WillGeneratorScreen::new);
        MenuScreens.register(ModContent.LP_CHARGER_MENU.get(), LpChargerScreen::new);
    }
}
