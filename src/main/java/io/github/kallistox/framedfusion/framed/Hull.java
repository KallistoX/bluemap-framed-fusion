package io.github.kallistox.framedfusion.framed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Faces of a small convex solid (brute force; solids have at most a dozen corners). */
public final class Hull {

    private static final float EPS = 1e-3f;

    /** A face: its corners counter-clockwise seen from outside, and its outward unit normal. */
    public record Polygon(List<float[]> points, float[] normal) {}

    private Hull() {}

    public static List<Polygon> faces(List<float[]> input) {
        List<float[]> pts = distinct(input);
        List<Polygon> faces = new ArrayList<>();
        List<float[]> planes = new ArrayList<>();
        int n = pts.size();
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) for (int k = j + 1; k < n; k++) {
            float[] a = pts.get(i), b = pts.get(j), c = pts.get(k);
            float[] nrm = normalize(cross(sub(b, a), sub(c, a)));
            if (nrm == null) continue;
            float d = dot(nrm, a);
            boolean front = false, back = false;
            for (float[] p : pts) {
                float s = dot(nrm, p) - d;
                if (s > EPS) front = true;
                else if (s < -EPS) back = true;
            }
            if (front && back) continue;
            if (front) { nrm = scale(nrm, -1); d = -d; }  // all points must lie behind the face
            if (known(planes, nrm, d)) continue;
            planes.add(new float[]{nrm[0], nrm[1], nrm[2], d});
            List<float[]> on = new ArrayList<>();
            for (float[] p : pts) if (Math.abs(dot(nrm, p) - d) <= EPS) on.add(p);
            faces.add(new Polygon(sortAround(on, nrm), nrm));
        }
        return faces;
    }

    private static List<float[]> sortAround(List<float[]> on, float[] normal) {
        float[] c = new float[3];
        for (float[] p : on) { c[0] += p[0] / on.size(); c[1] += p[1] / on.size(); c[2] += p[2] / on.size(); }
        float[] u = normalize(sub(on.get(0), c));
        float[] w = cross(normal, u);
        List<float[]> sorted = new ArrayList<>(on);
        sorted.sort(Comparator.comparingDouble(p -> Math.atan2(dot(sub(p, c), w), dot(sub(p, c), u))));
        return sorted;
    }

    private static boolean known(List<float[]> planes, float[] n, float d) {
        for (float[] p : planes) {
            if (Math.abs(p[0] - n[0]) < EPS && Math.abs(p[1] - n[1]) < EPS && Math.abs(p[2] - n[2]) < EPS && Math.abs(p[3] - d) < EPS * 16)
                return true;
        }
        return false;
    }

    private static List<float[]> distinct(List<float[]> in) {
        List<float[]> out = new ArrayList<>();
        outer:
        for (float[] p : in) {
            for (float[] q : out) if (Math.abs(p[0] - q[0]) < EPS && Math.abs(p[1] - q[1]) < EPS && Math.abs(p[2] - q[2]) < EPS) continue outer;
            out.add(p);
        }
        return out;
    }

    static float[] sub(float[] a, float[] b) { return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static float[] scale(float[] a, float s) { return new float[]{a[0] * s, a[1] * s, a[2] * s}; }
    static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
    static float[] normalize(float[] a) {
        float l = (float) Math.sqrt(dot(a, a));
        return l < 1e-6f ? null : scale(a, 1 / l);
    }
}
