package com.spacehorizons.survival.fuel;

import com.spacehorizons.content.ModBlockEntities;
import com.spacehorizons.content.ModRecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import org.jspecify.annotations.Nullable;

/**
 * Block entity da refinaria. Três slots (entrada, combustível de fornalha, galão) e um tanque interno em mB; a lógica
 * de tanque, queima e progresso fica em {@link RefineryLogic} (testável sem o jogo). Aqui só se liga a lógica aos
 * slots, às receitas ({@code spacehorizons:refining}, pelo RecipeManager) e ao estado do bloco.
 *
 * Automação (funil, etc.): é um {@link WorldlyContainer}, exposto como capability de item em {@link RefinerySetup}.
 * Por cima entra a entrada (só itens com receita); pelos lados, combustível de fornalha e galões; por baixo, saem só
 * galões cheios.
 */
public class RefineryBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_CANISTER = 2;
    public static final int SLOT_COUNT = 3;

    /** Índices do {@link ContainerData} sincronizado com a tela. */
    public static final int DATA_LIT_TIME = 0;
    public static final int DATA_LIT_TOTAL = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_PROCESS_TOTAL = 3;
    public static final int DATA_TANK = 4;
    public static final int DATA_COUNT = 5;

    private static final Component DEFAULT_NAME = Component.translatable("container.spacehorizons.refinery");
    private static final int[] SLOTS_UP = {SLOT_INPUT};
    private static final int[] SLOTS_DOWN = {SLOT_CANISTER};
    private static final int[] SLOTS_SIDES = {SLOT_FUEL, SLOT_CANISTER};

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final RefineryLogic logic = new RefineryLogic();
    private final RecipeManager.CachedCheck<SingleRecipeInput, RefiningRecipe> quickCheck =
            RecipeManager.createCheck(ModRecipes.REFINING.get());

    /** Tudo cabe em short (tanque até 4000, tempos de queima limitados), que é o que o menu sincroniza. */
    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_LIT_TIME -> scaleToShort(logic.litTime(), logic.litTotal());
                case DATA_LIT_TOTAL -> Math.min(logic.litTotal(), Short.MAX_VALUE);
                case DATA_PROGRESS -> logic.progress();
                case DATA_PROCESS_TOTAL -> logic.processTotal();
                case DATA_TANK -> logic.tank();
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

    public RefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REFINERY.get(), pos, state);
    }

    /** Combustíveis de queima muito longa (lava, bloco de carvão) passam de short; mantém a proporção para a chama. */
    private static int scaleToShort(int litTime, int litTotal) {
        if (litTotal > Short.MAX_VALUE) {
            return (int) Math.floor((double) litTime / litTotal * Short.MAX_VALUE);
        }
        return litTime;
    }

    public RefineryLogic logic() {
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
        logic.load(input.getIntOr("tank_mb", 0), input.getIntOr("lit_time", 0), input.getIntOr("lit_total", 0),
                input.getIntOr("progress", 0), input.getIntOr("process_total", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("tank_mb", logic.tank());
        output.putInt("lit_time", logic.litTime());
        output.putInt("lit_total", logic.litTotal());
        output.putInt("progress", logic.progress());
        output.putInt("process_total", logic.processTotal());
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, RefineryBlockEntity entity) {
        boolean changed = false;
        boolean wasLit = entity.logic.isLit();

        ItemStack input = entity.items.get(SLOT_INPUT);
        ItemStack fuel = entity.items.get(SLOT_FUEL);
        RefiningRecipe recipe = null;
        if (!input.isEmpty()) {
            recipe = entity.quickCheck.getRecipeFor(new SingleRecipeInput(input), level).map(RecipeHolder::value).orElse(null);
        }
        // o tempo de queima só é lido quando a refinaria precisa (re)acender
        int burn = entity.logic.litTime() <= 1 && !fuel.isEmpty() ? entity.getBurnDuration(level, fuel) : 0;
        RefineryLogic.Inputs inputs = recipe == null ? new RefineryLogic.Inputs(false, 0, 0, burn)
                : new RefineryLogic.Inputs(true, recipe.fuelMb(), recipe.time(), burn);

        RefineryLogic.Result result = entity.logic.tick(inputs);
        if (result.consumeFuelItem()) {
            consumeFuel(level, pos, entity.items, fuel);
        }
        if (result.consumeInputItem()) {
            input.shrink(1);
        }
        changed |= result.changed();

        // esvazia o tanque no galão do slot 2
        ItemStack canister = entity.items.get(SLOT_CANISTER);
        if (canister.getItem() instanceof FuelCanisterItem && entity.logic.tank() > 0) {
            int moved = entity.logic.transferToCanister(FuelCanisterItem.getFuel(canister));
            if (moved > 0) {
                FuelCanisterItem.setFuel(canister, FuelCanisterItem.getFuel(canister) + moved);
                changed = true;
            }
        }

        if (wasLit != result.lit()) {
            state = state.setValue(RefineryBlock.LIT, result.lit());
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

    /** Item que serve de combustível de fornalha (mesma regra da fornalha, que usa o componente cooking_fuel). */
    public static boolean isFurnaceFuel(ItemStack stack) {
        return stack.has(DataComponents.COOKING_FUEL);
    }

    // ---- Container ----

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new RefineryMenu(containerId, inventory, this, dataAccess);
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
            case SLOT_FUEL -> isFurnaceFuel(stack) || stack.is(net.minecraft.world.item.Items.BUCKET)
                    && !items.get(SLOT_FUEL).is(net.minecraft.world.item.Items.BUCKET);
            case SLOT_CANISTER -> stack.getItem() instanceof FuelCanisterItem;
            default -> hasRefiningRecipe(stack);
        };
    }

    /** Há receita de refino para o item? Só no servidor (o cliente não conhece o RecipeManager de mods). */
    private boolean hasRefiningRecipe(ItemStack stack) {
        if (level instanceof ServerLevel serverLevel) {
            return quickCheck.getRecipeFor(new SingleRecipeInput(stack), serverLevel).isPresent();
        }
        return true;
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
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return switch (slot) {
            // do galão: só o que está cheio (o vazio fica para receber combustível); de outro item, qualquer um
            case SLOT_CANISTER -> !(stack.getItem() instanceof FuelCanisterItem)
                    || FuelCanisterItem.getFuel(stack) >= CanisterFuel.CAPACITY;
            // do combustível de fornalha: só o resto (balde vazio), nunca o combustível
            case SLOT_FUEL -> !isFurnaceFuel(stack);
            default -> true;
        };
    }
}
