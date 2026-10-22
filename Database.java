package vn.astrax.quest.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ket noi JDBC: moi luong worker giu rieng 1 connection (ThreadLocal) => khong can pool ngoai.
 * SQLite dung dung 1 luong (ghi tuan tu, an toan); MySQL dung {@code poolSize} luong.
 */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection c) throws SQLException;
    }

    private final DbConfig cfg;
    private final Logger log;
    private final ExecutorService executor;
    private final ThreadLocal<Connection> local = new ThreadLocal<>();
    private final Queue<Connection> all = new ConcurrentLinkedQueue<>();

    public Database(DbConfig cfg, Logger log) {
        this.cfg = cfg;
        this.log = log;
        int threads = cfg.mysql() ? Math.max(1, Math.min(16, cfg.poolSize())) : 1;
        AtomicInteger n = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "AstraxQuest-DB-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        try {
            Class.forName(cfg.mysql() ? "com.mysql.cj.jdbc.Driver" : "org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            log.severe("Khong tim thay JDBC driver (" + (cfg.mysql() ? "MySQL" : "SQLite")
                    + "). Paper 1.21.4 thuong bundle san; hay kiem tra ban Paper cua ban.");
        }
    }

    public boolean mysql() {
        return cfg.mysql();
    }

    private Connection open() throws SQLException {
        Connection c;
        if (cfg.mysql()) {
            String props = cfg.properties() == null || cfg.properties().isBlank()
                    ? "useSSL=false&characterEncoding=utf8&autoReconnect=true" : cfg.properties();
            c = DriverManager.getConnection("jdbc:mysql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.database()
                    + "?" + props, cfg.user(), cfg.password());
        } else {
            c = DriverManager.getConnection("jdbc:sqlite:" + cfg.sqliteFile().toAbsolutePath());
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA busy_timeout=5000");
            }
        }
        all.add(c);
        return c;
    }

    private Connection connection() throws SQLException {
        Connection c = local.get();
        try {
            if (c == null || c.isClosed() || (cfg.mysql() && !c.isValid(2))) {
                if (c != null) {
                    all.remove(c);
                    try {
                        c.close();
                    } catch (SQLException ignored) {
                        // bo qua
                    }
                }
                c = open();
                local.set(c);
            }
        } catch (SQLException e) {
            local.remove();
            throw e;
        }
        return c;
    }

    public <T> CompletableFuture<T> supply(SqlFunction<T> fn) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return fn.apply(connection());
                } catch (SQLException e) {
                    throw new CompletionException(e);
                }
            }, executor);
        } catch (RuntimeException rejected) {
            return CompletableFuture.failedFuture(rejected);
        }
    }

    /** Chay dong bo tren luong DB va cho ket qua (dung khi khoi tao / tat plugin). */
    public <T> T await(SqlFunction<T> fn) throws SQLException {
        try {
            return supply(fn).get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() instanceof CompletionException ce && ce.getCause() != null
                    ? ce.getCause() : e.getCause();
            if (cause instanceof SQLException se) throw se;
            throw new SQLException(cause);
        } catch (InterruptedException | java.util.concurrent.TimeoutException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new SQLException("DB timeout/interrupted", e);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        for (Connection c : all) {
            try {
                c.close();
            } catch (SQLException e) {
                log.log(Level.FINE, "Dong ket noi loi", e);
            }
        }
        all.clear();
    }
}
