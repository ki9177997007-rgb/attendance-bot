package ru.secondmemory;

/** Conservative matching: uncertain speech is kept, never discarded. Not authentication. */
public final class SpeakerProfile {
    private double[] sum;
    private int count;
    public static double similarity(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return -1;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            if (!Double.isFinite(a[i]) || !Double.isFinite(b[i])) return -1;
            dot += a[i]*b[i]; aa += a[i]*a[i]; bb += b[i]*b[i];
        }
        return aa > 0 && bb > 0 ? dot/Math.sqrt(aa*bb) : -1;
    }
    public boolean add(double[] vector, int frames) {
        if (frames < 400 || similarity(vector, vector) < 0.99) return false;
        if (sum != null && similarity(sum, vector) < 0.65) return false;
        if (sum == null) sum = new double[vector.length];
        double norm = 0;
        for (double v : vector) norm += v*v;
        norm = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) sum[i] += vector[i]/norm;
        count++;
        return true;
    }
    public int count() { return count; }
    public double[] vector() { return sum == null ? null : sum.clone(); }
    public static boolean probableOwner(double[] owner, double[] voice, int frames) {
        return frames >= 400 && similarity(owner, voice) >= 0.65;
    }
}
