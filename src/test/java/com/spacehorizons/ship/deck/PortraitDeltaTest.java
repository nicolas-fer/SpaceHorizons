package com.spacehorizons.ship.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.PortraitChange;
import com.spacehorizons.rocket.PortraitGrid;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;

/** O delta do retrato: a mudança vai e volta pela rede sem perder o NBT, e a grade é copiada (o retrato antigo não muda). */
class PortraitDeltaTest {
    @Test
    void mudancaDeNbtVaiEVoltaPelaRede() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:chest");
        nbt.putInt("Slot", 7);
        PortraitChange sent = new PortraitChange(123456789L, -1, true, nbt);
        ByteBuf buf = Unpooled.buffer();
        sent.write(buf);
        PortraitChange got = PortraitChange.read(buf);
        assertEquals(sent.pos(), got.pos());
        assertEquals(-1, got.stateId());
        assertTrue(got.setNbt());
        assertEquals(nbt, got.nbt());
        assertEquals(0, buf.readableBytes(), "leu tudo o que foi escrito");
    }

    @Test
    void mudancaDeEstadoSemNbtEMudancaQueRemoveNbt() {
        ByteBuf buf = Unpooled.buffer();
        new PortraitChange(5L, 1234, false, null).write(buf);
        new PortraitChange(6L, -1, true, null).write(buf);
        PortraitChange state = PortraitChange.read(buf);
        PortraitChange removal = PortraitChange.read(buf);
        assertEquals(1234, state.stateId());
        assertFalse(state.setNbt());
        assertNull(state.nbt());
        assertEquals(-1, removal.stateId());
        assertTrue(removal.setNbt());
        assertNull(removal.nbt(), "setNbt sem NBT remove o block entity");
    }

    @Test
    void gradeCopiadaNaoMudaAOriginal() {
        PortraitGrid grid = PortraitGrid.empty(4, 4, 4);
        grid.set(1, 1, 1, 2);
        PortraitGrid changed = grid.with(1, 1, 1, 3);
        assertNotSame(grid, changed);
        assertEquals(2, grid.get(1, 1, 1));
        assertEquals(3, changed.get(1, 1, 1));
        assertEquals(grid.get(0, 0, 0), changed.get(0, 0, 0));
        assertEquals(1, changed.nonEmptyCount());
    }
}
