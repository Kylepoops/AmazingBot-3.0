package me.albert.amazingbot.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.albert.amazingbot.AmazingBot;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Properties;
import java.util.UUID;

import static java.sql.Statement.RETURN_GENERATED_KEYS;

public class MySQL {

    public static boolean ENABLED = false;

    public static HikariDataSource dataSource;
    private static String DATABASE;

    public static void setUP() {
        ENABLED = true;
        FileConfiguration cfg = AmazingBot.getMysqlSettings().getConfig();
        HikariConfig config = new HikariConfig();
        config.setPoolName("AmazingBot");
        config.setDriverClassName("com.mysql.jdbc.Driver");
        config.setUsername(cfg.getString("storage.username"));
        config.setPassword(cfg.getString("storage.password"));
        Properties properties = new Properties();
        String jdbcUrl = "jdbc:mysql://" + cfg.getString("storage.host") + ':' +
                cfg.getString("storage.port") + '/' + cfg.getString("storage.database");
        DATABASE = cfg.getString("storage.database");
        properties.setProperty("useSSL", cfg.getString("storage.useSSL"));
        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(1);
        properties.setProperty("date_string_format", "yyyy-MM-dd HH:mm:ss");
        config.setJdbcUrl(jdbcUrl);
        config.setDataSourceProperties(properties);
        dataSource = new HikariDataSource(config);
        try {
            createTables();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (!hasData()) {
            AmazingBot.getInstance().getPlugin().getLogger().info("§c检测到切换到MYSQL储存,且尚未有任何绑定数据,开始从yaml导入....");
            FileConfiguration data = AmazingBot.getData().getConfig();
            int imported = 0;
            for (String qq : data.getConfigurationSection("").getKeys(false)) {
                String uuid = data.getString(qq);
                imported++;
                MySQL.savePlayer(Long.parseLong(qq), uuid);
            }
            AmazingBot.getInstance().getPlugin().getLogger().info("§c已从YAML储存导入了" + imported + "条数据!");
        }
    }

    public static void createTables() throws SQLException {
        String file = "/create.sql";
        try (InputStream in = AmazingBot.getInstance().getClass().getResourceAsStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement()) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) continue;
                builder.append(line);
                if (line.endsWith(";")) {
                    String sql = builder.toString();
                    stmt.addBatch(sql);
                    builder = new StringBuilder();
                }
            }
            stmt.executeBatch();
        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    public static boolean hasData() {
        try (Connection con = dataSource.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT * FROM `" + DATABASE + "`.`binds` LIMIT 1;")) {
            try (ResultSet resultSet = stmt.executeQuery()) {
                if (resultSet.next()) {
                    return true;
                }
            }
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
        }
        return false;
    }

    public static void savePlayer(Long qq, String uuid) {
        if (getPlayer(qq) != null) {
            try (Connection con = dataSource.getConnection();
                 PreparedStatement stmt = con.prepareStatement("UPDATE  `" + DATABASE + "`.`binds` " +
                         "SET `qq`=?, `uuid`=? WHERE `qq`=?;", RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, qq);
                stmt.setString(2, uuid);
                stmt.setLong(3, qq);
                stmt.executeUpdate();
            } catch (SQLException sqlEx) {
                sqlEx.printStackTrace();
            }
            return;
        }
        if (getQQ(uuid) != null) {
            try (Connection con = dataSource.getConnection();
                 PreparedStatement stmt = con.prepareStatement("UPDATE  `" + DATABASE + "`.`binds` " +
                         "SET `qq`=?, `uuid`=? WHERE `uuid`=?;", RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, qq);
                stmt.setString(2, uuid);
                stmt.setLong(3, qq);
                stmt.executeUpdate();
            } catch (SQLException sqlEx) {
                sqlEx.printStackTrace();
            }
            return;
        }
        try (Connection con = dataSource.getConnection();
             PreparedStatement stmt = con.prepareStatement("INSERT INTO `" + DATABASE + "`.`binds` " +
                     "(`qq`, `uuid`)" +
                     "VALUES(?,?)  ON DUPLICATE KEY UPDATE  uuid=?;", RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, qq);
            stmt.setString(2, uuid);
            stmt.setString(3, uuid);
            stmt.executeUpdate();
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
        }
    }

    public static UUID getPlayer(Long qq) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT `id`, `qq`, `uuid` " +
                     "FROM `" + DATABASE + "`.`binds` WHERE  `qq`=?;")) {
            stmt.setLong(1, qq);
            try (ResultSet resultSet = stmt.executeQuery()) {
                if (resultSet.next()) {
                    String uuid = resultSet.getString("uuid");
                    return UUID.fromString(uuid);
                }
            }
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
        }
        return null;
    }

    public static void removePlayer(Long qq) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement stmt = con.prepareStatement("DELETE " +
                     "FROM `" + DATABASE + "`.`binds` WHERE  `qq`=?;")) {
            stmt.setLong(1, qq);
            stmt.executeUpdate();
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
        }
    }

    public static void removePlayer(String uuid) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement stmt = con.prepareStatement("DELETE " +
                     "FROM `" + DATABASE + "`.`binds` WHERE  `uuid`=?;")) {
            stmt.setString(1, uuid);
            stmt.executeUpdate();
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
        }
    }

    public static Long getQQ(String UUID) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT `id`, `qq`, `uuid` " +
                     "FROM `" + DATABASE + "`.`binds` WHERE  `uuid`=?;")) {
            stmt.setString(1, UUID);
            try (ResultSet resultSet = stmt.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("qq");
                }
            }
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
        }
        return null;
    }

    public static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }


}
