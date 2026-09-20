package com.ejemplo.chatbubbles;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Dibuja las burbujas arriba de la cabeza de cada jugador.
 *
 * La idea del truco:
 *   1. Nos movemos a la posicion del jugador (relativa a la camara)
 *   2. Subimos hasta arriba de su cabeza
 *   3. Rotamos el texto para que siempre mire a la camara (esto se llama "billboard")
 *   4. Lo achicamos (un caracter mide como 8 pixeles y un bloque mide 1, por eso el 0.025)
 *   5. Dibujamos los renglones de abajo hacia arriba
 */
public class BubbleRenderer {

	/** Separacion entre renglones, en "pixeles" de texto. */
	private static final float ALTO_RENGLON = 10f;

	/** Que tan arriba de la cabeza empieza la burbuja, en bloques. */
	private static final float ALTURA_EXTRA = 0.85f;

	public static void registrar() {
		// AFTER_ENTITIES = justo despues de que el juego dibujo a los jugadores.
		WorldRenderEvents.AFTER_ENTITIES.register(BubbleRenderer::dibujarTodo);
	}

	private static void dibujarTodo(WorldRenderContext contexto) {
		ModConfig cfg = ModConfig.get();
		if (!cfg.activado) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;
		if (mc.options.hudHidden) return; // F1 oculta la interfaz

		MatrixStack matrices = contexto.matrixStack();
		VertexConsumerProvider consumidores = contexto.consumers();
		Camera camara = contexto.camera();
		if (matrices == null || consumidores == null || camara == null) return;

		Vec3d posCamara = camara.getPos();
		float tickDelta = contexto.tickDelta();
		double distMax = cfg.distanciaMaxima;
		boolean primeraPersona = mc.options.getPerspective().isFirstPerson();

		for (AbstractClientPlayerEntity jugador : mc.world.getPlayers()) {
			// Tu propia burbuja solo se ve en tercera persona (F5), si no, te tapa la pantalla.
			if (jugador == mc.player && (primeraPersona || !cfg.mostrarMiPropiaBurbuja)) continue;
			if (jugador.isInvisibleTo(mc.player)) continue;
			if (jugador.isSpectator()) continue;

			List<ChatBubble> burbujas = BubbleManager.vivas(jugador.getUuid());
			if (burbujas.isEmpty()) continue;

			Vec3d posJugador = jugador.getLerpedPos(tickDelta);
			if (posJugador.squaredDistanceTo(posCamara) > distMax * distMax) continue;

			dibujarJugador(matrices, consumidores, camara, mc.textRenderer, cfg,
					posJugador.subtract(posCamara), jugador.getHeight(), burbujas);
		}

		// Volcamos a pantalla lo que se acumulo en el buffer.
		if (consumidores instanceof VertexConsumerProvider.Immediate inmediato) {
			inmediato.draw();
		}
	}

	private static void dibujarJugador(MatrixStack matrices, VertexConsumerProvider consumidores,
	                                   Camera camara, TextRenderer tr, ModConfig cfg,
	                                   Vec3d desplazamiento, float alturaJugador,
	                                   List<ChatBubble> burbujas) {

		long ahora = System.currentTimeMillis();

		// Primero juntamos todos los renglones de todas las burbujas del jugador,
		// para saber cuantos son y poder apilarlos bien.
		int totalRenglones = 0;
		for (ChatBubble b : burbujas) {
			totalRenglones += b.renglones(cfg.anchoMaximo).size();
		}
		if (totalRenglones == 0) return;

		matrices.push();
		matrices.translate(desplazamiento.x, desplazamiento.y + alturaJugador + ALTURA_EXTRA, desplazamiento.z);
		matrices.multiply(camara.getRotation());          // que siempre mire a la camara
		matrices.scale(-0.025f, -0.025f, 0.025f);         // pasar de pixeles de texto a bloques

		Matrix4f matriz = matrices.peek().getPositionMatrix();
		int luz = LightmapTextureManager.MAX_LIGHT_COORDINATE; // siempre bien iluminado
		TextRenderer.TextLayerType capa = cfg.verAtravesDeParedes
				? TextRenderer.TextLayerType.SEE_THROUGH
				: TextRenderer.TextLayerType.NORMAL;

		// Los renglones se dibujan de abajo (el mensaje mas nuevo) hacia arriba (el mas viejo).
		// En "y" negativo se va hacia arriba porque la escala de arriba invierte el eje.
		int indice = 0;
		for (ChatBubble burbuja : burbujas) {
			float alfa = burbuja.opacidad(ahora, cfg.segundosVisible);
			if (alfa <= 0.02f) {
				indice += burbuja.renglones(cfg.anchoMaximo).size();
				continue;
			}

			int alfaEntero = Math.round(alfa * 255f);
			int colorTexto = (alfaEntero << 24) | hexARGBsinAlfa(cfg.colorTextoHex, 0xFFFFFF);
			int colorFondo = (Math.round(alfaEntero * clamp(cfg.opacidadFondo)) << 24)
					| hexARGBsinAlfa(cfg.colorFondoHex, 0x000000);

			for (OrderedText renglon : burbuja.renglones(cfg.anchoMaximo)) {
				float y = -((totalRenglones - 1 - indice) * ALTO_RENGLON);
				float x = -tr.getWidth(renglon) / 2f;   // centrado sobre la cabeza
				tr.draw(renglon, x, y, colorTexto, false, matriz, consumidores, capa, colorFondo, luz);
				indice++;
			}
		}

		matrices.pop();
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	/**
	 * Convierte un color de texto tipo "#RRGGBB" a un entero 0xRRGGBB.
	 * Si el texto viene mal escrito (o vacio), usa porDefecto en su lugar
	 * en vez de tronar el juego.
	 */
	private static int hexARGBsinAlfa(String hex, int porDefecto) {
		if (hex == null || hex.isBlank()) return porDefecto;
		try {
			String limpio = hex.startsWith("#") ? hex.substring(1) : hex;
			return Integer.parseInt(limpio, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return porDefecto;
		}
	}
}
