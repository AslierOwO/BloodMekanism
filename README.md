# BloodMekanism

BloodMekanism is a Forge 1.20.1 addon that connects Blood Magic processing to Mekanism-style machines and automation.

Current target versions:

- Minecraft 1.20.1
- Forge 47.2.0
- Java 17
- Mekanism 10.4+
- Blood Magic 3.3+

## Features

- Five altar factories covering Blood Altar tiers 1-5, with batch sizes from 1 to 16.
- Automatic multi-tier Slate processing with a selectable final Slate target and internal intermediate storage.
- Industrial Alchemy Table and Potion Flask processing with count-aware repeated ingredients.
- Industrial Alchemy Array, Soul Forge, and ARC processing factories.
- Hemogenic Machine for automated Life Essence production from rotten flesh and raw meat.
- Mechanical Blood Orbs that store LP locally without binding automation to a player UUID.
- Five Mekanism Demon Will gases, a Demon Will Generator, Pressurized Tube transport, and gas-powered Soul Forge processing.
- Independent six-side item, fluid, gas, and energy configuration.
- Mekanism Speed and Energy Upgrades, redstone control, and automatic output.
- Forge item/fluid/energy capabilities and Mekanism gas capabilities for AE2 and pipe-based automation.
- English and Simplified Chinese localization.

Mekanism's Formulaic Assemblicator already supports Blood Magic's custom Blood Orb crafting ingredients and crafting remainders, so ritual stones and hydration cells do not require a duplicate assembler in this addon.

## Development Build

The current development build expects the three source projects to be sibling directories:

```text
workspace/
  BloodMagic/
  Mekanism/
  BloodMekanism/
```

Build the Mekanism development JAR and Blood Magic classes first. Then, from the workspace directory on Windows:

```powershell
.\BloodMagic\gradlew.bat -p BloodMekanism build --no-daemon
```

The development JAR is written to:

```text
BloodMekanism/build/libs/bloodmekanism-0.1.0-dev.jar
```

This repository is under active development. World-interaction systems such as ritual construction, Living Armour training, meteor placement, and Demon Realm generation are outside the scope of ordinary machine recipes and are not currently automated.
