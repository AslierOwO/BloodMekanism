# BloodMekanism

## 中文简介

BloodMekanism 是一个将 Blood Magic 血魔法处理流程接入 Mekanism 工业自动化体系的 Forge 1.20.1 附属模组。它提供一级至五级血祭坛工厂、工业炼金工厂、工业炼金阵工厂、工业灵魂锻炉和工业 ARC 工厂，让血祭坛、炼金台、炼金阵、灵魂锻炉与 ARC 配方可以通过 FE 电力、物流运输器、机械管道、加压管道及 AE2 网络连续运行。

模组还加入了血液发生器、机械血之宝珠、LP 充能器、恶魔意志五种气体和恶魔意志发生器，并支持 Mekanism 速度/能量升级、红石控制、六面独立输入输出配置，以及物品、流体、能量和气体能力接口。你可以从腐肉和生肉开始生产生命源质，再将材料和资源分配到各类血魔法工厂，建立完整的魔法工业产线。

当前版本重点覆盖 Blood Magic 的物品处理型配方；仪式建造与激活、活体盔甲训练、陨石放置、恶魔领域生成等需要世界交互或特殊状态的系统暂未自动化。模组提供英文和简体中文本地化。

## 中文使用要求

- Minecraft 1.20.1
- Forge 47.2.0
- Java 17（64 位）
- Mekanism 10.4.16
- Blood Magic 3.3.8-50

BloodMekanism is a Forge 1.20.1 addon that connects Blood Magic processing to Mekanism-style machines and automation.

Current target versions:

- Minecraft 1.20.1
- Forge 47.2.0
- Java 17
- Mekanism 10.4.16
- Blood Magic 3.3.8-50

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
BloodMekanism/build/libs/bloodmekanism-0.1.1-dev.jar
```

This repository is under active development. World-interaction systems such as ritual construction, Living Armour training, meteor placement, and Demon Realm generation are outside the scope of ordinary machine recipes and are not currently automated.
