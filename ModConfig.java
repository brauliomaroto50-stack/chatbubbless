package com.ejemplo.chatbubbles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuracion del mod. Se guarda en  .minecraft/config/chatbubbles.json
 * Puedes editar ese archivo con el bloc de notas y reiniciar el juego.
 */
public class ModConfig {

	// ---- Burbujas ----
	/** Prender/apagar todo el mod. */
	public boolean activado = true;
	/** Cuantos segundos dura una burbuja antes de desaparecer. */
	public int segundosVisible = 8;
	/** Cuantas burbujas se apilan como maximo por jugador. */
	public int maxBurbujasPorJugador = 3;
	/** A cuantos bloques de distancia se dejan de ver. */
	public int distanciaMaxima = 48;
	/** Ancho maximo de la burbuja en pixeles antes de partir el texto en renglones. */
	public int anchoMaximo = 180;
	/** Ver las burbujas a traves de las paredes. */
	public boolean verAtravesDeParedes = false;
	/** Mostrar tambien tu propia burbuja (solo se ve en tercera persona / F5). */
	public boolean mostrarMiPropiaBurbuja = true;
	/** Opacidad del fondo de la burbuja, de 0.0 (invisible) a 1.0 (negro solido). */
	public float opacidadFondo = 0.35f;

	// ---- Traduccion ----
	/** Prender/apagar la traduccion automatica. */
	public boolean traducir = true;
	/** Idioma al que se traduce: es, en, pt, fr, de, ja, ru... */
	public String idiomaDestino = "es";
	/** "auto" = detecta solo el idioma de quien escribio. */
	public String idiomaOrigen = "auto";
	/**
	 * Que se muestra en la burbuja:
	 *   "ambos"      -> el texto original y abajo la traduccion en gris
	 *   "traduccion" -> solo la traduccion
	 *   "original"   -> solo el original (equivale a apagar la traduccion)
	 */
	public String mostrar = "ambos";
	/** No se traducen mensajes mas largos que esto (para no saturar la API). */
	public int largoMaximoTraduccion = 200;

	// ---- Apariencia / Menu ----
	/** Tema de color de las burbujas: "oscuro", "neon", "retro" o "claro" (igual que en la web). */
	public String tema = "oscuro";
	/** Color del texto, en formato #RRGGBB. Lo cambia aplicarTema() al elegir un tema. */
	public String colorTextoHex = "#FFFFFF";
	/** Color del fondo de la burbuja, en formato #RRGGBB. */
	public String colorFondoHex = "#000000";
	/** URL de tu pagina web del traductor (BSMP Stream Translator). Se abre desde el menu del mod. */
	public String urlWebTraductor = "";

	// ------------------------------------------------------------------

	private static ModConfig INSTANCIA = new ModConfig();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static ModConfig get() {
		return INSTANCIA;
	}

	private static Path ruta() {
		return FabricLoader.getInstance().getConfigDir().resolve("chatbubbles.json");
	}

	public static void cargar() {
		Path p = ruta();
		try {
			if (Files.exists(p)) {
				ModConfig leido = GSON.fromJson(Files.readString(p), ModConfig.class);
				if (leido != null) INSTANCIA = leido;
			} else {
				guardar();
			}
		} catch (Exception e) {
			ChatBubblesClient.LOGGER.warn("[ChatBubbles] No se pudo leer la config, uso los valores por defecto.", e);
		}
	}

	public static void guardar() {
		try {
			Files.createDirectories(ruta().getParent());
			Files.writeString(ruta(), GSON.toJson(INSTANCIA));
		} catch (Exception e) {
			ChatBubblesClient.LOGGER.warn("[ChatBubbles] No se pudo guardar la config.", e);
		}
	}

	/**
	 * Aplica los mismos 4 temas de color que tiene la pagina web (BSMP Stream Translator):
	 * oscuro, neon, retro y claro. Cambia el color de texto y de fondo de las burbujas.
	 */
	public static void aplicarTema(String tema) {
		ModConfig cfg = get();
		cfg.tema = tema;
		switch (tema) {
			case "neon" -> {
				cfg.colorTextoHex = "#00FFFF";
				cfg.colorFondoHex = "#140028";
			}
			case "retro" -> {
				cfg.colorTextoHex = "#FF8800";
				cfg.colorFondoHex = "#281400";
			}
			case "claro" -> {
				cfg.colorTextoHex = "#202020";
				cfg.colorFondoHex = "#FFFFFF";
			}
			default -> { // "oscuro"
				cfg.colorTextoHex = "#FFFFFF";
				cfg.colorFondoHex = "#000000";
			}
		}
	}
}
