package dev.adaptation;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

final class WheelAnchor {
    static Vector3f aboveHead(Matrix4fc headTransform) {
        Vector3f center = headTransform.transformPosition(0, -0.25f, 0, new Vector3f());
        // The head is an eight-pixel cube: its transformed top depends on the flight and head rotations.
        center.y += 0.25f * (Math.abs(headTransform.m01()) + Math.abs(headTransform.m11())
                + Math.abs(headTransform.m21())) + 0.18f;
        return center;
    }
}
