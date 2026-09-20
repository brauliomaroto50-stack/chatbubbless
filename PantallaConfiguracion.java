package com.ejemplo.chatbubbles;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.Util;
import net.minecraft.text.Text;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Menu de configuracion del mod, dentro del juego. Se abre con la tecla O
 * (configurable en Opciones > Controles > Burbujas de Chat).
 *
 * Todo lo que se cambia aqui se guarda al presionar "Guardar y cerrar" o al
 * salir con Esc. No hace falta editar el chatbubbles.json a mano si no quieres.
 */
public class PantallaConfiguracion extends Screen {

	private static final String[] IDIOMAS = {"es", "en", "fr", "de", "it", "pt", "ru", "ja"};
	private static final String[] MODOS_MOSTRAR = {"ambos", "traduccion", "original"};
	private static final String[] TEMAS = {"oscuro", "neon", "retro", "claro"};

	private final Screen pantallaAnterior;
	private TextFieldWidget campoUrl;

	/** Filas de solo texto (numeros) que se dibujan a mano en render(), no son botones. */
	private final List<FilaNumerica> filasNumericas = new ArrayList<>();

	private record FilaNumerica(String etiqueta, Supplier<String> valor, int y) {
	}

	public PantallaConfiguracion(Screen pantallaAnterior) {
		super(Text.literal("Burbujas de Chat - Configuración"));
		this.pantallaAnterior = pantallaAnterior;
	}

	@Override
	protected void init() {
		filasNumericas.clear();
		ModConfig cfg = ModConfig.get();

		int centroX = this.width / 2;
		int anchoBtn = 220;
		int alto = 20;
		int y = 26;

		y = agregarBooleano(y, centroX, anchoBtn, alto, "Burbujas activas",
				() -> cfg.activado, v -> cfg.activado = v);

		y = agregarBooleano(y, centroX, anchoBtn, alto, "Traducción automática",
				() -> cfg.traducir, v -> cfg.traducir = v);

		y = agregarCiclo(y, centroX, anchoBtn, alto, "Mostrar", MODOS_MOSTRAR,
				() -> cfg.mostrar, v -> cfg.mostrar = v);

		y = agregarCiclo(y, centroX, anchoBtn, alto, "Idioma destino", IDIOMAS,
				() -> cfg.idiomaDestino, v -> cfg.idiomaDestino = v);

		y = agregarCiclo(y, centroX, anchoBtn, alto, "Tema", TEMAS,
				() -> cfg.tema, ModConfig::aplicarTema);

		y = agregarNumerica(y, centroX, alto, "Duración (segundos)",
				() -> String.valueOf(cfg.segundosVisible),
				() -> { if (cfg.segundosVisible > 3) cfg.segundosVisible--; },
				() -> { if (cfg.segundosVisible < 20) cfg.segundosVisible++; });

		y = agregarNumerica(y, centroX, alto, "Distancia máxima (bloques)",
				() -> String.valueOf(cfg.distanciaMaxima),
				() -> { if (cfg.distanciaMaxima > 8) cfg.distanciaMaxima -= 8; },
				() -> { if (cfg.distanciaMaxima < 128) cfg.distanciaMaxima += 8; });

		y = agregarNumerica(y, centroX, alto, "Máx. burbujas por jugador",
				() -> String.valueOf(cfg.maxBurbujasPorJugador),
				() -> { if (cfg.maxBurbujasPorJugador > 1) cfg.maxBurbujasPorJugador--; },
				() -> { if (cfg.maxBurbujasPorJugador < 6) cfg.maxBurbujasPorJugador++; });

		y = agregarNumerica(y, centroX, alto, "Opacidad de fondo",
				() -> String.format("%.2f", cfg.opacidadFondo),
				() -> cfg.opacidadFondo = Math.max(0f, cfg.opacidadFondo - 0.05f),
				() -> cfg.opacidadFondo = Math.min(1f, cfg.opacidadFondo + 0.05f));

		y = agregarBooleano(y, centroX, anchoBtn, alto, "Ver a través de paredes",
				() -> cfg.verAtravesDeParedes, v -> cfg.verAtravesDeParedes = v);

		y = agregarBooleano(y, centroX, anchoBtn, alto, "Mostrar mi propia burbuja",
				() -> cfg.mostrarMiPropiaBurbuja, v -> cfg.mostrarMiPropiaBurbuja = v);

		y += 8;
		campoUrl = new TextFieldWidget(this.textRenderer, centroX - anchoBtn / 2, y, anchoBtn, alto,
				Text.literal("URL del traductor"));
		campoUrl.setMaxLength(300);
		campoUrl.setText(cfg.urlWebTraductor == null ? "" : cfg.urlWebTraductor);
		addDrawableChild(campoUrl);
		y += alto + 6;

		addDrawableChild(ButtonWidget.builder(Text.literal("🌐 Abrir web del traductor"), b -> abrirWeb())
				.dimensions(centroX - anchoBtn / 2, y, anchoBtn, alto).build());
		y += alto + 14;

		addDrawableChild(ButtonWidget.builder(Text.literal("Guardar y cerrar"), b -> guardarYCerrar())
				.dimensions(centroX - anchoBtn / 2, y, anchoBtn, alto).build());
	}

