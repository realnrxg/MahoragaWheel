package dev.adaptation;

import java.io.IOException;
import java.nio.file.Files;

final class WheelSettingsCheck {
    static void run() throws Exception {
        var directory = Files.createTempDirectory("mahoraga-settings-check");
        var file = directory.resolve("wheel.properties");
        try {
            assert WheelSettings.load(file).equals(new WheelSettings(true, false));
            for (boolean enabled : new boolean[] {true, false}) {
                for (boolean white : new boolean[] {true, false}) {
                    var settings = new WheelSettings(enabled, white);
                    settings.save(file);
                    assert WheelSettings.load(file).equals(settings);
                }
            }
            for (String invalid : new String[] {"enabled=maybe", "color=purple"}) {
                Files.writeString(file, invalid);
                try {
                    WheelSettings.load(file);
                    throw new AssertionError("Accepted invalid settings");
                } catch (IOException expected) { }
            }
            System.out.println("Wheel settings checks passed: defaults, all on/off and color combinations, invalid values.");
        } finally {
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
    }
}
