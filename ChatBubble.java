package com.ejemplo.chatbubbles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Una burbuja = un mensaje de chat de un jugador.
 * Guarda el texto original y, cuando llega, la traduccion.
 */
public class ChatBubble {

	public final String textoOriginal;
	public final long creadaEn = System.currentTimeMillis();

	/** volatile porque la traduccion llega desde otro hilo (el de la red). */
	private volatile String traduccion = null;

	/** Renglones ya cortados, para no recalcularlos en cada frame (60 veces por segundo). */
	private List<OrderedText> cacheRenglones = null;
	private int cacheAncho = -1;
	private boolean cacheTeniaTraduccion = false;

	public ChatBubble(String textoOriginal) {
		this.textoOriginal = textoOriginal;
	}

	public void setTraduccion(String t) {
		this.traduccion = t;
		this.cacheRenglones = null; // invalidar el cache para que se redibuje
	}

	public boolean expirada(long ahora, int segundos) {
		return ahora - creadaEn > segundos * 1000L;
	}

	/** 0.0 a 1.0 — se usa para que la burbuja se desvanezca al final. */
	public float opacidad(long ahora, int segundos) {
		long vida = ahora - creadaEn;
		long total = segundos * 1000L;
		long desvanecer = 800L;
		if (vida > total - desvanecer) {
			return Math.max(0f, (total - vida) / (float) desvanecer);
		}
		return 1f;
	}

	/**
	 * Devuelve el texto ya partido en renglones que caben en el ancho configurado.
	 */
	public List<OrderedText> renglones(int anchoMax) {
		boolean hayTrad = traduccion != null;
		if (cacheRenglones != null && cacheAncho == anchoMax && cacheTeniaTraduccion == hayTrad) {
			return cacheRenglones;
		}

		var tr = MinecraftClient.getInstance().textRenderer;
		ModConfig cfg = ModConfig.get();
		List<OrderedText> salida = new ArrayList<>();

		String modo = cfg.mostrar == null ? "ambos" : cfg.mostrar;
		boolean verOriginal = !modo.equals("traduccion") || !hayTrad;
		boolean verTraduccion = hayTrad && !modo.equals("original");

		if (verOriginal) {
			salida.addAll(tr.wrapLines(Text.literal(textoOriginal), anchoMax));
		}
		if (verTraduccion) {
			// La traduccion va en gris y en cursiva para distinguirla del original.
			Text t = Text.literal(traduccion).formatted(Formatting.GRAY, Formatting.ITALIC);
			salida.addAll(tr.wrapLines(t, anchoMax));
		}

		cacheRenglones = salida;
		cacheAncho = anchoMax;
		cacheTeniaTraduccion = hayTrad;
		return salida;
	}
}
