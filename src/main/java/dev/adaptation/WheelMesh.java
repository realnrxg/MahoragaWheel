package dev.adaptation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class WheelMesh {
    // Each triangle stores three Minecraft-space vertices followed by its gold shade.
    final float[] triangles;

    private WheelMesh(float[] triangles) { this.triangles = triangles; }

    static WheelMesh read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing wheel STL");
        byte[] bytes = input.readAllBytes();
        if (bytes.length < 84) throw new IOException("Truncated STL header");
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int count = data.getInt(80);
        if (count <= 0 || count > 100_000 || bytes.length != 84L + count * 50L)
            throw new IOException("Invalid binary STL triangle count");
        float[] triangles = new float[count * 10];
        float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int i = 0; i < count; i++) {
            data.position(84 + i * 50 + 12);
            for (int v = 0; v < 3; v++) {
                for (int axis = 0; axis < 3; axis++) {
                    float value = data.getFloat();
                    if (!Float.isFinite(value)) throw new IOException("Non-finite STL vertex");
                    triangles[i * 10 + v * 3 + axis] = value;
                    min[axis] = Math.min(min[axis], value);
                    max[axis] = Math.max(max[axis], value);
                }
            }
        }
        float width = Math.max(max[0] - min[0], max[1] - min[1]);
        if (!(width > 0)) throw new IOException("Empty STL bounds");
        float scale = 1.2f / width;
        for (int i = 0; i < count; i++) {
            int base = i * 10;
            for (int v = 0; v < 3; v++) {
                int p = base + v * 3;
                float x = triangles[p], y = triangles[p + 1], z = triangles[p + 2];
                triangles[p] = (x - (min[0] + max[0]) / 2) * scale;
                triangles[p + 1] = (z - min[2]) * scale;
                triangles[p + 2] = -(y - (min[1] + max[1]) / 2) * scale;
            }
            float ax = triangles[base + 3] - triangles[base];
            float ay = triangles[base + 4] - triangles[base + 1];
            float az = triangles[base + 5] - triangles[base + 2];
            float bx = triangles[base + 6] - triangles[base];
            float by = triangles[base + 7] - triangles[base + 1];
            float bz = triangles[base + 8] - triangles[base + 2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            triangles[base + 9] = length == 0 ? 0.6f
                    : 0.55f + 0.45f * Math.max(0, (nx * 0.3f + ny * 0.8f + nz * 0.52f) / length);
        }
        return new WheelMesh(triangles);
    }
}
