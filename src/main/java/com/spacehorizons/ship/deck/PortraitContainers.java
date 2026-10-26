package com.spacehorizons.ship.deck;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.PortraitChange;
import com.spacehorizons.rocket.RocketEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Os baús (e barris, dispensers, caixas de shulker: todo block entity com inventário em grade de 9 colunas) do retrato de uma nave em movimento.
 * O inventário existe num lugar só, o NBT do block entity no retrato da entidade. Abrir um baú cria um contêiner proxy ({@link Proxy}) com o
 * conteúdo desse NBT e abre o menu vanilla sobre ele; cada mudança (e o fechamento) devolve o conteúdo ao retrato no servidor
 * ({@link RocketEntity#changePortrait}), que manda o delta aos clientes. Dois jogadores no mesmo baú recebem o mesmo proxy ({@link SharedProxies}).
 * Ao converter a nave em blocos (estaleiro, âncora, pouso) o NBT vai com o retrato: o proxy não guarda nada que o retrato não tenha.
 */
public final class PortraitContainers {
    /** Chave do proxy: a nave e a posição local do bloco. */
    public record Key(UUID rocket, long pos) {
    }

    private static final SharedProxies<Key, Proxy> OPEN = new SharedProxies<>();

    private PortraitContainers() {
    }

    /** O contêiner proxy: uma cópia de trabalho do inventário, devolvida ao retrato a cada mudança. */
    public static final class Proxy extends SimpleContainer {
        private final Key key;
        private final ServerLevel level;
        private boolean loading = true;

        Proxy(Key key, ServerLevel level, int size) {
            super(size);
            this.key = key;
            this.level = level;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!this.loading) {
                flush(this.level, this.key, this);
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return player instanceof ServerPlayer sp && reachable(sp, this.level, this.key);
        }

        @Override
        public void stopOpen(ContainerUser user) {
            super.stopOpen(user);
            OPEN.release(this.key);
        }

        public Key key() {
            return this.key;
        }
    }

    /** Linhas do menu para um inventário de {@code size} slots, ou 0 se o block entity não tem inventário em grade que o menu de baú sirva. */
    public static int rowsFor(int size) {
        return size >= 9 && size <= 54 && size % 9 == 0 ? size / 9 : 0;
    }

    /** O block entity do NBT é um contêiner de grade que o proxy sabe abrir? */
    public static boolean supports(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
        if (nbt == null || !state.hasBlockEntity()) {
            return false;
        }
        BlockEntity be = loadEntity(level, pos, state, nbt);
        return be instanceof Container c && rowsFor(c.getContainerSize()) > 0;
    }

    private static @Nullable BlockEntity loadEntity(ServerLevel level, BlockPos pos, BlockState state, CompoundTag nbt) {
        try {
            return BlockEntity.loadStatic(pos, state, nbt, level.registryAccess());
        } catch (RuntimeException e) {
            SpaceHorizons.LOGGER.warn("[deck] NBT de block entity ilegível em {}: {}", pos, e.toString());
            return null;
        }
    }

    /** Abre o menu do baú {@code pos} da nave para o jogador. Devolve se abriu. */
    public static boolean open(ServerPlayer player, RocketEntity rocket, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockState state = rocket.portrait().get(pos);
        CompoundTag nbt = rocket.portrait().blockEntities().get(pos.asLong());
        if (!supports(level, pos, state, nbt)) {
            return false;
        }
        Key key = new Key(rocket.getUUID(), pos.asLong());
        Proxy proxy = OPEN.acquire(key, () -> load(level, key, pos, state, nbt));
        int rows = rowsFor(proxy.getContainerSize());
        MenuType<ChestMenu> type = menuType(rows);
        boolean opened = player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(type, id, inventory, proxy, rows), state.getBlock().getName()))
                .isPresent();
        if (!opened) {
            OPEN.release(key);
        } else {
            SpaceHorizons.LOGGER.info("[deck] {} abriu o {} {} da nave {} ({} slots, {} espectador(es))", player.getPlainTextName(), state.getBlock().getName().getString(),
                    pos, rocket.getId(), proxy.getContainerSize(), OPEN.viewers(key));
        }
        return opened;
    }

    private static Proxy load(ServerLevel level, Key key, BlockPos pos, BlockState state, CompoundTag nbt) {
        BlockEntity be = loadEntity(level, pos, state, nbt);
        Container source = (Container) be;
        Proxy proxy = new Proxy(key, level, source.getContainerSize());
        for (int i = 0; i < source.getContainerSize(); i++) {
            proxy.setItem(i, source.getItem(i).copy());
        }
        proxy.loading = false;
        return proxy;
    }

    private static MenuType<ChestMenu> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    /** O conteúdo do proxy volta ao NBT do retrato (a partir do NBT que o retrato tem agora: o nome, a chave e o resto ficam). */
    static void flush(ServerLevel level, Key key, Container contents) {
        if (!(level.getEntity(key.rocket()) instanceof RocketEntity rocket) || rocket.isRemoved()) {
            return;
        }
        BlockPos pos = BlockPos.of(key.pos());
        CompoundTag current = rocket.portrait().blockEntities().get(key.pos());
        if (current == null) {
            return;
        }
        BlockEntity be = loadEntity(level, pos, rocket.portrait().get(pos), current);
        if (!(be instanceof Container target) || target.getContainerSize() != contents.getContainerSize()) {
            return;
        }
        for (int i = 0; i < contents.getContainerSize(); i++) {
            ItemStack stack = contents.getItem(i);
            target.setItem(i, stack.copy());
        }
        CompoundTag out = be.saveWithFullMetadata(level.registryAccess());
        out.remove("x");
        out.remove("y");
        out.remove("z");
        rocket.changePortrait(List.of(PortraitChange.nbt(pos, out)));
    }

    /** O jogador ainda alcança o baú (no convés, pela posição local aceita; de fora, pela pose da nave agora). */
    static boolean reachable(ServerPlayer player, ServerLevel level, Key key) {
        if (!(level.getEntity(key.rocket()) instanceof RocketEntity rocket) || rocket.isRemoved() || player.level() != level) {
            return false;
        }
        BlockPos pos = BlockPos.of(key.pos());
        Vector3d eye = eyeLocal(player, rocket);
        double dx = eye.x - (pos.getX() + 0.5);
        double dy = eye.y - (pos.getY() + 0.5);
        double dz = eye.z - (pos.getZ() + 0.5);
        double reach = player.blockInteractionRange() + 4.0;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /** O olho do jogador em coordenadas locais da nave: a posição aceita se está no convés; senão, a do mundo pela pose da nave. */
    public static Vector3d eyeLocal(ServerPlayer player, RocketEntity rocket) {
        DeckCrew.Member member = DeckCrew.member(player.getUUID());
        if (member != null && member.rocketUuid().equals(rocket.getUUID())) {
            DeckState s = member.state();
            return new Vector3d(s.pos.x, s.pos.y + player.getEyeHeight(), s.pos.z);
        }
        Vec3 eye = player.getEyePosition();
        return rocket.deckFrame().toLocal(eye.x, eye.y, eye.z);
    }

    /** Quantos proxies estão abertos (diagnóstico do autoteste). */
    public static int openCount() {
        return OPEN.size();
    }

    public static void reset() {
        OPEN.clear();
    }
}
