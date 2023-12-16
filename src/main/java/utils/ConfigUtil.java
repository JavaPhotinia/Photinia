package utils;

import java.io.IOException;
import java.util.Properties;

public class ConfigUtil {
    private static Properties properties;

    public static Properties getProperties() {
        if (properties != null) {
            return properties;
        }
        try {
            properties = new Properties();
            properties.load(ConfigUtil.class.getClassLoader().getResourceAsStream("config.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties;
    }
}
