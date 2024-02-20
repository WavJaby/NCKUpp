package com.wavjaby.lib;

import com.wavjaby.Main;
import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

public class PropertiesReader extends Properties {
    private static final Logger logger = new Logger("PropertiesReader");
    private final File file;

    public PropertiesReader(String filePath) {
        file = new File(filePath);
        try {
            if (!file.exists()) {
                logger.log("Properties: \"" + filePath + "\" not found, create default");
                InputStream stream = PropertiesReader.class.getClassLoader().getResourceAsStream(filePath);
                if (stream == null) {
                    logger.log("Default properties: \"" + filePath + "\" not found");
                    return;
                }
                Files.copy(stream, file.toPath());
                stream.close();
            }
            InputStream in = Files.newInputStream(file.toPath());
            load(in);
            in.close();
        } catch (IOException e) {
            logger.errTrace(e);
        }
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        String value = (String) get(key);
        if (value == null) {
            value = defaultValue;
            logger.warn(file.getName() + " missing \"" + key + "\", using default: " + defaultValue);
        }
        return value;
    }

    public int getPropertyInt(String key, int defaultValue) {
        String value = (String) get(key);
        int intValue;
        if (value == null) {
            intValue = defaultValue;
            logger.warn(file.getName() + " missing \"" + key + "\", using default: " + defaultValue);
        } else
            try {
                intValue = Integer.parseInt(value);
            } catch (NumberFormatException ignore) {
                intValue = defaultValue;
                logger.warn(file.getName() + " \"" + key + "\": \"" + value + "\" is not integer, using default: " + defaultValue);
            }
        return intValue;
    }

    public boolean getPropertyBoolean(String key, boolean defaultValue) {
        String value = (String) get(key);
        boolean out;
        if (value == null) {
            out = defaultValue;
            logger.warn(file.getName() + " missing \"" + key + "\", using default: " + defaultValue);
        } else {
            if (value.equalsIgnoreCase("true"))
                out = true;
            else if (value.equalsIgnoreCase("false"))
                out = false;
            else {
                out = defaultValue;
                logger.warn(file.getName() + " \"" + key + "\": \"" + value + "\" is not integer, using default: " + defaultValue);
            }
        }
        return out;
    }
}
