package dev.bloodmekanism.registry;

import dev.bloodmekanism.BloodMekanism;
import dev.bloodmekanism.item.MechanicalBloodOrbItem;
import dev.bloodmekanism.item.MechanicalOrbTier;
import dev.bloodmekanism.machine.FactoryTier;
import dev.bloodmekanism.machine.FactoryMode;
import dev.bloodmekanism.machine.MachineBlock;
import dev.bloodmekanism.machine.MachineBlockItem;
import dev.bloodmekanism.machine.blockentity.HemogenicBlockEntity;
import dev.bloodmekanism.machine.blockentity.LpChargerBlockEntity;
import dev.bloodmekanism.machine.blockentity.UniversalFactoryBlockEntity;
import dev.bloodmekanism.machine.blockentity.WillGeneratorBlockEntity;
import dev.bloodmekanism.menu.HemogenicMenu;
import dev.bloodmekanism.menu.LpChargerMenu;
import dev.bloodmekanism.menu.UniversalFactoryMenu;
import dev.bloodmekanism.menu.WillGeneratorMenu;
import dev.bloodmekanism.recipe.BloodGeneratorRecipe;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BloodMekanism.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BloodMekanism.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BloodMekanism.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, BloodMekanism.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, BloodMekanism.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, BloodMekanism.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BloodMekanism.MOD_ID);
    public static final DeferredRegister<Gas> GASES = DeferredRegister.create(MekanismAPI.GAS_REGISTRY_NAME, BloodMekanism.MOD_ID);

    private static final BlockBehaviour.Properties MACHINE_PROPERTIES = BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(4.0F, 8.0F);

    public static final RegistryObject<Block> HEMOGENIC_MACHINE = BLOCKS.register("hemogenic_machine",
          () -> new MachineBlock(MACHINE_PROPERTIES, MachineBlock.Kind.HEMOGENIC, null));
    public static final RegistryObject<Block> WILL_GENERATOR = BLOCKS.register("will_generator",
          () -> new MachineBlock(MACHINE_PROPERTIES, MachineBlock.Kind.WILL_GENERATOR, null));
    public static final RegistryObject<Block> LP_CHARGER = BLOCKS.register("lp_charger",
          () -> new MachineBlock(MACHINE_PROPERTIES, MachineBlock.Kind.LP_CHARGER, null));
    public static final RegistryObject<Gas> RAW_WILL = GASES.register("raw_will", () -> new Gas(GasBuilder.builder().tint(0x8A2BE2)));
    public static final RegistryObject<Gas> CORROSIVE_WILL = GASES.register("corrosive_will", () -> new Gas(GasBuilder.builder().tint(0x55B84D)));
    public static final RegistryObject<Gas> DESTRUCTIVE_WILL = GASES.register("destructive_will", () -> new Gas(GasBuilder.builder().tint(0xD94B45)));
    public static final RegistryObject<Gas> VENGEFUL_WILL = GASES.register("vengeful_will", () -> new Gas(GasBuilder.builder().tint(0xE08B32)));
    public static final RegistryObject<Gas> STEADFAST_WILL = GASES.register("steadfast_will", () -> new Gas(GasBuilder.builder().tint(0x4B78C2)));
    public static final Map<FactoryTier, RegistryObject<Block>> UNIVERSAL_FACTORIES = new EnumMap<>(FactoryTier.class);
    public static final Map<FactoryMode, RegistryObject<Block>> PROCESS_FACTORIES = new EnumMap<>(FactoryMode.class);
    public static final Map<MechanicalOrbTier, RegistryObject<Item>> MECHANICAL_BLOOD_ORBS = new EnumMap<>(MechanicalOrbTier.class);

    static {
        for (FactoryTier tier : FactoryTier.values()) {
            String name = tier.name().toLowerCase() + "_blood_factory";
            UNIVERSAL_FACTORIES.put(tier, BLOCKS.register(name,
                  () -> new MachineBlock(MACHINE_PROPERTIES, MachineBlock.Kind.UNIVERSAL_FACTORY, tier, FactoryMode.ALTAR)));
        }
        for (MechanicalOrbTier tier : MechanicalOrbTier.values()) {
            String name = "mechanical_" + tier.name().toLowerCase() + "_blood_orb";
            MECHANICAL_BLOOD_ORBS.put(tier, ITEMS.register(name, () -> new MechanicalBloodOrbItem(tier)));
        }
        PROCESS_FACTORIES.put(FactoryMode.ALCHEMY_TABLE, BLOCKS.register("industrial_alchemy_factory",
              () -> new MachineBlock(MACHINE_PROPERTIES, MachineBlock.Kind.UNIVERSAL_FACTORY, FactoryTier.ABSOLUTE, FactoryMode.ALCHEMY_TABLE)));
        PROCESS_FACTORIES.put(FactoryMode.ALCHEMY_ARRAY, BLOCKS.register("industrial_array_factory",
              () -> new MachineBlock(MACHINE_PROPERTIES, MachineBlock.Kind.UNIVERSAL_FACTORY, FactoryTier.ABSOLUTE, FactoryMode.ALCHEMY_ARRAY)));
        PROCESS_FACTORIES.put(FactoryMode.SOUL_FORGE, BLOCKS.register("industrial_soul_forge",
              () -> new MachineBlock(MACHINE_PROPERTIES, MachineBlock.Kind.UNIVERSAL_FACTORY, FactoryTier.ABSOLUTE, FactoryMode.SOUL_FORGE)));
        PROCESS_FACTORIES.put(FactoryMode.ARC, BLOCKS.register("industrial_arc_factory",
              () -> new MachineBlock(MACHINE_PROPERTIES, MachineBlock.Kind.UNIVERSAL_FACTORY, FactoryTier.ABSOLUTE, FactoryMode.ARC)));
    }

    public static final RegistryObject<BlockEntityType<HemogenicBlockEntity>> HEMOGENIC_BLOCK_ENTITY = BLOCK_ENTITIES.register("hemogenic_machine",
          () -> BlockEntityType.Builder.of(HemogenicBlockEntity::new, HEMOGENIC_MACHINE.get()).build(null));
    public static final RegistryObject<BlockEntityType<WillGeneratorBlockEntity>> WILL_GENERATOR_BLOCK_ENTITY = BLOCK_ENTITIES.register("will_generator",
          () -> BlockEntityType.Builder.of(WillGeneratorBlockEntity::new, WILL_GENERATOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<LpChargerBlockEntity>> LP_CHARGER_BLOCK_ENTITY = BLOCK_ENTITIES.register("lp_charger",
          () -> BlockEntityType.Builder.of(LpChargerBlockEntity::new, LP_CHARGER.get()).build(null));
    public static final RegistryObject<BlockEntityType<UniversalFactoryBlockEntity>> UNIVERSAL_FACTORY_BLOCK_ENTITY = BLOCK_ENTITIES.register("universal_blood_factory",
          () -> BlockEntityType.Builder.of(UniversalFactoryBlockEntity::new,
                java.util.stream.Stream.concat(UNIVERSAL_FACTORIES.values().stream(), PROCESS_FACTORIES.values().stream())
                      .map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static final RegistryObject<MenuType<HemogenicMenu>> HEMOGENIC_MENU = MENUS.register("hemogenic_machine",
          () -> IForgeMenuType.create(HemogenicMenu::clientConstructor));
    public static final RegistryObject<MenuType<WillGeneratorMenu>> WILL_GENERATOR_MENU = MENUS.register("will_generator",
          () -> IForgeMenuType.create(WillGeneratorMenu::clientConstructor));
    public static final RegistryObject<MenuType<LpChargerMenu>> LP_CHARGER_MENU = MENUS.register("lp_charger",
          () -> IForgeMenuType.create(LpChargerMenu::clientConstructor));
    public static final RegistryObject<MenuType<UniversalFactoryMenu>> UNIVERSAL_FACTORY_MENU = MENUS.register("universal_blood_factory",
          () -> IForgeMenuType.create(UniversalFactoryMenu::clientConstructor));

    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeType<BloodGeneratorRecipe>> BLOOD_GENERATOR_RECIPE_TYPE = RECIPE_TYPES.register("blood_generator", () -> new net.minecraft.world.item.crafting.RecipeType<>() {
        @Override
        public String toString() { return BloodMekanism.MOD_ID + ":blood_generator"; }
    });
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<BloodGeneratorRecipe>> BLOOD_GENERATOR_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register("blood_generator", BloodGeneratorRecipe.Serializer::new);

    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = TABS.register("main", () -> CreativeModeTab.builder()
          .title(Component.translatable("itemGroup.bloodmekanism"))
          .icon(() -> new ItemStack(UNIVERSAL_FACTORIES.get(FactoryTier.BASIC).get()))
          .displayItems((parameters, output) -> {
              output.accept(HEMOGENIC_MACHINE.get());
              output.accept(WILL_GENERATOR.get());
              output.accept(LP_CHARGER.get());
              UNIVERSAL_FACTORIES.values().forEach(block -> output.accept(block.get()));
              PROCESS_FACTORIES.values().forEach(block -> output.accept(block.get()));
              MECHANICAL_BLOOD_ORBS.values().forEach(item -> output.accept(item.get()));
          }).build());

    private ModContent() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        RECIPE_TYPES.register(bus);
        RECIPE_SERIALIZERS.register(bus);
        TABS.register(bus);
        GASES.register(bus);

        ITEMS.register("hemogenic_machine", () -> new MachineBlockItem((MachineBlock) HEMOGENIC_MACHINE.get(), new Item.Properties()));
        ITEMS.register("will_generator", () -> new MachineBlockItem((MachineBlock) WILL_GENERATOR.get(), new Item.Properties()));
        ITEMS.register("lp_charger", () -> new MachineBlockItem((MachineBlock) LP_CHARGER.get(), new Item.Properties()));
        for (Map.Entry<FactoryTier, RegistryObject<Block>> entry : UNIVERSAL_FACTORIES.entrySet()) {
            String name = entry.getKey().name().toLowerCase() + "_blood_factory";
            ITEMS.register(name, () -> new MachineBlockItem((MachineBlock) entry.getValue().get(), new Item.Properties()));
        }
        for (Map.Entry<FactoryMode, RegistryObject<Block>> entry : PROCESS_FACTORIES.entrySet()) {
            String name = switch (entry.getKey()) {
                case ALCHEMY_TABLE -> "industrial_alchemy_factory";
                case ALCHEMY_ARRAY -> "industrial_array_factory";
                case SOUL_FORGE -> "industrial_soul_forge";
                case ARC -> "industrial_arc_factory";
                default -> throw new IllegalStateException("Unexpected dedicated mode: " + entry.getKey());
            };
            ITEMS.register(name, () -> new MachineBlockItem((MachineBlock) entry.getValue().get(), new Item.Properties()));
        }
    }
}
