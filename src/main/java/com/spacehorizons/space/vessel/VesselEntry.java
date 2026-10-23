package com.spacehorizons.space.vessel;

import java.util.UUID;

/**
 * O estado virtual de uma nave ou foguete visto de OUTRO veículo (docs/PLANO_NAVES.md, 4.3; fase 2d): onde está, como está virado e para onde anda.
 * É o que o servidor manda aos clientes no espaço e no estaleiro ({@link VesselStatePayload}) e o que o cliente extrapola entre pacotes.
 *
 * @param id id estável do veículo (uuid do foguete; para a nave, {@link #shipUuid})
 * @param kind {@link #SHIP} ou {@link #ROCKET}
 * @param hullVersion versão do casco que o servidor tem para ele (o cliente pede o casco se não tem esta versão)
 * @param radius raio (blocos) da esfera que cobre o veículo: para escolher a distância do casco e o impostor sem esperar o casco
 * @param pos posição absoluta em E (eixos do espaço do SolarSystem) no instante do pacote
 * @param attitude atitude local {@code ql}: quaternion (x, y, z, w) dos blocos do veículo (relativos ao pivô) para os eixos locais da dimensão de espaço;
 *                 o modelo vai ao espaço por {@code LOCAL_TO_SPACE · R(ql)} ({@link VesselPose#modelToSpace})
 * @param velocity velocidade em E (blocos por tick de servidor)
 * @param omega giro (rad por tick de servidor) em torno de um eixo dos eixos locais da dimensão: {@code q(t+dt) = rot(omega·dt) · q(t)}
 */
public record VesselEntry(UUID id, byte kind, int hullVersion, float radius, double[] pos, double[] attitude, double[] velocity, double[] omega) {
    public static final byte SHIP = 0;
    public static final byte ROCKET = 1;

    /** O id do veículo de uma nave do estaleiro (a nave tem inteiro sequencial; o uuid é só uma chave estável). */
    public static UUID shipUuid(int shipId) {
        return new UUID(0x53484950L << 32 | 0x5645L, shipId);
    }

    /** Cor do impostor (sem casco) por tipo: a nave cinza-azulada, o foguete branco-laranja. */
    public static int impostorRgb(byte kind) {
        return kind == SHIP ? 0x8FA3B8 : 0xE8B27A;
    }
}
