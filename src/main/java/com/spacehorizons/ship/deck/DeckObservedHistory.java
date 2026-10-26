package com.spacehorizons.ship.deck;

import java.util.ArrayDeque;

import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * A história do estado local de UM tripulante visto por um observador: os estados que o servidor mandou, cada um com o tick do servidor em que valeu.
 * O observador lê o estado num instante fracionário do tempo do servidor (o mesmo {@link DeckPoseTimeline#serverTimeAt} do casco), interpolando
 * linearmente a posição e o olhar entre dois ticks; nas pontas fica o estado da ponta (nunca extrapola). Lógica pura, testada sem o jogo.
 */
public final class DeckObservedHistory {
    private static final int CAPACITY = 32;

    private record Sample(int stamp, double x, double y, double z, float yaw, float pitch, boolean onGround) {
    }

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private int rocketId = -1;
    private double lastArrival = Double.NEGATIVE_INFINITY;

    /** Chegou um estado: o tick do servidor, o estado e o tempo do cliente (ticks) da chegada. */
    public void record(int rocketId, int stamp, DeckState s, double arrivalTick) {
        if (rocketId != this.rocketId) {
            this.samples.clear();
            this.rocketId = rocketId;
        }
        Sample last = this.samples.peekLast();
        if (last != null && stamp <= last.stamp) {
            return;
        }
        this.lastArrival = arrivalTick;
        this.samples.addLast(new Sample(stamp, s.pos.x, s.pos.y, s.pos.z, s.yaw, s.pitch, s.onGround));
        while (this.samples.size() > CAPACITY) {
            this.samples.removeFirst();
        }
    }

    public int rocketId() {
        return this.rocketId;
    }

    public int size() {
        return this.samples.size();
    }

    public void clear() {
        this.samples.clear();
        this.rocketId = -1;
        this.lastArrival = Double.NEGATIVE_INFINITY;
    }

    /** Há estados e o último chegou há no máximo {@code staleTicks} (em relação ao tempo do cliente {@code nowTick}). */
    public boolean fresh(double nowTick, double staleTicks) {
        return !this.samples.isEmpty() && nowTick - this.lastArrival <= staleTicks;
    }

    /** O estado no tempo do servidor {@code serverTime} (fracionário); null se vazia. A velocidade fica zerada. */
    public @Nullable DeckState sample(double serverTime) {
        if (this.samples.isEmpty()) {
            return null;
        }
        Sample first = this.samples.peekFirst();
        Sample last = this.samples.peekLast();
        Sample a = first;
        Sample b = first;
        if (serverTime >= last.stamp) {
            a = last;
            b = last;
        } else if (serverTime > first.stamp) {
            Sample previous = null;
            for (Sample s : this.samples) {
                if (previous != null && serverTime < s.stamp) {
                    a = previous;
                    b = s;
                    break;
                }
                previous = s;
            }
        }
        double t = a == b ? 0.0 : Math.max(0.0, Math.min(1.0, (serverTime - a.stamp) / (b.stamp - a.stamp)));
        DeckState out = new DeckState();
        out.pos.set(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
        out.yaw = (float) (a.yaw + wrap(b.yaw - a.yaw) * t);
        out.pitch = (float) (a.pitch + (b.pitch - a.pitch) * t);
        // o chão não se interpola: vale o estado mais próximo
        out.onGround = t < 0.5 ? a.onGround : b.onGround;
        return out;
    }

    /** O ângulo em graus levado a (-180, 180]. */
    static double wrap(double degrees) {
        double d = degrees % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }

    /**
     * A posição dos pés do tripulante no mundo vista pelo observador: a pose da nave aplicada ao pé local. É a mesma conta do servidor
     * ({@code DeckCrew.applyToPlayer}) e do jogador local ({@code DeckClient}); fica aqui para o teste conferir a conta sem o jogo.
     */
    public static Vector3d worldFeet(DeckFrame frame, DeckState s) {
        return frame.toWorld(s.pos.x, s.pos.y, s.pos.z);
    }
}
