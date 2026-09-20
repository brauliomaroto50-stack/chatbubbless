package com.ejemplo.chatbubbles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Atajos de teclado. Se pueden cambiar desde Opciones > Controles.
 *   B          -> prender / apagar las burbujas
 *   Ctrl + B   -> prender / apagar la traduccion
 */
public class Teclas {

	private static KeyBinding teclaBurbujas;
	private static KeyBinding teclaTraduccion;
	private static KeyBinding teclaMenu;

	public static void registrar() {
		teclaBurbujas = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.chatbubbles.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_B,
				"category.chatbubbles"
		));

		teclaTraduccion = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.chatbubbles.toggle_translate",
				InputUtil.Type.KEYSYM,
				InputUtil.UNKNOWN_KEY.getCode(), // sin tecla asignada por defecto
				"category.chatbubbles"
		));

		teclaMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.chatbubbles.open_menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_O,
				"category.chatbubbles"
		));

		ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
			while (teclaBurbujas.wasPressed()) {
				ModConfig cfg = ModConfig.get();
				cfg.activado = !cfg.activado;
				ModConfig.guardar();
				avisar(cliente, cfg.activado
						? Text.translatable("msg.chatbubbles.on")
						: Text.translatable("msg.chatbubbles.off"));
			}
			while (teclaTraduccion.wasPressed()) {
				ModConfig cfg = ModConfig.get();
				cfg.traducir = !cfg.traducir;
				ModConfig.guardar();
				avisar(cliente, cfg.traducir
						? Text.translatable("msg.chatbubbles.translate_on")
						: Text.translatable("msg.chatbubbles.translate_off"));
			}
			while (teclaMenu.wasPressed()) {
				if (cliente.currentScreen == null) {
					cliente.setScreen(new PantallaConfiguracion(null));
				}
			}
		});
	}

	private static void avisar(net.minecraft.client.MinecraftClient cliente, Text texto) {
		if (cliente.player != null) {
			cliente.player.sendMessage(texto, true); // true = en la barra de accion
		}
	}
}
