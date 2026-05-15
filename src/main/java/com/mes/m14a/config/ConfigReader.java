package com.mes.m14a.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ConfigReader {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = open()) {
            PROPS.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.properties", e);
        }
    }

    /**
     * Looks for config.properties in: (1) classpath, (2) ./config.properties,
     * (3) ./src/main/resources/config.properties, (4) -Dconfig.file=<path>.
     * Falls back to filesystem so the framework still runs when Eclipse imports
     * the project as a plain Java project (and `src/main/resources` is not on the build path).
     */
    private static InputStream open() throws IOException {
        String override = System.getProperty("config.file");
        if (override != null && !override.isEmpty()) {
            return new FileInputStream(override);
        }
        InputStream cp = ConfigReader.class.getClassLoader()
                .getResourceAsStream("config.properties");
        if (cp != null) return cp;

        for (String path : new String[]{
                "config.properties",
                "src/main/resources/config.properties"}) {
            File f = new File(path);
            if (f.isFile()) return new FileInputStream(f);
        }
        throw new IllegalStateException(
                "config.properties not found. Either: " +
                "(a) import project as Maven so src/main/resources is on the classpath, " +
                "(b) right-click src/main/resources in Eclipse > Build Path > Use as Source Folder, or " +
                "(c) pass -Dconfig.file=<absolute path>.");
    }

    private ConfigReader() {}

    public static String get(String key) {
        String value = PROPS.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing config key: " + key);
        }
        return value;
    }

    public static String get(String key, String defaultValue) {
        return PROPS.getProperty(key, defaultValue);
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }
}
