package com.spacehorizons.rocket;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Comando de voo do piloto (cliente para servidor). O cliente manda ao mudar e a cada 5 ticks; o servidor zera comandos
 * sem pacote há 10 ticks. Giros e acelerador vão de -1 a 1 (acelerador de 0 a 1); {@code flags} tem os bits abaixo.
 *
 * @param pitch    arfagem (W/S)
 * @param yaw      guinada (A/D)
 * @param roll     rolagem (Q/E)
 * @param throttle acelerador de 0 a 1
 * @param flags    bits: {@link #FLAG_NEXT_SAS}, {@link #FLAG_PARACHUTE}, {@link #FLAG_DISASSEMBLE}, {@link #FLAG_FULL_THROTTLE},
 *                 {@link #FLAG_CUT_THROTTLE}
 */
public record RocketControlPayload(float pitch, float yaw, float roll, float throttle, byte flags) implements CustomPacketPayload {
    public static final Type<RocketControlPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "rocket_control"));

    public static final StreamCodec<ByteBuf, RocketControlPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RocketControlPayload::pitch,
            ByteBufCodecs.FLOAT, RocketControlPayload::yaw,
            ByteBufCodecs.FLOAT, RocketControlPayload::roll,
            ByteBufCodecs.FLOAT, RocketControlPayload::throttle,
            ByteBufCodecs.BYTE, RocketControlPayload::flags,
            RocketControlPayload::new);

    /** Passa para o próximo modo do SAS (desligado, estabilizar, prógrado, retrógrado). */
    public static final int FLAG_NEXT_SAS = 1;
    public static final int FLAG_PARACHUTE = 1 << 1;
    /** Pedido de desmontar (pousar de volta a blocos), só com o foguete pousado. */
    public static final int FLAG_DISASSEMBLE = 1 << 2;
    public static final int FLAG_FULL_THROTTLE = 1 << 3;
    public static final int FLAG_CUT_THROTTLE = 1 << 4;

    public static final RocketControlPayload NEUTRAL = new RocketControlPayload(0.0F, 0.0F, 0.0F, 0.0F, (byte) 0);

    public boolean has(int flag) {
        return (this.flags & flag) != 0;
    }

    /** Cópia com os números dentro dos limites (o servidor não confia no cliente): NaN vira zero, giros em [-1, 1], acelerador em [0, 1]. */
    public RocketControlPayload sanitized() {
        return new RocketControlPayload(clampUnit(this.pitch), clampUnit(this.yaw), clampUnit(this.roll),
                Math.max(0.0F, Math.min(1.0F, finite(this.throttle))), (byte) (this.flags & 0x1F));
    }

    private static float finite(float v) {
        return Float.isFinite(v) ? v : 0.0F;
    }

    private static float clampUnit(float v) {
        return Math.max(-1.0F, Math.min(1.0F, finite(v)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
