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

    public PropertiesReader(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                logger.log("Properties: \"" + filePath + "\" not found, create default");
                InputStream stream = Main.class.getResourceAsStream(filePath);
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

    public String getPropertyDefault(String key, String defaultValue) {
        String value = (String) get(key);
        if (value == null) {
            value = defaultValue;
            logger.warn("Property \"" + key + "\" not found, using default: " + defaultValue);
        }
        return value;
    }

    public int getPropertyIntDefault(String key, int defaultValue) {
        String value = (String) get(key);
        int intValue;
        if (value == null) {
            intValue = defaultValue;
            logger.warn("Property \"" + key + "\" not found, using default: " + defaultValue);
        } else
            try {
                intValue = Integer.parseInt(value);
            } catch (NumberFormatException ignore) {
                intValue = defaultValue;
                logger.warn("Property \"" + key + "\": \"" + value + "\" have wrong format, using default: " + defaultValue);
            }
        return intValue;
    }
}
