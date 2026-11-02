package net.tkgon.mc.iruuRPG.itempack;

/**
 * The few turns (3 x 3 matrices, rows first) and vectors the item models need. Angles are degrees, turning the right-hand way about an axis,
 * as the game and Blockbench both do.
 */
final class Rotation {

    static final double[][] IDENTITY = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

    private Rotation() {
    }

    static double[][] x(double degrees) {
        double c = Math.cos(Math.toRadians(degrees));
        double s = Math.sin(Math.toRadians(degrees));
        return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
    }

    static double[][] y(double degrees) {
        double c = Math.cos(Math.toRadians(degrees));
        double s = Math.sin(Math.toRadians(degrees));
        return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
    }

    static double[][] z(double degrees) {
        double c = Math.cos(Math.toRadians(degrees));
        double s = Math.sin(Math.toRadians(degrees));
        return new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
    }

    static double[][] about(String axis, double degrees) {
        return switch (axis) {
            case "x" -> x(degrees);
            case "y" -> y(degrees);
            default -> z(degrees);
        };
    }

    /** The turn of a display setting ({@code rotation: [x, y, z]}): the game turns about z, then y, then x (x · y · z). */
    static double[][] display(double[] degrees) {
        return multiply(x(degrees[0]), multiply(y(degrees[1]), z(degrees[2])));
    }

    /** The turn of a cube or group in Blockbench ({@code rotation: [x, y, z]}): about x, then y, then z (z · y · x). */
    static double[][] blockbench(double[] degrees) {
        return multiply(z(degrees[2]), multiply(y(degrees[1]), x(degrees[0])));
    }

    /** The angles {x, y, z} (degrees) of a display setting that makes this turn (the inverse of {@link #display}). */
    static double[] displayAngles(double[][] m) {
        double sinY = Math.max(-1.0, Math.min(1.0, m[0][2]));
        double y = Math.asin(sinY);
        double x;
        double z;
        if (Math.abs(sinY) < 0.999999) {
            x = Math.atan2(-m[1][2], m[2][2]);
            z = Math.atan2(-m[0][1], m[0][0]);
        } else {
            // (straight up or down: only x + z or x - z is known; all of it goes to x)
            x = Math.atan2(m[2][1], m[1][1]);
            z = 0.0;
        }
        return new double[]{Math.toDegrees(x), Math.toDegrees(y), Math.toDegrees(z)};
    }

    static double[][] multiply(double[][] a, double[][] b) {
        double[][] result = new double[3][3];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                double sum = 0;
                for (int k = 0; k < 3; k++) sum += a[row][k] * b[k][column];
                result[row][column] = sum;
            }
        }
        return result;
    }

    static double[][] transpose(double[][] m) {
        double[][] result = new double[3][3];
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) result[row][column] = m[column][row];
        return result;
    }

    static double[] apply(double[][] m, double[] v) {
        return new double[]{
                m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    static double[] plus(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static double[] minus(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] times(double[] v, double k) {
        return new double[]{v[0] * k, v[1] * k, v[2] * k};
    }

    /** How far two turns are (the size of their difference). */
    static double distance(double[][] a, double[][] b) {
        double sum = 0;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) sum += (a[row][column] - b[row][column]) * (a[row][column] - b[row][column]);
        return Math.sqrt(sum);
    }

    static boolean isIdentity(double[][] m) {
        return distance(m, IDENTITY) < 1.0E-9;
    }

    static boolean isZero(double[] degrees) {
        return Math.abs(degrees[0]) < 1.0E-9 && Math.abs(degrees[1]) < 1.0E-9 && Math.abs(degrees[2]) < 1.0E-9;
    }
}
