package com.mes.m14a.reporting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads JSON request templates (e.g. login.json) from the classpath or local
 * filesystem, and writes session artifacts (e.g. session.json holding the
 * captured Hwd token) to {@code target/}.
 */
public final class JsonResource {

    private JsonResource() {}

    /** Read a JSON file. Looks on classpath first, then ./, then ./src/main/resources/. */
    public static String read(String name) {
        try (InputStream in = open(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + name, e);
        }
    }

    private static InputStream open(String name) throws IOException {
        InputStream cp = JsonResource.class.getClassLoader().getResourceAsStream(name);
        if (cp != null) return cp;
        for (String path : new String[]{name, "src/main/resources/" + name}) {
            File f = new File(path);
            if (f.isFile()) return Files.newInputStream(f.toPath());
        }
        throw new IOException(name + " not found on classpath or in src/main/resources");
    }

    /** Write a JSON snippet under target/ (created if missing). */
    public static Path writeUnderTarget(String fileName, String content) {
        try {
            Path dir = Paths.get("target");
            Files.createDirectories(dir);
            Path out = dir.resolve(fileName);
            Files.write(out, content.getBytes(StandardCharsets.UTF_8));
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write " + fileName, e);
        }
    }
}
