package com.spacehorizons.survival.compressor;

import com.spacehorizons.content.ModBlockEntities;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.survival.fuel.RefineryBlockEntity;
import com.spacehorizons.survival.oxygen.OxygenStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import org.jspecify.annotations.Nullable;

/**
 * Block entity do compressor de oxigênio. Dois slots (tanque e combustível de fornalha); a decisão de trabalhar, a
 * taxa e a queima ficam em {@link CompressorLogic} (testável sem o jogo). Aqui só se liga a lógica aos slots, ao
 * planeta do level (ar respirável ou vácuo) e ao estado aceso do bloco.
 *
 * O ar vai direto para o item do slot do tanque, pelo contrato de {@link OxygenStorage} (qualquer item que guarde
 * oxigênio serve). Nada fica guardado no bloco: ao quebrar, o tanque e o combustível caem como itens.
 *
 * Automação (funil, etc.): é um {@link WorldlyContainer}, exposto como capability de item em {@link CompressorSetup}.
 * Por cima entra só o tanque ainda com espaço; pelos lados entram combustível de fornalha e tanque (ainda com
 * espaço); por baixo saem só o tanque cheio (e o resto do combustível, como o balde vazio da lava) e nada entra.
 * Nada sai por cima nem pelos lados, como na fornalha.
 */
public class OxygenCompressorBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOT_TANK = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_COUNT = 2;

    /** Índices do {@link ContainerData} sincronizado com a tela. */
    public static final int DATA_LIT_TIME = 0;
    public static final int DATA_LIT_TOTAL = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_COUNT = 3;

    /** Enquanto comprime, o bloco só é marcado para salvar a cada tantos ticks (o ar entra todo tick). */
    private static final int SAVE_INTERVAL_TICKS = 20;

    private static final Component DEFAULT_NAME = Component.translatable("container.spacehorizons.oxygen_compressor");
    private static final int[] SLOTS_UP = {SLOT_TANK};
    /** Por baixo saem o tanque cheio e o resto do combustível (balde vazio da lava), como o slot 1 da fornalha. */
    private static final int[] SLOTS_DOWN = {SLOT_TANK, SLOT_FUEL};
    private static final int[] SLOTS_SIDES = {SLOT_FUEL, SLOT_TANK};

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final CompressorLogic logic = new CompressorLogic();

    /** Tudo cabe em short (tempos de queima limitados), que é o que o menu sincroniza. */
    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_LIT_TIME -> scaleToShort(logic.litTime(), logic.litTotal());
                case DATA_LIT_TOTAL -> Math.min(logic.litTotal(), Short.MAX_VALUE);
                case DATA_STATUS -> logic.status().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // só o cliente (menu) escreve aqui; o servidor é a fonte da verdade
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public OxygenCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OXYGEN_COMPRESSOR.get(), pos, state);
    }

    /** Combustíveis de queima muito longa passam de short; mantém a proporção para a chama. */
    private static int scaleToShort(int litTime, int litTotal) {
        if (litTotal > Short.MAX_VALUE) {
            return (int) Math.floor((double) litTime / litTotal * Short.MAX_VALUE);
        }
        return litTime;
    }

    public CompressorLogic logic() {
        return logic;
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        logic.load(input.getIntOr("lit_time", 0), input.getIntOr("lit_total", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("lit_time", logic.litTime());
        output.putInt("lit_total", logic.litTotal());
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, OxygenCompressorBlockEntity entity) {
        ItemStack tank = entity.items.get(SLOT_TANK);
        ItemStack fuel = entity.items.get(SLOT_FUEL);

        // o espaço aberto é vácuo em qualquer ponto (a lógica pura só conhece planetas)
        boolean breathable = !SpaceDimension.is(level) && CompressorLogic.isBreathable(Planets.forLevel(level), pos.getY());
        // o tempo de queima só é lido quando o compressor precisa (re)acender
        int burn = entity.logic.litTime() <= 0 && !fuel.isEmpty() ? entity.getBurnDuration(level, fuel) : 0;
        CompressorLogic.Result result = entity.logic.tick(new CompressorLogic.Inputs(breathable,
                OxygenStorage.isContainer(tank), OxygenStorage.get(tank), OxygenStorage.capacity(tank), burn));

        boolean changed = result.changed();
        if (result.consumeFuelItem()) {
            consumeFuel(level, pos, entity.items, fuel);
        }
        if (result.fill() > 0) {
            OxygenStorage.insert(tank, result.fill());
            changed |= level.getGameTime() % SAVE_INTERVAL_TICKS == 0;
        }

        if (state.getValue(OxygenCompressorBlock.LIT) != result.lit()) {
            state = state.setValue(OxygenCompressorBlock.LIT, result.lit());
            level.setBlockAndUpdate(pos, state);
        }
        if (changed) {
            setChanged(level, pos, state);
        }
    }

    /** Queima um item de combustível de fornalha, devolvendo o resto (balde vazio da lava) como a fornalha faz. */
    private static void consumeFuel(ServerLevel level, BlockPos pos, NonNullList<ItemStack> items, ItemStack fuel) {
        ItemStackTemplate remainder = fuel.getCraftingRemainder();
        ItemStack newFuel = fuel;
        fuel.shrink(1);
        if (remainder != null) {
            if (fuel.isEmpty()) {
                newFuel = remainder.create();
            } else {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder.create());
            }
        }
        items.set(SLOT_FUEL, newFuel);
    }

    private int getBurnDuration(ServerLevel level, ItemStack fuelItem) {
        return ResolvableInt.getFromItem(fuelItem, DataComponents.COOKING_FUEL, CookingFuel::burnTime,
                getLootContext(level, fuelItem), 0);
    }

    /** Item que serve de combustível de fornalha (mesma regra da refinaria e da fornalha). */
    public static boolean isFurnaceFuel(ItemStack stack) {
        return RefineryBlockEntity.isFurnaceFuel(stack);
    }

    /** Tanque cheio (ou item que não guarda oxigênio: nada a esperar dele). */
    private static boolean isFullOrNotATank(ItemStack stack) {
        return !OxygenStorage.isContainer(stack) || OxygenStorage.get(stack) >= OxygenStorage.capacity(stack);
    }

    // ---- Container ----

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CompressorMenu(containerId, inventory, this, dataAccess);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_TANK -> OxygenStorage.isContainer(stack);
            default -> isFurnaceFuel(stack) || stack.is(Items.BUCKET) && !items.get(SLOT_FUEL).is(Items.BUCKET);
        };
    }

    // ---- WorldlyContainer (funil e automação) ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case UP -> SLOTS_UP;
            case DOWN -> SLOTS_DOWN;
            default -> SLOTS_SIDES;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        // por baixo só saem itens
        if (side == Direction.DOWN) {
            return false;
        }
        // pela automação só entra tanque que ainda tem o que encher (um cheio só ocuparia o slot)
        return slot == SLOT_TANK ? canPlaceItem(slot, stack) && !isFullOrNotATank(stack) : canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // como na fornalha, nada sai por cima nem pelos lados
        if (side != Direction.DOWN) {
            return false;
        }
        return switch (slot) {
            // do tanque: só o cheio (o que ainda enche fica na máquina)
            case SLOT_TANK -> isFullOrNotATank(stack);
            // do combustível: só o resto (balde vazio), nunca o combustível
            default -> !isFurnaceFuel(stack);
        };
    }
}
