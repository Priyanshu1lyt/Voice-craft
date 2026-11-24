package com.nebrionverse.voicecraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.registry.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.*;
import java.util.concurrent.CompletableFuture;

public class VoiceCraftClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("voicecraft_ai");
    public static final RegistryIndex<Item> ITEMS = new RegistryIndex<>();
    public static final RegistryIndex<EntityType<?>> ENTITIES = new RegistryIndex<>();
    public static final RegistryIndex<StatusEffect> EFFECTS = new RegistryIndex<>();
    public static final EnchantmentManager ENCH = new EnchantmentManager();
    public static final VoiceRecognitionManager MANAGER = new VoiceRecognitionManager();
    public static KeyBinding talkKey, confirmKey, cancelKey;
    private boolean wasDown;

    @Override public void onInitializeClient() {
        VoiceCraftConfig.get();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("voicecraft_ai", "keys"));
        talkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.voicecraft_ai.talk", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, cat));
        confirmKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.voicecraft_ai.confirm", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, cat));
        cancelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.voicecraft_ai.cancel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, cat));

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.of("voicecraft_ai", "hud"), VoiceCraftHUD::render);
        CommandExecutor.init();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            VoiceCraftConfig cfg = VoiceCraftConfig.get();
            if (cfg.toggleMode) {
                while (talkKey.wasPressed()) {
                    if (MANAGER.mic().isRecording()) MANAGER.stop();
                    else if (mc.currentScreen == null) MANAGER.start(mc);
                }
            } else {
                while (talkKey.wasPressed()) { }
                boolean down = talkKey.isPressed() && mc.currentScreen == null;
                if (down && !wasDown) MANAGER.start(mc);
                if (!down && wasDown) MANAGER.stop();
                wasDown = down;
            }
            while (confirmKey.wasPressed()) MANAGER.confirm();
            while (cancelKey.wasPressed()) MANAGER.cancel();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var dyn = handler.getRegistryManager();
            CompletableFuture.runAsync(() -> {
                try {
                    ITEMS.build(Registries.ITEM, i -> i.getName());
                    ENTITIES.build(Registries.ENTITY_TYPE, e -> e.getName());
                    EFFECTS.build(Registries.STATUS_EFFECT, e -> e.getName());
                    ENCH.build(dyn.getOrThrow(RegistryKeys.ENCHANTMENT));
                    LOG.info("VoiceCraft indexed items, entities, effects and enchantments");
                } catch (Throwable t) { ErrorHandler.report(t); }
            });
            MANAGER.preload();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> MANAGER.shutdown());
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> { MANAGER.shutdown(); SpeechToTextEngine.closeAll(); });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof OptionsScreen)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("VoiceCraft AI Settings"),
                        b -> client.setScreen(new VoiceCraftConfigScreen(screen))).dimensions(6, 6, 140, 20).build());
        });
    }
}
