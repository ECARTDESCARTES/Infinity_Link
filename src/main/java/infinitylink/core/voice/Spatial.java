package infinitylink.core.voice;

/** Spatialisation stéréo sans bibliothèque : gain décroissant linéaire jusqu'au rayon, panoramique à puissance constante
 *  selon l'angle entre le regard de l'auditeur et la source (Minecraft : yaw en degrés, 0 = +Z, 90 = −X). */
public final class Spatial {
    private Spatial() {}

    /** {gauche, droite}. */
    public static float[] gains(double lx, double ly, double lz, float yawDeg, double sx, double sy, double sz, float radius) {
        double dx = sx - lx, dy = sy - ly, dz = sz - lz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float g = (float) Math.max(0, 1 - d / Math.max(1, radius));
        if (d < 1e-3) return new float[]{g, g};
        double yaw = Math.toRadians(yawDeg);
        // repère de l'auditeur : avant = (−sin, cos), droite = (−cos, −sin)
        double right = (-Math.cos(yaw) * dx - Math.sin(yaw) * dz) / Math.max(1e-6, Math.hypot(dx, dz));
        if (Double.isNaN(right)) right = 0;
        double pan = (Math.max(-1, Math.min(1, right)) + 1) * Math.PI / 4; // 0 = gauche, π/2 = droite
        return new float[]{(float) (g * Math.cos(pan) * Math.sqrt(2) * 0.85), (float) (g * Math.sin(pan) * Math.sqrt(2) * 0.85)};
    }
}
