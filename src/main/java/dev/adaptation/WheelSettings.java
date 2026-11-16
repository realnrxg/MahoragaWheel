package dev.adaptation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

record WheelSettings(boolean enabled, boolean white) {
    static WheelSettings load(Path file) throws IOException {
        if (!Files.exists(file)) return new WheelSettings(true, false);
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        }
        boolean enabled = switch (properties.getProperty("enabled", "true")) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IOException("Invalid wheel enabled setting");
        };
        boolean white = switch (properties.getProperty("color", "gold")) {
            case "white" -> true;
            case "gold" -> false;
            default -> throw new IOException("Invalid wheel color setting");
        };
        return new WheelSettings(enabled, white);
    }

    void save(Path file) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("color", white ? "white" : "gold");
        Files.createDirectories(file.getParent());
        try (var output = Files.newOutputStream(file)) {
            properties.store(output, "Mahoraga wheel cosmetic settings");
        }
    }
}
