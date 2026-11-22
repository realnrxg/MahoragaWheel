package dev.adaptation;

import java.io.IOException;
import java.nio.file.Path;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import org.joml.Vector3f;

public final class MahoragaWheel implements ClientModInitializer {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("mahoraga_wheel", "pipeline/wheel"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();
    private static final RenderType WHEEL = RenderType.create("mahoraga_wheel", RenderSetup.builder(PIPELINE).createRenderSetup());

    private WheelMesh mesh;
    private final Path settingsFile = FabricLoader.getInstance().getConfigDir().resolve("mahoraga-wheel.properties");
    private WheelSettings settings = new WheelSettings(true, false);

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
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatarRenderer) {
                helper.register(new RenderLayer<AvatarRenderState, PlayerModel>(avatarRenderer) {
                    @Override
                    public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                                       AvatarRenderState state, float yaw, float pitch) {
                        Minecraft client = Minecraft.getInstance();
                        var player = client.player;
                        if (!settings.enabled() || player == null || state.id != player.getId()
                                || client.options.getCameraType().isFirstPerson() || client.getCameraEntity() != player
                                || player.isInvisible() || player.isSpectator() || !player.isAlive() || player.isSleeping()) {
                            return;
                        }

                        poses.pushPose();
                        try {
                            getParentModel().head.translateAndRotate(poses);
                            Vector3f anchor = WheelAnchor.aboveHead(poses.last().pose());
                            // Follow the head position while keeping the spin upright in world space.
                            poses.setIdentity();
                            poses.translate(anchor.x, anchor.y, anchor.z);
                            float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                            poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees((player.tickCount + partialTick) * 1.5f % 360));
                            boolean white = settings.white();
                            collector.submitCustomGeometry(poses, WHEEL, (pose, consumer) -> draw(pose, consumer, white));
                        } finally {
                            poses.popPose();
                        }
                    }
                });
            }
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

    private void draw(PoseStack.Pose pose, VertexConsumer consumer, boolean white) {
        float[] triangles = mesh.triangles;
        for (int i = 0; i < triangles.length; i += 10) {
            float shade = triangles[i + 9];
            for (int v = 0; v < 3; v++) {
                int p = i + v * 3;
                consumer.addVertex(pose, triangles[p], triangles[p + 1], triangles[p + 2])
                        .setColor((white ? 1f : 0.96f) * shade,
                                (white ? 1f : 0.73f) * shade,
                                (white ? 1f : 0.25f) * shade, 1f);
            }
        }
    }
}
