package dev.adaptation;

import org.joml.Matrix4f;
import org.joml.Vector3f;

final class WheelAnchorCheck {
    static void run() {
        for (float pitch : new float[] {0, 0.3f, 1.57f, -1.57f, 3.14f}) {
            for (float yaw : new float[] {0, 0.8f, 3.14f}) {
                var head = new Matrix4f().translation(4, 2, -3).rotateY(yaw).rotateX(pitch).scale(-0.9375f);
                var anchor = WheelAnchor.aboveHead(head);
                var center = head.transformPosition(0, -0.25f, 0, new Vector3f());
                assert Math.abs(anchor.x - center.x) < 0.0001f;
                assert Math.abs(anchor.z - center.z) < 0.0001f;
                float top = Float.NEGATIVE_INFINITY;
                for (float x : new float[] {-0.25f, 0.25f}) {
                    for (float y : new float[] {-0.5f, 0}) {
                        for (float z : new float[] {-0.25f, 0.25f}) {
                            top = Math.max(top, head.transformPosition(x, y, z, new Vector3f()).y);
                        }
                    }
                }
                assert Math.abs(anchor.y - top - 0.18f) < 0.0001f : "Wheel does not clear head";
            }
        }
        var flyingHead = new Matrix4f().translation(0, 0.6f, -0.9f).rotateX((float) Math.PI / 2);
        assert WheelAnchor.aboveHead(flyingHead).z < -1 : "Wheel stayed above hitbox instead of moving with head";
        System.out.println("Wheel anchor checks passed: head clearance, flying offset, yaw, pitch and scale.");
    }
}
