package vn.astrax.quest.util;

import java.util.Locale;

public final class Strings {
    private Strings() {
    }

    public static String bar(long cur, long max, int len, String fill, String empty) {
        if (len <= 0) return "";
        double r = max <= 0 ? 1.0 : Math.min(1.0, (double) Math.max(0, cur) / max);
        int f = (int) Math.round(r * len);
        if (cur < max && f >= len) f = len - 1;
        if (cur > 0 && f == 0 && len > 1) f = 1;
        return fill.repeat(f) + empty.repeat(len - f);
    }

    public static int percent(long cur, long max) {
        if (max <= 0) return 100;
        return (int) Math.min(100, Math.max(0, cur) * 100 / max);
    }

    public static String number(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    public static String money(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return number((long) Math.rint(v));
        return String.format(Locale.US, "%,.2f", v);
    }

    /** Vi du: 93784 giay -> "1 ngày 2 giờ"; duoi 60s -> "45 giây". */
    public static String duration(long seconds) {
        if (seconds < 0) seconds = 0;
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(" ngày ");
        if (h > 0) sb.append(h).append(" giờ ");
        if (m > 0 && d == 0) sb.append(m).append(" phút ");
        if (d == 0 && h == 0 && m == 0) sb.append(s).append(" giây ");
        String r = sb.toString().trim();
        return r.isEmpty() ? "0 giây" : r;
    }

    /** STONE_BRICKS -> "Stone Bricks". */
    public static String pretty(String enumLike) {
        if (enumLike == null || enumLike.isEmpty()) return "";
        String[] parts = enumLike.toLowerCase(Locale.ROOT).replace("minecraft:", "").split("[_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
