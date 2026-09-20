package com.ejemplo.chatbubbles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada del mod.
 *
 * Es un mod SOLO DE CLIENTE: funciona en cualquier servidor (vanilla, Paper,
 * Spigot, un realm, lo que sea) sin que el servidor tenga que instalar nada.
 * Lee el chat que ya te llega y lo dibuja arriba de las cabezas.
 */
@Environment(EnvType.CLIENT)
public class ChatBubblesClient implements ClientModInitializer {

	public static final String MOD_ID = "chatbubbles";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ModConfig.cargar();        // lee config/chatbubbles.json
		ChatListener.registrar();  // escucha los mensajes del chat
		BubbleRenderer.registrar();// dibuja las burbujas en el mundo
		Teclas.registrar();        // tecla B para prender/apagar, O para el menu
		LOGGER.info("[ChatBubbles] Listo. Diseñado por ItsBrauu.");
	}
}
