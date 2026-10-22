package vn.astrax.quest.storage;

import vn.astrax.quest.util.LevelCalculator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Toan bo truy van SQL. Cac thao tac quan trong (nhan thuong, tru token) deu nguyen tu trong 1 giao dich. */
public final class Storage {

    public record Profile(int level, long exp, long tokens, String title) {
    }

    public record ProgressRow(String questId, String period, long progress, long target, boolean completed) {
    }

    public record Pending(long id, String payload) {
    }

    public record Snapshot(Profile profile, Set<String> titles, Set<String> claims,
                           List<ProgressRow> progress, List<Pending> pending) {
    }

    public enum ClaimOutcome { SUCCESS, ALREADY_CLAIMED, NOT_COMPLETE }

    public record ClaimRequest(UUID uuid, String name, String owner, String questId, String period,
                               long pendingDelta, long target, long required,
                               double money, long tokens, long exp, String title, String items, String note) {
    }

    public record ClaimResult(ClaimOutcome outcome, int oldLevel, int newLevel, long exp, long tokens, long txId) {
    }

    public record LevelChange(int oldLevel, int newLevel, long exp) {
    }

    public record Delta(String owner, String questId, String period, long delta, long target, long required) {
    }

    public record Tx(long id, String questId, String period, String action, double money, long tokens,
                     long exp, String items, String status, String note, long at) {
    }

    public record TopRow(String name, int level, long exp) {
    }

    private final Database db;
    private final Supplier<LevelCalculator> levels;

    public Storage(Database db, Supplier<LevelCalculator> levels) {
        this.db = db;
        this.levels = levels;
    }

    public Database database() {
        return db;
    }

