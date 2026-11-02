package util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads settings from backend/config.properties.
 * An environment variable (db.url -> DB_URL) overrides the file.
 */
public final class Config {

    private static final Properties PROPS = new Properties();

    static {
        String[] candidates = {
                System.getProperty("cc.config", "backend/config.properties"),
                "config.properties",
                "../backend/config.properties"
        };
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    PROPS.load(in);
                    System.out.println("Loaded settings from " + path.toAbsolutePath().normalize());
                } catch (IOException e) {
                    System.err.println("Could not read " + path + ": " + e.getMessage());
                }
                break;
            }
        }
    }

    private Config() {
    }

    public static String get(String key, String fallback) {
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return PROPS.getProperty(key, fallback);
    }

    public static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