	// ------------------------------------------------------------------
	// Helpers para no repetir codigo al crear cada fila del menu
	// ------------------------------------------------------------------

	private int agregarBooleano(int y, int centroX, int ancho, int alto, String etiqueta,
	                             Supplier<Boolean> leer, java.util.function.Consumer<Boolean> escribir) {
		ButtonWidget boton = ButtonWidget.builder(textoBooleano(etiqueta, leer.get()), b -> {
			boolean nuevo = !leer.get();
			escribir.accept(nuevo);
			b.setMessage(textoBooleano(etiqueta, nuevo));
		}).dimensions(centroX - ancho / 2, y, ancho, alto).build();
		addDrawableChild(boton);
		return y + alto + 6;
	}

	private int agregarCiclo(int y, int centroX, int ancho, int alto, String etiqueta, String[] valores,
	                          Supplier<String> leer, java.util.function.Consumer<String> escribir) {
		ButtonWidget boton = ButtonWidget.builder(Text.literal(etiqueta + ": " + leer.get()), b -> {
			String siguiente = siguienteValor(valores, leer.get());
			escribir.accept(siguiente);
			b.setMessage(Text.literal(etiqueta + ": " + siguiente));
		}).dimensions(centroX - ancho / 2, y, ancho, alto).build();
		addDrawableChild(boton);
		return y + alto + 6;
	}

	private int agregarNumerica(int y, int centroX, int alto, String etiqueta,
	                             Supplier<String> valor, Runnable restar, Runnable sumar) {
		addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> restar.run())
				.dimensions(centroX - 110, y, 20, alto).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> sumar.run())
				.dimensions(centroX + 90, y, 20, alto).build());
		filasNumericas.add(new FilaNumerica(etiqueta, valor, y));
		return y + alto + 6;
	}

	private Text textoBooleano(String etiqueta, boolean valor) {
		return Text.literal(etiqueta + ": " + (valor ? "Sí" : "No"));
	}

	private String siguienteValor(String[] valores, String actual) {
		int indice = 0;
		for (int i = 0; i < valores.length; i++) {
			if (valores[i].equals(actual)) {
				indice = i;
				break;
			}
		}
		return valores[(indice + 1) % valores.length];
	}

	// ------------------------------------------------------------------

	private void abrirWeb() {
		String url = campoUrl.getText().trim();
		if (url.isEmpty()) {
			if (this.client != null && this.client.player != null) {
				this.client.player.sendMessage(
						Text.literal("§eEscribe primero la URL de tu web del traductor en el campo de arriba."), true);
			}
			return;
		}
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			url = "https://" + url;
		}
		try {
			Util.getOperatingSystem().open(new URI(url));
		} catch (Exception e) {
			ChatBubblesClient.LOGGER.warn("[ChatBubbles] No se pudo abrir la URL del traductor: {}", e.toString());
		}
	}

	private void guardarYCerrar() {
		ModConfig cfg = ModConfig.get();
		cfg.urlWebTraductor = campoUrl.getText().trim();
		ModConfig.guardar();
		if (this.client != null) this.client.setScreen(pantallaAnterior);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

		for (FilaNumerica fila : filasNumericas) {
			String texto = fila.etiqueta() + ": " + fila.valor().get();
			context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(texto), this.width / 2, fila.y() + 6, 0xE4E9F2);
		}

		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Diseñado por ItsBrauu"),
				this.width / 2, this.height - 14, 0x888888);
	}

	@Override
	public void close() {
		guardarYCerrar();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
