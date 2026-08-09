package net.fabricmc.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClientMod implements ClientModInitializer {
    public static final String MOD_ID = "customclient";
    public static ModuleManager moduleManager;
    private static KeyBinding guiKeyBind;

    @Override
    public void onInitializeClient() {
        moduleManager = new ModuleManager();

        guiKeyBind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.customclient.open_gui", 
                InputUtil.Type.KEYSYM, 
                GLFW.GLFW_KEY_RIGHT_SHIFT, 
                "category.customclient.keys"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && guiKeyBind.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ClickGuiScreen(moduleManager));
                }
            }
        });
    }
}

abstract class Module {
    private final String name;
    private final String category;
    private boolean enabled;
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    public Module(String name, String category) {
        this.name = name;
        this.category = category;
        this.enabled = false;
    }

    public void toggle() {
        this.enabled = !this.enabled;
        if (this.enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    protected void onEnable() {}
    protected void onDisable() {}

    public String getName() { return name; }
    public String getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
}

class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    public static final Set<ChunkPos> suspiciousChunks = new HashSet<>();

    public ModuleManager() {
        modules.add(new Module("Freecam", "Movement") {
            private double oldX, oldY, oldZ;
            private boolean oldFlying;

            @Override
            protected void onEnable() {
                if (mc.player == null) return;
                oldX = mc.player.getX();
                oldY = mc.player.getY();
                oldZ = mc.player.getZ();
                oldFlying = mc.player.getAbilities().flying;
                
                mc.player.getAbilities().flying = true;
                mc.player.noClip = true;
            }

            @Override
            protected void onDisable() {
                if (mc.player == null) return;
                mc.player.setPosition(oldX, oldY, oldZ);
                mc.player.getAbilities().flying = oldFlying;
                mc.player.noClip = false;
                mc.player.setVelocity(0, 0, 0);
            }
        });

        modules.add(new Module("Chest ESP", "Render") {
            public boolean shouldHighlight(BlockEntity entity) {
                return isEnabled() && entity instanceof ChestBlockEntity;
            }
        });

        modules.add(new Module("Shulker ESP", "Render") {
            public boolean shouldHighlight(BlockEntity entity) {
                return isEnabled() && entity instanceof ShulkerBoxBlockEntity;
            }
        });

        modules.add(new Module("Sus Chunks", "Exploit") {
            @Override
            protected void onEnable() {
                suspiciousChunks.clear();
                ClientPlayNetworking.registerGlobalReceiver(ChunkDataS2CPacket.class, (packet, player, responseSender) -> {
                    if (!isEnabled()) return;
                    ChunkPos pos = new ChunkPos(packet.getX(), packet.getZ());
                    if (packet.isFullChunk()) { 
                        suspiciousChunks.add(pos);
                    }
                });
            }

            @Override
            protected void onDisable() {
                suspiciousChunks.clear();
            }
        });
    }

    public List<Module> getModules() { return modules; }

    public List<Module> getModulesByCategory(String category) {
        List<Module> categoryModules = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory().equalsIgnoreCase(category)) {
                categoryModules.add(m);
            }
        }
        return categoryModules;
    }
}

class ClickGuiScreen extends Screen {
    private final ModuleManager moduleManager;
    private final String[] categories = {"Movement", "Render", "Exploit"};
    
    private final int panelWidth = 100;
    private final int panelHeight = 20;
    private final int buttonHeight = 18;

    public ClickGuiScreen(ModuleManager moduleManager) {
        super(Text.of("67Client UI"));
        this.moduleManager = moduleManager;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        int startX = 20;
        int startY = 20;

        for (String category : categories) {
            context.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0xFF121212);
            context.drawText(this.textRenderer, category, startX + 6, startY + 6, 0xFFFFFFFF, false);

            List<Module> modules = moduleManager.getModulesByCategory(category);
            int currentY = startY + panelHeight;

            for (Module m : modules) {
                int buttonColor = m.isEnabled() ? 0xFF00ADB5 : 0xFF222831;
                
                context.fill(startX, currentY, startX + panelWidth, currentY + buttonHeight, buttonColor);
                context.drawText(this.textRenderer, m.getName(), startX + 8, currentY + 5, 0xFFEEEEEE, false);
                
                currentY += buttonHeight;
            }
            startX += panelWidth + 15;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int startX = 20;
            int startY = 20;

            for (String category : categories) {
                List<Module> modules = moduleManager.getModulesByCategory(category);
                int currentY = startY + panelHeight;

                for (Module m : modules) {
                    if (mouseX >= startX && mouseX <= startX + panelWidth && mouseY >= currentY && mouseY <= currentY + buttonHeight) {
                        m.toggle();
                        return true;
                    }
                    currentY += buttonHeight;
                }
                startX += panelWidth + 15;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
