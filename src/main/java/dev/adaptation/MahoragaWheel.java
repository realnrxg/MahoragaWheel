package dev.adaptation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

public final class MahoragaWheel implements ClientModInitializer {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("mahoraga_wheel", "pipeline/wheel"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .withCull(false)
            .build();
    private WheelMesh mesh;
    private ByteBufferBuilder allocator;
    private MappableRingBuffer vertices;
    private final Path settingsFile = FabricLoader.getInstance().getConfigDir().resolve("mahoraga-wheel.properties");
    private WheelSettings settings = new WheelSettings(true, false);
    private Vector3f headAnchor;

    @Override
    public void onInitializeClient() {
        try {
            settings = WheelSettings.load(settingsFile);
        } catch (IOException | IllegalArgumentException e) {
            LoggerFactory.getLogger("mahoraga_wheel").warn("Could not load wheel settings; using enabled/gold", e);
        }
        try (var input = getClass().getResourceAsStream("/assets/mahoraga_wheel/wheel.stl")) {
            mesh = WheelMesh.read(input);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load Mahoraga wheel model", e);
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("mahoraga")
                        .then(literal("on").executes(context -> updateSettings(context.getSource(),
                                new WheelSettings(true, settings.white()))))
                        .then(literal("off").executes(context -> updateSettings(context.getSource(),
                                new WheelSettings(false, settings.white()))))
                        .then(literal("color")
                                .then(literal("white").executes(context -> updateSettings(context.getSource(),
                                        new WheelSettings(settings.enabled(), true))))
                                .then(literal("gold").executes(context -> updateSettings(context.getSource(),
                                        new WheelSettings(settings.enabled(), false)))))));
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatarRenderer) {
                helper.register(new RenderLayer<AvatarRenderState, PlayerModel>(avatarRenderer) {
                    @Override
                    public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                                       AvatarRenderState state, float yaw, float pitch) {
                        var player = Minecraft.getInstance().player;
                        if (player == null || state.id != player.getId()) return;
                        poses.pushPose();
                        try {
                            getParentModel().head.translateAndRotate(poses);
                            headAnchor = WheelAnchor.aboveHead(poses.last().pose());
                        } finally {
                            poses.popPose();
                        }
                    }
                });
            }
        });
        WorldRenderEvents.START_MAIN.register(context -> headAnchor = null);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(this::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (vertices != null) vertices.close();
            if (allocator != null) allocator.close();
        });
    }

    private int updateSettings(FabricClientCommandSource source, WheelSettings selected) {
        settings = selected;
        source.sendFeedback(Component.literal("Mahoraga wheel: " + (settings.enabled() ? "on" : "off")
                + ", color: " + (settings.white() ? "white" : "gold") + "."));
        try {
            settings.save(settingsFile);
        } catch (IOException e) {
            source.sendError(Component.literal("Wheel updated, but settings could not be saved for next launch."));
            LoggerFactory.getLogger("mahoraga_wheel").warn("Could not save wheel settings", e);
        }
        return 1;
    }

    private void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (!settings.enabled() || headAnchor == null || player == null || client.level == null || client.options.getCameraType().isFirstPerson()
                || client.getCameraEntity() != player || player.isInvisible() || player.isSpectator()
                || !player.isAlive() || player.isSleeping()) return;

        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var poses = context.matrices();
        poses.pushPose();
        // The player layer supplies the animated head position relative to the camera.
        poses.translate(headAnchor.x, headAnchor.y, headAnchor.z);
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees((player.tickCount + partialTick) * 1.5f % 360));
        try {
            draw(client, poses.last().pose());
        } finally {
            poses.popPose();
        }
    }

    private void draw(Minecraft client, Matrix4f transform) {
        if (allocator == null) allocator = new ByteBufferBuilder(mesh.triangles.length / 10 * 3 * 16);
        var builder = new BufferBuilder(allocator, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float[] triangles = mesh.triangles;
        for (int i = 0; i < triangles.length; i += 10) {
            float shade = triangles[i + 9];
            for (int v = 0; v < 3; v++) {
                int p = i + v * 3;
                builder.addVertex(transform, triangles[p], triangles[p + 1], triangles[p + 2])
                        .setColor((settings.white() ? 1f : 0.96f) * shade,
                                (settings.white() ? 1f : 0.73f) * shade,
                                (settings.white() ? 1f : 0.25f) * shade, 1f);
            }
        }
        try (var built = builder.buildOrThrow()) {
            int size = built.vertexBuffer().remaining();
            if (vertices == null) vertices = new MappableRingBuffer(() -> "Mahoraga wheel vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            try (var mapped = encoder.mapBuffer(vertices.currentBuffer().slice(0, size), false, true)) {
                MemoryUtil.memCopy(built.vertexBuffer(), mapped.data());
            }
            var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.TRIANGLES);
            var indexBuffer = indices.getBuffer(built.drawState().indexCount());
            var uniforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(),
                    new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
            var target = client.getMainRenderTarget();
            try (var pass = encoder.createRenderPass(() -> "Mahoraga wheel", target.getColorTextureView(),
                    OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", uniforms);
                pass.setVertexBuffer(0, vertices.currentBuffer());
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.drawIndexed(0, 0, built.drawState().indexCount(), 1);
            }
        }
        vertices.rotate();
    }
}
