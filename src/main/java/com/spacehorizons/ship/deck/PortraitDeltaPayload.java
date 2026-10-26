package com.spacehorizons.ship.deck;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.PortraitChange;
import com.spacehorizons.rocket.RocketAssembly;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * O delta do retrato (servidor para cliente): as mudanças que o servidor aplicou ao retrato da entidade (porta aberta, conteúdo de baú) no lugar
 * do retrato inteiro, que numa nave grande tem megabytes. Leva o id do retrato de base (o cliente só aplica sobre o retrato que o servidor
 * mudou; se ele ainda não tem esse, guarda o delta) e o id do retrato novo (a chave do cache de malha).
 *
 * @param rocketId id da entidade da nave
 * @param baseId id do retrato a que as mudanças se aplicam
 * @param newId id do retrato depois delas
 */
public record PortraitDeltaPayload(int rocketId, UUID baseId, UUID newId, List<PortraitChange> changes) implements CustomPacketPayload {
    public static final Type<PortraitDeltaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "portrait_delta"));

    public static final StreamCodec<ByteBuf, PortraitDeltaPayload> CODEC = StreamCodec.of((buf, p) -> p.write(new FriendlyByteBuf(buf)),
            buf -> read(new FriendlyByteBuf(buf)));

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.rocketId);
        buf.writeUUID(this.baseId);
        buf.writeUUID(this.newId);
        buf.writeVarInt(this.changes.size());
        for (PortraitChange change : this.changes) {
            change.write(buf);
        }
    }

    private static PortraitDeltaPayload read(FriendlyByteBuf buf) {
        int rocketId = buf.readVarInt();
        UUID baseId = buf.readUUID();
        UUID newId = buf.readUUID();
        int count = buf.readVarInt();
        if (count < 0 || count > RocketAssembly.MAX_BLOCKS) {
            throw new IllegalArgumentException("delta de retrato grande demais: " + count);
        }
        List<PortraitChange> changes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            changes.add(PortraitChange.read(buf));
        }
        return new PortraitDeltaPayload(rocketId, baseId, newId, List.copyOf(changes));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
