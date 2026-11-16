package dev.adaptation;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class WheelMeshCheck {
    public static void main(String[] args) throws Exception {
        WheelSettingsCheck.run();
        WheelAnchorCheck.run();
        WheelMesh mesh;
        try (var input = WheelMesh.class.getResourceAsStream("/assets/mahoraga_wheel/wheel.stl")) {
            mesh = WheelMesh.read(input);
        }
        assert mesh.triangles.length == 6064 * 10;
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < mesh.triangles.length; i += 10) {
            assert mesh.triangles[i + 9] >= 0.55f && mesh.triangles[i + 9] <= 1.001f;
            for (int v = 0; v < 3; v++) {
                minX = Math.min(minX, mesh.triangles[i + v * 3]);
                maxX = Math.max(maxX, mesh.triangles[i + v * 3]);
                assert mesh.triangles[i + v * 3 + 1] >= 0;
                assert mesh.triangles[i + v * 3 + 1] < 0.18f : "Spindle protrudes above wheel";
            }
        }
        assert Math.abs(maxX - minX - 1.2f) < 0.0001f;
        for (byte[] invalid : new byte[][] {new byte[0], new byte[84], new byte[100]}) {
            try {
                WheelMesh.read(new ByteArrayInputStream(invalid));
                throw new AssertionError("Accepted invalid STL");
            } catch (IOException expected) { }
        }
        System.out.println("Wheel mesh checks passed: 6,064 triangles, no spindle, 1.2-block width, valid shading and STL rejection.");
    }
}
