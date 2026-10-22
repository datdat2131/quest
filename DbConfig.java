package vn.astrax.quest.storage;

import java.nio.file.Path;

public record DbConfig(boolean mysql, Path sqliteFile, String host, int port, String database,
                       String user, String password, int poolSize, String properties) {
}
