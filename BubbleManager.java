package com.ejemplo.chatbubbles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Guarda que burbujas tiene cada jugador en este momento.
 *
 * Usa estructuras "Concurrent" porque se escribe desde el hilo del chat
 * y se lee desde el hilo de render al mismo tiempo.
 */
public class BubbleManager {

	private static final Map<UUID, Deque<ChatBubble>> BURBUJAS = new ConcurrentHashMap<>();

	public static void agregar(UUID jugador, ChatBubble burbuja) {
		Deque<ChatBubble> cola = BURBUJAS.computeIfAbsent(jugador, k -> new ConcurrentLinkedDeque<>());
		cola.addLast(burbuja);
		// Si se pasa del limite, tiramos la mas vieja.
		while (cola.size() > Math.max(1, ModConfig.get().maxBurbujasPorJugador)) {
			cola.pollFirst();
		}
	}

	/**
	 * Devuelve las burbujas vivas de un jugador (de la mas vieja a la mas nueva)
	 * y de paso limpia las que ya expiraron.
	 */
	public static List<ChatBubble> vivas(UUID jugador) {
		Deque<ChatBubble> cola = BURBUJAS.get(jugador);
		if (cola == null || cola.isEmpty()) return Collections.emptyList();

		long ahora = System.currentTimeMillis();
		int segundos = ModConfig.get().segundosVisible;

		ChatBubble primera;
		while ((primera = cola.peekFirst()) != null && primera.expirada(ahora, segundos)) {
			cola.pollFirst();
		}
		if (cola.isEmpty()) {
			BURBUJAS.remove(jugador);
			return Collections.emptyList();
		}
		return new ArrayList<>(cola);
	}

	/** Se llama al salir de un mundo o servidor. */
	public static void limpiarTodo() {
		BURBUJAS.clear();
	}
}
