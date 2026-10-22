package vn.astrax.quest.time;

import vn.astrax.quest.model.ResetRule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/** Tinh khoa ky (period key) va thoi diem reset ke tiep theo mui gio + moc gio cau hinh. */
public final class PeriodCalculator {

    public record Snapshot(String daily, String weekly, String monthly,
                           Instant nextDaily, Instant nextWeekly, Instant nextMonthly) {
        public Instant validUntil() {
            Instant m = nextDaily;
            if (nextWeekly.isBefore(m)) m = nextWeekly;
            if (nextMonthly.isBefore(m)) m = nextMonthly;
            return m;
        }

        public String key(ResetRule rule) {
            return switch (rule) {
                case DAILY -> daily;
                case WEEKLY -> weekly;
                case MONTHLY -> monthly;
                case NEVER, EVENT -> "once";
            };
        }

        public Instant next(ResetRule rule) {
            return switch (rule) {
                case DAILY -> nextDaily;
                case WEEKLY -> nextWeekly;
                case MONTHLY -> nextMonthly;
                case NEVER, EVENT -> null;
            };
        }
    }

    private final ZoneId zone;
    private final int dailyHour;
    private final DayOfWeek weeklyDay;
    private final int weeklyHour;
    private final int monthlyDay;

    public PeriodCalculator(ZoneId zone, int dailyHour, DayOfWeek weeklyDay, int weeklyHour, int monthlyDay) {
        this.zone = zone;
        this.dailyHour = clamp(dailyHour, 0, 23);
        this.weeklyDay = weeklyDay;
        this.weeklyHour = clamp(weeklyHour, 0, 23);
        this.monthlyDay = clamp(monthlyDay, 1, 28);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public ZoneId zone() {
        return zone;
    }

    public Snapshot at(Instant now) {
        ZonedDateTime z = now.atZone(zone);

        LocalDate d = z.minusHours(dailyHour).toLocalDate();
        String daily = "d" + d;
        Instant nextDaily = d.plusDays(1).atTime(dailyHour, 0).atZone(zone).toInstant();

        LocalDate wd = z.minusHours(weeklyHour).toLocalDate().with(TemporalAdjusters.previousOrSame(weeklyDay));
        String weekly = "w" + wd;
        Instant nextWeekly = wd.plusWeeks(1).atTime(weeklyHour, 0).atZone(zone).toInstant();

        LocalDate base = z.minusHours(dailyHour).toLocalDate();
        LocalDate ms = base.getDayOfMonth() >= monthlyDay
                ? base.withDayOfMonth(monthlyDay)
                : base.minusMonths(1).withDayOfMonth(monthlyDay);
        String monthly = "m" + ms;
        Instant nextMonthly = ms.plusMonths(1).atTime(dailyHour, 0).atZone(zone).toInstant();

        return new Snapshot(daily, weekly, monthly, nextDaily, nextWeekly, nextMonthly);
    }
}
