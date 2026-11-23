package dev.privalo.achievements.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.privalo.achievements.data.PlayerData;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite (single server) or MySQL/MariaDB (network) storage through a HikariCP pool.
 */
public final class SqlStorage implements Storage {

    public enum Dialect { SQLITE, MYSQL }

    private final Dialect dialect;
    private final File dataFolder;
    private final ConfigurationSection config;
    private final String prefix;
    private HikariDataSource dataSource;

    public SqlStorage(Dialect dialect, File dataFolder, ConfigurationSection storageSection) {
        this.dialect = dialect;
        this.dataFolder = dataFolder;
        this.config = storageSection;
        this.prefix = storageSection.getString("table-prefix", "ra_").replaceAll("[^A-Za-z0-9_]", "");
    }

    @Override
    public void init() throws SQLException {
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("RadiantAchievements-" + dialect.name());
        if (dialect == Dialect.SQLITE) {
            File db = new File(dataFolder, config.getString("sqlite-file", "data.db"));
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setJdbcUrl("jdbc:sqlite:" + db.getAbsolutePath());
            hc.setMaximumPoolSize(1);
            hc.setConnectionInitSql("PRAGMA journal_mode=WAL");
        } else {
            ConfigurationSection my = config.getConfigurationSection("mysql");
            if (my == null) {
                throw new SQLException("storage.mysql section missing");
            }
            StringBuilder url = new StringBuilder("jdbc:mysql://")
                    .append(my.getString("host", "localhost")).append(':').append(my.getInt("port", 3306))
                    .append('/').append(my.getString("database", "minecraft"));
            ConfigurationSection props = my.getConfigurationSection("properties");
            if (props != null && !props.getKeys(false).isEmpty()) {
                char sep = '?';
                for (String key : props.getKeys(false)) {
                    url.append(sep).append(key).append('=').append(props.getString(key));
                    sep = '&';
                }
            }
            hc.setDriverClassName(mysqlDriver());
            hc.setJdbcUrl(url.toString());
            hc.setUsername(my.getString("username", "root"));
            hc.setPassword(my.getString("password", ""));
            hc.setMaximumPoolSize(Math.max(2, my.getInt("pool-size", 8)));
        }
        hc.setConnectionTimeout(10_000);
        dataSource = new HikariDataSource(hc);
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "players ("
                    + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(16) NOT NULL, updated_at BIGINT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "progress ("
                    + "uuid VARCHAR(36) NOT NULL, achievement VARCHAR(64) NOT NULL, progress BIGINT NOT NULL, "
                    + "PRIMARY KEY (uuid, achievement))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "unlocked ("
                    + "uuid VARCHAR(36) NOT NULL, achievement VARCHAR(64) NOT NULL, unlocked_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (uuid, achievement))");
        }
    }

    private static String mysqlDriver() {
        for (String driver : new String[]{"com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver", "org.mariadb.jdbc.Driver"}) {
            try {
                Class.forName(driver);
                return driver;
            } catch (ClassNotFoundException ignored) {
                // try next
            }
        }
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    public PlayerData load(UUID uuid, String name) throws SQLException {
        PlayerData data = new PlayerData(uuid, name);
        String id = uuid.toString();
        try (Connection c = dataSource.getConnection()) {
            if (name == null || name.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("SELECT name FROM " + prefix + "players WHERE uuid=?")) {
                    ps.setString(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            data.name(rs.getString(1));
                        }
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT achievement, progress FROM " + prefix + "progress WHERE uuid=?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        data.loadProgress(rs.getString(1), rs.getLong(2));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT achievement, unlocked_at FROM " + prefix + "unlocked WHERE uuid=?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        data.loadUnlocked(rs.getString(1), rs.getLong(2));
                    }
                }
            }
        }
        data.markClean();
        return data;
    }

    @Override
    public void save(PlayerData data) throws SQLException {
        String id = data.uuid().toString();
        try (Connection c = dataSource.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                for (String table : new String[]{"players", "progress", "unlocked"}) {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + prefix + table + " WHERE uuid=?")) {
                        ps.setString(1, id);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + prefix + "players (uuid, name, updated_at) VALUES (?,?,?)")) {
                    ps.setString(1, id);
                    ps.setString(2, data.name().length() > 16 ? data.name().substring(0, 16) : data.name());
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + prefix + "progress (uuid, achievement, progress) VALUES (?,?,?)")) {
                    for (Map.Entry<String, Long> e : data.progressView().entrySet()) {
                        ps.setString(1, id);
                        ps.setString(2, e.getKey());
                        ps.setLong(3, e.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + prefix + "unlocked (uuid, achievement, unlocked_at) VALUES (?,?,?)")) {
                    for (Map.Entry<String, Long> e : data.unlockedView().entrySet()) {
                        ps.setString(1, id);
                        ps.setString(2, e.getKey());
                        ps.setLong(3, e.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(auto);
            }
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public String name() {
        return dialect.name();
    }
}