    private String ignore(String table, String cols, String vals) {
        return (db.mysql() ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ") + table + "(" + cols + ") VALUES(" + vals + ")";
    }

    private static String marks(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    // ------------------------------------------------------------------ schema
    public void init() throws SQLException {
        db.await(c -> {
            String idCol = db.mysql() ? "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
            String tail = db.mysql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS aq_players(uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "name VARCHAR(32) NOT NULL, level INT NOT NULL DEFAULT 1, exp BIGINT NOT NULL DEFAULT 0, "
                        + "tokens BIGINT NOT NULL DEFAULT 0, title VARCHAR(64), "
                        + "created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)" + tail);
                st.execute("CREATE TABLE IF NOT EXISTS aq_progress(owner VARCHAR(64) NOT NULL, quest_id VARCHAR(64) NOT NULL, "
                        + "period VARCHAR(64) NOT NULL, progress BIGINT NOT NULL DEFAULT 0, target BIGINT NOT NULL, "
                        + "completed INT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL, "
                        + "PRIMARY KEY(owner, quest_id, period))" + tail);
                st.execute("CREATE TABLE IF NOT EXISTS aq_claims(uuid VARCHAR(36) NOT NULL, quest_id VARCHAR(64) NOT NULL, "
                        + "period VARCHAR(64) NOT NULL, claimed_at BIGINT NOT NULL, "
                        + "PRIMARY KEY(uuid, quest_id, period))" + tail);
                st.execute("CREATE TABLE IF NOT EXISTS aq_titles(uuid VARCHAR(36) NOT NULL, title_id VARCHAR(64) NOT NULL, "
                        + "unlocked_at BIGINT NOT NULL, PRIMARY KEY(uuid, title_id))" + tail);
                st.execute("CREATE TABLE IF NOT EXISTS aq_pending(id " + idCol + ", uuid VARCHAR(36) NOT NULL, "
                        + "payload TEXT NOT NULL, created_at BIGINT NOT NULL)" + tail);
                st.execute("CREATE TABLE IF NOT EXISTS aq_transactions(id " + idCol + ", uuid VARCHAR(36) NOT NULL, "
                        + "player_name VARCHAR(32), quest_id VARCHAR(64), period VARCHAR(64), action VARCHAR(24) NOT NULL, "
                        + "money DOUBLE NOT NULL DEFAULT 0, tokens BIGINT NOT NULL DEFAULT 0, exp BIGINT NOT NULL DEFAULT 0, "
                        + "items TEXT, status VARCHAR(24) NOT NULL, note TEXT, created_at BIGINT NOT NULL)" + tail);
                for (String idx : new String[]{
                        "CREATE INDEX idx_aq_tx_uuid ON aq_transactions(uuid, created_at)",
                        "CREATE INDEX idx_aq_pending_uuid ON aq_pending(uuid)",
                        "CREATE INDEX idx_aq_players_exp ON aq_players(exp)"}) {
                    try {
                        st.execute(idx);
                    } catch (SQLException ignored) {
                        // index da ton tai
                    }
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ load
    public CompletableFuture<Snapshot> loadPlayer(UUID uuid, String name, Collection<String> periods) {
        return db.supply(c -> {
            long now = System.currentTimeMillis();
            String id = uuid.toString();
            try (PreparedStatement ps = c.prepareStatement(ignore("aq_players",
                    "uuid,name,level,exp,tokens,created_at,updated_at", "?,?,1,0,0,?,?"))) {
                ps.setString(1, id);
                ps.setString(2, name);
                ps.setLong(3, now);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE aq_players SET name=?, updated_at=? WHERE uuid=?")) {
                ps.setString(1, name);
                ps.setLong(2, now);
                ps.setString(3, id);
                ps.executeUpdate();
            }
            Profile profile;
            try (PreparedStatement ps = c.prepareStatement("SELECT level,exp,tokens,title FROM aq_players WHERE uuid=?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Khong tao duoc ho so nguoi choi " + id);
                    profile = new Profile(rs.getInt(1), rs.getLong(2), rs.getLong(3), rs.getString(4));
                }
            }
            Set<String> titles = new HashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT title_id FROM aq_titles WHERE uuid=?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) titles.add(rs.getString(1));
                }
            }
            Set<String> claims = new HashSet<>();
            List<ProgressRow> progress = new ArrayList<>();
            if (!periods.isEmpty()) {
                String in = marks(periods.size());
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT quest_id,period FROM aq_claims WHERE uuid=? AND period IN (" + in + ")")) {
                    ps.setString(1, id);
                    int i = 2;
                    for (String p : periods) ps.setString(i++, p);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) claims.add(rs.getString(1) + "|" + rs.getString(2));
                    }
                }
                progress = queryProgress(c, id, periods);
            }
            List<Pending> pending = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id,payload FROM aq_pending WHERE uuid=? ORDER BY id")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) pending.add(new Pending(rs.getLong(1), rs.getString(2)));
                }
            }
            return new Snapshot(profile, titles, claims, progress, pending);
        });
    }

    private List<ProgressRow> queryProgress(Connection c, String owner, Collection<String> periods) throws SQLException {
        List<ProgressRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT quest_id,period,progress,target,completed FROM aq_progress WHERE owner=? AND period IN ("
                        + marks(periods.size()) + ")")) {
            ps.setString(1, owner);
            int i = 2;
            for (String p : periods) ps.setString(i++, p);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ProgressRow(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getInt(5) == 1));
                }
            }
        }
        return out;
    }

    public CompletableFuture<List<ProgressRow>> loadProgress(String owner, Collection<String> periods) {
        if (periods.isEmpty()) return CompletableFuture.completedFuture(List.of());
        return db.supply(c -> queryProgress(c, owner, periods));
    }

    // ------------------------------------------------------------------ progress
    /** Cong don tien do (additive) => an toan khi nhieu may chu cung ghi. */
    static void applyDelta(Connection c, Database db, String owner, String questId, String period,
                           long delta, long target, long required, long now, boolean mysql) throws SQLException {
        if (delta > 0) {
            int n;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE aq_progress SET progress=progress+?, updated_at=? WHERE owner=? AND quest_id=? AND period=?")) {
                ps.setLong(1, delta);
                ps.setLong(2, now);
                ps.setString(3, owner);
                ps.setString(4, questId);
                ps.setString(5, period);
                n = ps.executeUpdate();
            }
            if (n == 0) {
                String sql = (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                        + "aq_progress(owner,quest_id,period,progress,target,completed,updated_at) VALUES(?,?,?,?,?,0,?)";
                int ins;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, owner);
                    ps.setString(2, questId);
                    ps.setString(3, period);
                    ps.setLong(4, delta);
                    ps.setLong(5, target);
                    ps.setLong(6, now);
                    ins = ps.executeUpdate();
                }
                if (ins == 0) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE aq_progress SET progress=progress+?, updated_at=? WHERE owner=? AND quest_id=? AND period=?")) {
                        ps.setLong(1, delta);
                        ps.setLong(2, now);
                        ps.setString(3, owner);
                        ps.setString(4, questId);
                        ps.setString(5, period);
                        ps.executeUpdate();
                    }
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE aq_progress SET completed=1, target=? WHERE owner=? AND quest_id=? AND period=? AND progress>=? AND completed=0")) {
            ps.setLong(1, target);
            ps.setString(2, owner);
            ps.setString(3, questId);
            ps.setString(4, period);
            ps.setLong(5, required);
            ps.executeUpdate();
        }
    }

    public CompletableFuture<Void> flush(List<Delta> deltas) {
        if (deltas.isEmpty()) return CompletableFuture.completedFuture(null);
        return db.supply(c -> {
            boolean old = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                for (Delta d : deltas) {
                    applyDelta(c, db, d.owner(), d.questId(), d.period(), d.delta(), d.target(), d.required(), now, db.mysql());
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(old);
            }
            return null;
        });
    }

    private static long progressOf(Connection c, String owner, String questId, String period) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT progress FROM aq_progress WHERE owner=? AND quest_id=? AND period=?")) {
            ps.setString(1, owner);
            ps.setString(2, questId);
            ps.setString(3, period);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    // ------------------------------------------------------------------ claim (atomic, anti-duplicate)
    public CompletableFuture<ClaimResult> claim(ClaimRequest r) {
        return db.supply(c -> {
            boolean old = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                String uid = r.uuid().toString();
                applyDelta(c, db, r.owner(), r.questId(), r.period(), r.pendingDelta(), r.target(), r.required(), now, db.mysql());

                long prog = progressOf(c, r.owner(), r.questId(), r.period());
                if (prog < r.required()) {
                    c.commit();
                    return new ClaimResult(ClaimOutcome.NOT_COMPLETE, 0, 0, 0, 0, 0);
                }
                int inserted;
                try (PreparedStatement ps = c.prepareStatement(ignore("aq_claims", "uuid,quest_id,period,claimed_at", "?,?,?,?"))) {
                    ps.setString(1, uid);
                    ps.setString(2, r.questId());
                    ps.setString(3, r.period());
                    ps.setLong(4, now);
                    inserted = ps.executeUpdate();
                }
                if (inserted == 0) {
                    c.commit();
                    return new ClaimResult(ClaimOutcome.ALREADY_CLAIMED, 0, 0, 0, 0, 0);
                }
                int oldLevel = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT level FROM aq_players WHERE uuid=?")) {
                    ps.setString(1, uid);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Thieu ho so nguoi choi " + uid);
                        oldLevel = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE aq_players SET exp=exp+?, tokens=tokens+?, updated_at=? WHERE uuid=?")) {
                    ps.setLong(1, r.exp());
                    ps.setLong(2, r.tokens());
                    ps.setLong(3, now);
                    ps.setString(4, uid);
                    ps.executeUpdate();
                }
                long exp;
                long tokens;
                try (PreparedStatement ps = c.prepareStatement("SELECT exp,tokens FROM aq_players WHERE uuid=?")) {
                    ps.setString(1, uid);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        exp = rs.getLong(1);
                        tokens = rs.getLong(2);
                    }
                }
                int newLevel = levels.get().levelForExp(exp);
                if (newLevel != oldLevel) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE aq_players SET level=? WHERE uuid=?")) {
                        ps.setInt(1, newLevel);
                        ps.setString(2, uid);
                        ps.executeUpdate();
                    }
                }
                if (r.title() != null) {
                    try (PreparedStatement ps = c.prepareStatement(ignore("aq_titles", "uuid,title_id,unlocked_at", "?,?,?"))) {
                        ps.setString(1, uid);
                        ps.setString(2, r.title());
                        ps.setLong(3, now);
                        ps.executeUpdate();
                    }
                }
                long txId = insertTx(c, uid, r.name(), r.questId(), r.period(), "CLAIM", r.money(), r.tokens(), r.exp(),
                        r.items(), "PENDING", r.note(), now);
                c.commit();
                return new ClaimResult(ClaimOutcome.SUCCESS, oldLevel, newLevel, exp, tokens, txId);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(old);
            }
        });
    }

    // ------------------------------------------------------------------ transactions log
    private static long insertTx(Connection c, String uuid, String name, String questId, String period, String action,
                                 double money, long tokens, long exp, String items, String status, String note,
                                 long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO aq_transactions(uuid,player_name,quest_id,period,action,money,tokens,exp,items,status,note,created_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setString(3, questId);
            ps.setString(4, period);
            ps.setString(5, action);
            ps.setDouble(6, money);
            ps.setLong(7, tokens);
            ps.setLong(8, exp);
            ps.setString(9, items);
            ps.setString(10, status);
            ps.setString(11, note);
            ps.setLong(12, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public CompletableFuture<Long> logTx(UUID uuid, String name, String questId, String period, String action,
                                         double money, long tokens, long exp, String items, String status, String note) {
        return db.supply(c -> insertTx(c, uuid.toString(), name, questId, period, action, money, tokens, exp,
                items, status, note, System.currentTimeMillis()));
    }

    public CompletableFuture<Void> updateTx(long id, String status, String note) {
        if (id < 0) return CompletableFuture.completedFuture(null);
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE aq_transactions SET status=?, note=? WHERE id=?")) {
                ps.setString(1, status);
                ps.setString(2, note);
                ps.setLong(3, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<Tx>> recentTx(UUID uuid, int limit) {
        return db.supply(c -> {
            List<Tx> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id,quest_id,period,action,money,tokens,exp,items,status,note,created_at FROM aq_transactions "
                            + "WHERE uuid=? ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, Math.max(1, Math.min(100, limit)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Tx(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5),
                                rs.getLong(6), rs.getLong(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getLong(11)));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------ tokens / exp / titles
    public CompletableFuture<Void> ensurePlayer(UUID uuid, String name) {
        return db.supply(c -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = c.prepareStatement(ignore("aq_players",
                    "uuid,name,level,exp,tokens,created_at,updated_at", "?,?,1,0,0,?,?"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name == null ? "unknown" : name);
                ps.setLong(3, now);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** @return so du moi, hoac -1 neu tru vuot qua so du. */
    public CompletableFuture<Long> addTokens(UUID uuid, long delta) {
        return db.supply(c -> {
            String id = uuid.toString();
            int n;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE aq_players SET tokens=tokens+?, updated_at=? WHERE uuid=? AND tokens+? >= 0")) {
                ps.setLong(1, delta);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, id);
                ps.setLong(4, delta);
                n = ps.executeUpdate();
            }
            if (n == 0) return -1L;
            try (PreparedStatement ps = c.prepareStatement("SELECT tokens FROM aq_players WHERE uuid=?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : -1L;
                }
            }
        });
    }

    public CompletableFuture<LevelChange> addExp(UUID uuid, long delta) {
        return db.supply(c -> {
            boolean old = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                String id = uuid.toString();
                int oldLevel;
                try (PreparedStatement ps = c.prepareStatement("SELECT level FROM aq_players WHERE uuid=?")) {
                    ps.setString(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Khong co ho so " + id);
                        oldLevel = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE aq_players SET exp=CASE WHEN exp+? < 0 THEN 0 ELSE exp+? END, updated_at=? WHERE uuid=?")) {
                    ps.setLong(1, delta);
                    ps.setLong(2, delta);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.setString(4, id);
                    ps.executeUpdate();
                }
                long exp;
                try (PreparedStatement ps = c.prepareStatement("SELECT exp FROM aq_players WHERE uuid=?")) {
                    ps.setString(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        exp = rs.getLong(1);
                    }
                }
                int newLevel = levels.get().levelForExp(exp);
                try (PreparedStatement ps = c.prepareStatement("UPDATE aq_players SET level=? WHERE uuid=?")) {
                    ps.setInt(1, newLevel);
                    ps.setString(2, id);
                    ps.executeUpdate();
                }
                c.commit();
                return new LevelChange(oldLevel, newLevel, exp);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(old);
            }
        });
    }

    public CompletableFuture<Void> setTitle(UUID uuid, String titleOrNull) {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE aq_players SET title=?, updated_at=? WHERE uuid=?")) {
                ps.setString(1, titleOrNull);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** @return true neu moi mo khoa. */
    public CompletableFuture<Boolean> unlockTitle(UUID uuid, String titleId) {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement(ignore("aq_titles", "uuid,title_id,unlocked_at", "?,?,?"))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, titleId);
                ps.setLong(3, System.currentTimeMillis());
                return ps.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Boolean> removeTitle(UUID uuid, String titleId) {
        return db.supply(c -> {
            boolean removed;
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM aq_titles WHERE uuid=? AND title_id=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, titleId);
                removed = ps.executeUpdate() > 0;
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE aq_players SET title=NULL WHERE uuid=? AND title=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, titleId);
                ps.executeUpdate();
            }
            return removed;
        });
    }

    // ------------------------------------------------------------------ pending items
    public CompletableFuture<Long> addPending(UUID uuid, String payload) {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO aq_pending(uuid,payload,created_at) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, payload);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    return rs.next() ? rs.getLong(1) : -1L;
                }
            }
        });
    }

    /** @return true neu dong nay do chinh lenh nay xoa (tranh giao 2 lan khi nhieu may chu). */
    public CompletableFuture<Boolean> deletePending(long id) {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM aq_pending WHERE id=?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    // ------------------------------------------------------------------ admin / misc
    public CompletableFuture<Void> resetQuests(UUID uuid, Collection<String> questIds) {
        return db.supply(c -> {
            boolean old = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                String idCond = questIds.isEmpty() ? "" : " AND quest_id IN (" + marks(questIds.size()) + ")";
                for (String sql : new String[]{
                        "DELETE FROM aq_progress WHERE owner=?" + idCond,
                        "DELETE FROM aq_claims WHERE uuid=?" + idCond}) {
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setString(1, uuid.toString());
                        int i = 2;
                        for (String q : questIds) ps.setString(i++, q);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(old);
            }
            return null;
        });
    }

    public CompletableFuture<List<TopRow>> top(int limit) {
        return db.supply(c -> {
            List<TopRow> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT name,level,exp FROM aq_players ORDER BY exp DESC LIMIT ?")) {
                ps.setInt(1, Math.max(1, Math.min(50, limit)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new TopRow(rs.getString(1), rs.getInt(2), rs.getLong(3)));
                }
            }
            return out;
        });
    }
}
