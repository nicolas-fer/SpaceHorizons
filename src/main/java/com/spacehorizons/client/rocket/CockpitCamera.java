package com.spacehorizons.client.rocket;

import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * Contas da câmera do piloto do foguete (lógica pura sobre JOML, testada em ida e volta).
 *
 * <h2>Convenções</h2>
 * <ul>
 *   <li>A câmera do Minecraft guarda um quaternion {@code R = rotationYXZ(π − yaw, −pitch, −roll)} que leva os eixos da
 *       câmera (frente (0,0,−1), cima (0,1,0), esquerda (−1,0,0)) para o mundo. O evento
 *       {@code ViewportEvent.ComputeCameraAngles} só aceita yaw, pitch e roll em graus; {@link #toVanilla} faz a conversão
 *       inversa a partir de um quaternion qualquer. Com pitch exatamente ±90° (o foguete em pé olhando ao longo do nariz, o
 *       caso mais comum!) yaw e roll se confundem; a extração própria ({@link #eulerYXZ}) põe tudo no yaw e zera o roll, e a
 *       rotação composta continua exata. A extração do JOML perde a informação nesse caso.</li>
 *   <li><b>Primeira pessoa (cockpit)</b>: a câmera olha ao longo do NARIZ (+Y do retrato), o padrão KSP; o "cima" da tela
 *       é −Z do retrato (a frente do cockpit) e a "direita" é −X. Com isto W (arfagem +) leva o nariz para baixo da tela,
 *       D (guinada +) para a direita e E (rolagem +) gira no sentido horário, como num avião.</li>
 *   <li><b>Terceira pessoa</b>: a câmera orbita o assento. A base olha para +Z do retrato com o nariz para cima da tela
 *       (a câmera fica na frente do cockpit, vendo o foguete de três quartos) e inclinada alguns graus para baixo; W, D e E
 *       continuam girando o foguete no mesmo sentido visto da tela. O olhar livre do mouse soma por cima.</li>
 *   <li>O olhar livre usa o yaw e o pitch do próprio jogador (o vanilla já os mexe com o mouse): zero é "olhando para a
 *       frente da base".</li>
 * </ul>
 */
public final class CockpitCamera {
    /** Quanto a base de terceira pessoa olha para baixo, em graus. */
    public static final float CHASE_DOWN_TILT_DEGREES = 18.0F;
    /** Distância mínima e máxima da câmera em terceira pessoa, em blocos. */
    public static final float MIN_DISTANCE = 6.0F;
    public static final float MAX_DISTANCE = 96.0F;

    /** Eixos da câmera → eixos do corpo na primeira pessoa: frente → +Y (nariz), cima → −Z, esquerda → +X. */
    private static final Quaternionf NOSE_VIEW = new Quaternionf().setFromNormalized(new Matrix3f(-1, 0, 0, 0, 0, -1, 0, -1, 0));
    /** Terceira pessoa: frente → +Z, cima → +Y, esquerda → −X (um giro de 180° em Y), já inclinada para baixo. */
    private static final Quaternionf CHASE_VIEW =
            new Quaternionf().rotationY((float) Math.PI).rotateX((float) Math.toRadians(-CHASE_DOWN_TILT_DEGREES));

    /** Altura do centro da janela do cockpit no bloco (px/16), medida no modelo {@code rocket_cockpit}: abertura de y = 3 a 10. */
    public static final float WINDOW_CENTER_Y = 6.5F / 16.0F;
    /** Quanto o olho fica à frente do plano da janela, em blocos: o bastante para a moldura não passar do plano de corte da câmera. */
    public static final float WINDOW_EYE_OUT = 0.12F;

    private CockpitCamera() {
    }

    /**
     * Posição do olho da primeira pessoa no corpo do foguete, relativa ao CENTRO do bloco do cockpit: no meio da janela, logo à
     * frente do plano dela ({@code frontX}, {@code frontZ} é o passo da face da janela, a direção do bloco). Fora do casco o
     * olhar ao longo do nariz não esbarra na moldura nem no ombro do cockpit (o casco só aparece de lado, na parte de baixo
     * da tela) e a câmera não fica dentro do bloco.
     */
    public static Vector3f windowEye(int frontX, int frontZ) {
        float out = 0.5F + WINDOW_EYE_OUT;
        return new Vector3f(frontX * out, WINDOW_CENTER_Y - 0.5F, frontZ * out);
    }

    /** Eixos da câmera para os do corpo da base escolhida (cópia). */
    public static Quaternionf baseView(boolean thirdPerson) {
        return new Quaternionf(thirdPerson ? CHASE_VIEW : NOSE_VIEW);
    }

    /**
     * O quaternion da câmera no mundo: {@code atitude · base · olharLivre}. {@code lookYawDeg} e {@code lookPitchDeg} são o
     * yaw e o pitch do jogador no sentido do Minecraft (yaw cresce para a direita, pitch positivo é olhar para baixo).
     */
    public static Quaternionf cameraRotation(Quaternionfc attitude, boolean thirdPerson, float lookYawDeg, float lookPitchDeg) {
        Quaternionf free = new Quaternionf().rotationYXZ((float) Math.toRadians(-lookYawDeg), (float) Math.toRadians(-lookPitchDeg), 0.0F);
        return new Quaternionf(attitude).mul(baseView(thirdPerson)).mul(free).normalize();
    }

    /**
     * A câmera do tripulante a pé no convés (S5): {@code atitude · olhar local}. {@code lookYawDeg} e {@code lookPitchDeg} são o yaw e o pitch do
     * jogador NO REFERENCIAL DA NAVE (o que o mouse mexe); a atitude os leva ao mundo, então a nave inclinada inclina o horizonte da câmera.
     */
    public static Quaternionf deckRotation(Quaternionfc attitude, float lookYawDeg, float lookPitchDeg) {
        return new Quaternionf(attitude).mul(fromVanilla(lookYawDeg, lookPitchDeg, 0.0F)).normalize();
    }

    /** O quaternion que a câmera do vanilla monta de yaw, pitch e roll (graus): {@code rotationYXZ(π − yaw, −pitch, −roll)}. */
    public static Quaternionf fromVanilla(float yawDeg, float pitchDeg, float rollDeg) {
        return new Quaternionf().rotationYXZ((float) (Math.PI - Math.toRadians(yawDeg)), (float) -Math.toRadians(pitchDeg),
                (float) -Math.toRadians(rollDeg));
    }

    /** Yaw, pitch e roll (graus) que fazem a câmera do vanilla montar o quaternion {@code q}: o inverso de {@link #fromVanilla}. */
    public static float[] toVanilla(Quaternionfc q) {
        double[] eul = eulerYXZ(q);
        return new float[] {(float) Math.toDegrees(Math.PI - eul[0]), (float) -Math.toDegrees(eul[1]), (float) -Math.toDegrees(eul[2])};
    }

    /**
     * Ângulos {@code {a, b, c}} (radianos) tais que {@code q = Ry(a) · Rx(b) · Rz(c)}, a ordem do {@code rotationYXZ}. No caso
     * degenerado (b = ±90°, o cosseno de b é zero) só {@code a ± c} existe: fica {@code c = 0} e a rotação toda vai para {@code a}.
     */
    static double[] eulerYXZ(Quaternionfc q) {
        Matrix3d m = new Matrix3d().set(new Quaterniond(q.x(), q.y(), q.z(), q.w()).normalize());
        // R[linha][coluna] = m.get(coluna, linha). Para Ry(a)·Rx(b)·Rz(c): R[1][2] = -sin b; R[0][2] = sin a cos b; R[2][2] = cos a cos b;
        // R[1][0] = cos b sin c; R[1][1] = cos b cos c
        double sinB = Math.max(-1.0, Math.min(1.0, -m.get(2, 1)));
        double b = Math.asin(sinB);
        if (Math.abs(sinB) > 0.9999999) {
            // com c = 0: R[0][0] = cos a e R[2][0] = -sin a
            return new double[] {Math.atan2(-m.get(0, 2), m.get(0, 0)), b, 0.0};
        }
        return new double[] {Math.atan2(m.get(2, 0), m.get(2, 2)), b, Math.atan2(m.get(0, 1), m.get(1, 1))};
    }

    /**
     * Como {@link #toVanilla}, mas para a câmera espelhada (terceira pessoa "de frente", F5): o vanilla chama
     * {@code setRotation(yaw + 180, −pitch, −roll)} de novo, o que daria {@code rotationYXZ(−yaw, pitch, roll)}. Os
     * ângulos devolvidos fazem esse resultado ser {@code q} girado 180° em torno do "cima" da câmera (a visão de frente).
     */
    public static float[] toVanillaMirrored(Quaternionfc q) {
        double[] eul = eulerYXZ(new Quaternionf(q).rotateY((float) Math.PI));
        return new float[] {(float) -Math.toDegrees(eul[0]), (float) Math.toDegrees(eul[1]), (float) Math.toDegrees(eul[2])};
    }

    /** Distância da câmera em terceira pessoa para um retrato de {@code sx × sy × sz} blocos: proporcional ao maior lado. */
    public static float thirdPersonDistance(int sx, int sy, int sz) {
        float longest = Math.max(sx, Math.max(sy, sz));
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, 2.0F + 1.5F * longest));
    }
}
