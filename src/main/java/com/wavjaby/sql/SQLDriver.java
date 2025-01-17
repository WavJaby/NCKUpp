package com.wavjaby.sql;

import com.wavjaby.Module;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class SQLDriver implements Module {
    private static final String TAG = "SQLDriver";
    private static final Logger logger = new Logger(TAG);
    private final String databasePath, connectionUrl, driverPath, driverClass, errorFilter;
    private final String user, password;
    private java.sql.Connection connection;

    public SQLDriver(String databasePath, String connectionUrl, String driverPath, String driverClass,
                     String user, String password) {
        this.databasePath = databasePath;
        this.connectionUrl = connectionUrl;
        this.driverPath = driverPath;
        this.driverClass = driverClass;
        this.user = user;
        this.password = password;

        int index = driverClass.lastIndexOf('.');
        if (index == -1) {
            logger.warn("error class filter not found");
            this.errorFilter = "";
        } else
            this.errorFilter = driverClass.substring(0, index);
    }

    public void printStackTrace(SQLException e) {
        logger.err(e.getClass().getName() + ": " + e.getMessage() + '\n' +
                   "\tat " + Arrays.stream(e.getStackTrace())
                           .filter(i -> !i.getClassName().startsWith(errorFilter))
                           .map(StackTraceElement::toString)
                           .collect(Collectors.joining("\n\tat "))
        );
    }

    @Override
    public void start() {
        try {
            File databaseFile = new File(databasePath);
            if (!databaseFile.exists()) {
                logger.warn("Database file not exist: " + databaseFile.getAbsolutePath());
                if (!databaseFile.createNewFile()) {
                    logger.err("Failed to create database file: " + databaseFile.getAbsolutePath());
                    return;
                }
            }
            logger.log("Connecting to database: " + databaseFile.getAbsolutePath());

            // Load driver
            File driverFile = new File(driverPath);
            if (!driverFile.exists()) {
                logger.err("Driver not found! " + driverFile.getAbsolutePath());
                return;
            }
            URLClassLoader ucl = new URLClassLoader(new URL[]{driverFile.toURI().toURL()});
            Driver driver = (Driver) Class.forName(driverClass, true, ucl).newInstance();
            DriverManager.registerDriver(new DriverShim(driver));

            // Connect to database
            connection = DriverManager.getConnection(connectionUrl, user, password);
        } catch (SQLException e) {
            printStackTrace(e);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IOException e) {
            logger.errTrace(e);
        }
    }

    @Override
    public void stop() {
        if (connection == null)
            return;
        try {
            connection.close();
        } catch (SQLException e) {
            logger.errTrace(e);
        }
    }

    @Override
    public String getTag() {
        return TAG;
    }

    public Connection getDatabase() {
        return connection;
    }

    @Override
    public boolean api() {
        return false;
    }
}
