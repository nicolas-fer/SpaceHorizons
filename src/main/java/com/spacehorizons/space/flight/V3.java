package com.spacehorizons.space.flight;

/**
 * Vetor 3D imutável em double. É a moeda da física de voo: posição, velocidade e direção de empuxo, sempre nos eixos do
 * espaço (os do {@link com.spacehorizons.space.SpaceFrame}), salvo onde o contrário estiver dito.
 */
public record V3(double x, double y, double z) {
    public static final V3 ZERO = new V3(0.0, 0.0, 0.0);

    /** Vetor a partir de um array {x, y, z} (os {@code out} do SpaceFrame). */
    public static V3 of(double[] a) {
        return new V3(a[0], a[1], a[2]);
    }

    public V3 plus(V3 o) {
        return new V3(x + o.x, y + o.y, z + o.z);
    }

    public V3 minus(V3 o) {
        return new V3(x - o.x, y - o.y, z - o.z);
    }

    public V3 times(double k) {
        return new V3(x * k, y * k, z * k);
    }

    public V3 negate() {
        return new V3(-x, -y, -z);
    }

    public double dot(V3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public V3 cross(V3 o) {
        return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    /** Vetor unitário na mesma direção; o vetor nulo continua nulo (nunca NaN). */
    public V3 unit() {
        double l = length();
        return l == 0.0 ? this : times(1.0 / l);
    }

    public double distanceTo(V3 o) {
        return minus(o).length();
    }

    /** Ângulo (rad, 0..π) entre dois vetores; numericamente estável perto de 0 e de π. 0 se algum for nulo. */
    public double angleTo(V3 o) {
        double c = cross(o).length();
        double d = dot(o);
        if (c == 0.0 && d == 0.0) {
            return 0.0;
        }
        return Math.atan2(c, d);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public double[] toArray() {
        return new double[] {x, y, z};
    }
}
