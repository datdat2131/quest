package vn.astrax.quest.util;

/** Cap do Quest: exp can de len cap n+1 = base + growth * (n - 1). */
public final class LevelCalculator {
    private final long base;
    private final long growth;
    private final int maxLevel;

    public LevelCalculator(long base, long growth, int maxLevel) {
        this.base = Math.max(1, base);
        this.growth = Math.max(0, growth);
        this.maxLevel = Math.max(1, maxLevel);
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** Exp can de tu cap {@code level} len cap ke tiep. */
    public long costOf(int level) {
        return base + growth * (Math.max(1, level) - 1L);
    }

    public int levelForExp(long exp) {
        int lvl = 1;
        long rem = Math.max(0, exp);
        while (lvl < maxLevel && rem >= costOf(lvl)) {
            rem -= costOf(lvl);
            lvl++;
        }
        return lvl;
    }

    public long expIntoLevel(long exp) {
        int lvl = 1;
        long rem = Math.max(0, exp);
        while (lvl < maxLevel && rem >= costOf(lvl)) {
            rem -= costOf(lvl);
            lvl++;
        }
        return rem;
    }

    /** Exp con thieu de len cap ke tiep (0 neu da toi da). */
    public long expToNext(long exp) {
        int lvl = levelForExp(exp);
        if (lvl >= maxLevel) return 0;
        return costOf(lvl) - expIntoLevel(exp);
    }
}
