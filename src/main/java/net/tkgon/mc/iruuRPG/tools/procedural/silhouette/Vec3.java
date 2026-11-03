package net.tkgon.mc.iruuRPG.tools.procedural.silhouette;

/** 不変の3Dベクトル。 */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 UP = new Vec3(0, 1, 0);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 mul(double s) { return new Vec3(x * s, y * s, z * s); }
    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
    public double len() { return Math.sqrt(x * x + y * y + z * z); }
    public double hlen() { return Math.sqrt(x * x + z * z); }
    public Vec3 norm() { double l = len(); return l < 1e-9 ? UP : mul(1.0 / l); }
    public Vec3 abs() { return new Vec3(Math.abs(x), Math.abs(y), Math.abs(z)); }
    public Vec3 lerp(Vec3 o, double t) { return new Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t); }
    public Vec3 withY(double ny) { return new Vec3(x, ny, z); }

    public static Vec3 horizontal(double angle) { return new Vec3(Math.cos(angle), 0, Math.sin(angle)); }

    public static Vec3 bezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1 - t;
        double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
        return new Vec3(a * p0.x + b * p1.x + c * p2.x + d * p3.x,
                a * p0.y + b * p1.y + c * p2.y + d * p3.y,
                a * p0.z + b * p1.z + c * p2.z + d * p3.z);
    }
}
