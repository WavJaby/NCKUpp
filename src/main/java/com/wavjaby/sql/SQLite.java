package com.wavjaby.sql;

import com.wavjaby.Module;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

public class SQLite implements Module {
    private static final String TAG = "SQLite";
    private static final Logger logger = new Logger(TAG);
    private java.sql.Connection sqlite;

    public static void printSqlError(SQLException e) {
        logger.err(e.getClass().getName() + ": " + e.getMessage() + '\n' +
                "\tat " + Arrays.stream(e.getStackTrace())
                .filter(i -> !i.getClassName().startsWith("org.sqlite"))
                .map(StackTraceElement::toString)
                .collect(Collectors.joining("\n\tat "))
        );
    }

    @Override
    public void start() {
        String database = "./database.db";
        String driverJar = "./sqlite-jdbc-3.40.0.0.jar";
        String driverClassname = "org.sqlite.JDBC";
        String databaseProperties = "./database.properties";
        try {
            File databaseFile = new File(database);
            if (!databaseFile.exists()) {
                logger.err("Database file not found! " + databaseFile.getAbsolutePath());
                return;
            }
            logger.log("Connecting to database: " + databaseFile.getAbsolutePath());

            // Load sqlite driver
            File driverFile = new File(driverJar);
            if (!driverFile.exists()) {
                logger.err("Driver not found! " + driverFile.getAbsolutePath());
                return;
            }
            URLClassLoader ucl = new URLClassLoader(new URL[]{driverFile.toURI().toURL()});
            Driver driver = (Driver) Class.forName(driverClassname, true, ucl).newInstance();
            DriverManager.registerDriver(new DriverShim(driver));


            // Load database properties
            Properties props = new Properties();
            File propsFile = new File(databaseProperties);
            if (!propsFile.exists()) {
                logger.err("Database properties not found! " + propsFile.getAbsolutePath());
                return;
            }
            props.load(Files.newInputStream(propsFile.toPath()));

            // Connect to database
            sqlite = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath(), props);
        } catch (SQLException e) {
            SQLite.printSqlError(e);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IOException e) {
            logger.errTrace(e);
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public String getTag() {
        return TAG;
    }

    public Connection getDatabase() {
        return sqlite;
    }

    @Override
    public boolean api() {
        return false;
    }
}
