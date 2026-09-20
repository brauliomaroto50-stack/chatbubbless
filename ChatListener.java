package com.ejemplo.chatbubbles;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Se engancha al chat que le llega al cliente y convierte cada mensaje
 * en una burbuja para el jugador que lo escribio.
 *
 * Hay dos casos:
 *   1. CHAT  -> mensaje normal de jugador. El juego ya nos dice quien lo mando. Facil.
 *   2. GAME  -> mensaje "de sistema". Muchos servidores con plugins mandan el chat
 *               asi (formateado, con rangos, colores...). Ahi hay que adivinar el
 *               nombre leyendo el texto.
 */
public class ChatListener {

	/** Quita los codigos de color viejos tipo §a §l */
	private static final Pattern CODIGOS_COLOR = Pattern.compile("\u00a7[0-9a-fk-orA-FK-OR]");

	/** Formato clasico de vanilla:  <Pepe> hola */
	private static final Pattern VANILLA = Pattern.compile("^<([A-Za-z0-9_]{1,16})>\\s*(.+)$");

	public static void registrar() {

		// --- Caso 1: mensaje de chat firmado por un jugador ---
		ClientReceiveMessageEvents.CHAT.register((mensaje, mensajeFirmado, remitente, parametros, momento) -> {
			if (!ModConfig.get().activado) return;
			if (remitente == null) return;
			String texto = limpiar(mensaje.getString());
			// El mensaje puede venir ya formateado con el nombre incluido: se lo quitamos.
			texto = quitarPrefijoDeNombre(texto, remitente.getName());
			crearBurbuja(remitente.getId(), texto);
		});

		// --- Caso 2: mensaje de sistema (servidores con plugins) ---
		ClientReceiveMessageEvents.GAME.register((mensaje, sobreImpreso) -> {
			if (!ModConfig.get().activado) return;
			if (sobreImpreso) return; // esto es la barra de accion, no el chat
			String texto = limpiar(mensaje.getString());

			Matcher m = VANILLA.matcher(texto);
			if (m.matches()) {
				asignarPorNombre(m.group(1), m.group(2));
				return;
			}
			// Formato libre:  [VIP] Pepe: hola   /   Pepe » hola
			int corte = indiceDelSeparador(texto);
			if (corte > 0) {
				String prefijo = texto.substring(0, corte);
				String cuerpo = texto.substring(corte + 1).trim();
				if (!cuerpo.isEmpty()) {
					String nombre = buscarNombreConocidoEn(prefijo);
					if (nombre != null) asignarPorNombre(nombre, cuerpo);
				}
			}
		});

		// Al desconectarte, se borran todas las burbujas.
		ClientPlayConnectionEvents.DISCONNECT.register((manejador, cliente) -> BubbleManager.limpiarTodo());
	}

	// ------------------------------------------------------------------

	private static void crearBurbuja(UUID jugador, String texto) {
		if (texto == null || texto.isBlank()) return;

		ChatBubble burbuja = new ChatBubble(texto);
		BubbleManager.agregar(jugador, burbuja);

		// La traduccion se pide aparte y se rellena sola cuando llega la respuesta.
		ModConfig cfg = ModConfig.get();
		String modo = cfg.mostrar == null ? "ambos" : cfg.mostrar;
		if (cfg.traducir && !modo.equals("original") && texto.length() <= cfg.largoMaximoTraduccion) {
			Translator.traducirAsync(texto, burbuja::setTraduccion);
		}
	}

	/** Busca al jugador por su nombre visible y le pone la burbuja. */
	private static void asignarPorNombre(String nombre, String texto) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) return;
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			if (p.getGameProfile().getName().equalsIgnoreCase(nombre)) {
				crearBurbuja(p.getUuid(), texto);
				return;
			}
		}
	}

	/** Devuelve el nombre de un jugador conectado que aparezca dentro del texto. */
	private static String buscarNombreConocidoEn(String fragmento) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) return null;
		String enMinusculas = fragmento.toLowerCase();
		String mejor = null;
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			String n = p.getGameProfile().getName();
			if (enMinusculas.contains(n.toLowerCase())) {
				// Nos quedamos con el nombre mas largo que coincida (evita confundir "Ana" con "Anabel")
				if (mejor == null || n.length() > mejor.length()) mejor = n;
			}
		}
		return mejor;
	}

	/** Posicion del primer ":" o "»" que separa el nombre del mensaje. */
	private static int indiceDelSeparador(String texto) {
		int a = texto.indexOf(':');
		int b = texto.indexOf('\u00bb');
		if (a < 0) return b;
		if (b < 0) return a;
		return Math.min(a, b);
	}

	private static String limpiar(String s) {
		return CODIGOS_COLOR.matcher(s).replaceAll("").trim();
	}

	/** De "<Pepe> hola" o "Pepe: hola" saca solamente "hola". */
	private static String quitarPrefijoDeNombre(String texto, String nombre) {
		Matcher m = VANILLA.matcher(texto);
		if (m.matches()) return m.group(2).trim();

		int i = texto.indexOf(nombre);
		if (i >= 0) {
			int corte = indiceDelSeparador(texto.substring(i));
			if (corte > 0) return texto.substring(i + corte + 1).trim();
		}
		return texto;
	}
}
