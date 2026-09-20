package com.ejemplo.chatbubbles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Traduce texto usando el endpoint publico y gratuito de Google Translate
 * (el mismo que usa la pagina web, sin llave de API):
 *
 *   https://translate.googleapis.com/translate_a/single?client=gtx&sl=..&tl=..&dt=t&q=..
 *
 * OJO, cosas importantes de este endpoint:
 *   - No es oficial ni documentado. Google lo puede cambiar o cerrar cuando quiera.
 *   - Tiene limite de peticiones. Si mandas muchisimas seguidas te puede bloquear
 *     la IP por un rato (te va a responder 429 o 403).
 *   - Por eso aqui hay cache, limite de peticiones simultaneas y limite de largo.
 *
 * Si quieres algo 100% legitimo y sin sorpresas, en el LEEME.md viene como
 * cambiarlo por LibreTranslate, que es open source y se puede autoalojar.
 */
public class Translator {

	/** Un solo hilo: las peticiones se mandan de a una, en orden, nunca en paralelo. */
	private static final ExecutorService HILOS = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ChatBubbles-Traductor");
		t.setDaemon(true); // daemon = no impide que el juego cierre
		return t;
	});

	private static final HttpClient CLIENTE = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(4))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	/** Cache: si alguien repite el mismo mensaje, no volvemos a pedirlo. */
	private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
	private static final int CACHE_MAXIMO = 500;

	/**
	 * En vez de mandar varias peticiones al mismo tiempo (lo que hacia antes con
	 * un pool de hilos), las mandamos UNA POR UNA con una pequeña pausa entre
	 * cada una, igual que hace la version web con su "processQueue". Mandar
	 * rafagas simultaneas es lo que mas rapido hace que Google nos bloquee.
	 */
	private static final long PAUSA_ENTRE_PETICIONES_MS = 250L;
	private static volatile long ultimaPeticion = 0L;
	private static final Object CANDADO_COLA = new Object();

	/**
	 * Encabezados que manda un Chrome real al usar Google Translate desde su pagina web.
	 * Java, por defecto, no manda nada de esto y eso hace que Google trate la peticion
	 * como si fuera de un bot, aunque venga de la misma casa/IP que un navegador normal.
	 * Poniendolos igual que un navegador de verdad baja mucho la probabilidad de bloqueo.
	 */
	private static final String USER_AGENT_NAVEGADOR =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

	/** Si Google nos bloquea, dejamos de insistir por un rato. */
	private static volatile long bloqueadoHasta = 0L;

	/**
	 * Pide la traduccion en segundo plano. Cuando llega, llama a alRecibir.
	 * Si no se pudo traducir (o el texto ya estaba en el idioma destino) no llama a nada.
	 */
	public static void traducirAsync(String texto, Consumer<String> alRecibir) {
		ModConfig cfg = ModConfig.get();
		String destino = cfg.idiomaDestino;
		String origen = (cfg.idiomaOrigen == null || cfg.idiomaOrigen.isBlank()) ? "auto" : cfg.idiomaOrigen;

		String llaveCache = origen + "|" + destino + "|" + texto;
		String yaTraducido = CACHE.get(llaveCache);
		if (yaTraducido != null) {
			alRecibir.accept(yaTraducido);
			return;
		}

		if (System.currentTimeMillis() < bloqueadoHasta) return;

		HILOS.submit(() -> {
			try {
				esperarTurno();
				String resultado = pedirConRespaldo(texto, origen, destino);
				if (resultado != null && !resultado.isBlank() && !resultado.equals(texto)) {
					if (CACHE.size() > CACHE_MAXIMO) CACHE.clear();
					CACHE.put(llaveCache, resultado);
					alRecibir.accept(resultado);
				}
			} catch (Exception e) {
				ChatBubblesClient.LOGGER.debug("[ChatBubbles] Fallo al traducir: {}", e.toString());
			}
		});
	}

	/** Espera lo necesario para que entre esta peticion y la anterior pase al menos PAUSA_ENTRE_PETICIONES_MS. */
	private static void esperarTurno() {
		synchronized (CANDADO_COLA) {
			long ahora = System.currentTimeMillis();
			long esperar = (ultimaPeticion + PAUSA_ENTRE_PETICIONES_MS) - ahora;
			if (esperar > 0) {
				try {
					Thread.sleep(esperar);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			ultimaPeticion = System.currentTimeMillis();
		}
	}

	/**
	 * Intenta primero con translate.googleapis.com. Si ese nos bloquea (403/429),
	 * intenta de inmediato con un segundo servidor (clients5.google.com), que usa
	 * Google internamente para su extension de Chrome y a veces sigue libre cuando
	 * el otro ya esta saturado. Solo se activa la pausa de 2 minutos si LOS DOS fallan.
	 */
	private static String pedirConRespaldo(String texto, String origen, String destino) throws Exception {
		try {
			return pedir(texto, origen, destino);
		} catch (Bloqueado primerBloqueo) {
			try {
				String resultado = pedirRespaldo(texto, origen, destino);
				if (resultado != null) return resultado;
			} catch (Exception ignorado) {
				// el respaldo tambien fallo, seguimos abajo con la pausa
			}
			bloqueadoHasta = System.currentTimeMillis() + 120_000L;
			ChatBubblesClient.LOGGER.warn("[ChatBubbles] Los dos servidores de traduccion nos limitaron. Pausando 2 minutos.");
			return null;
		}
	}

	/** Se lanza cuando un servidor responde 403/429, para saber que hay que probar el respaldo. */
	private static class Bloqueado extends Exception {
	}

	/** Hace la peticion HTTP y saca la traduccion de la respuesta. */
	private static String pedir(String texto, String origen, String destino) throws Exception {
		String url = "https://translate.googleapis.com/translate_a/single"
				+ "?client=gtx"
				+ "&sl=" + URLEncoder.encode(origen, StandardCharsets.UTF_8)
				+ "&tl=" + URLEncoder.encode(destino, StandardCharsets.UTF_8)
				+ "&dt=t"
				+ "&q=" + URLEncoder.encode(texto, StandardCharsets.UTF_8);

		HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", USER_AGENT_NAVEGADOR)
				.header("Referer", "https://translate.google.com/")
				.header("Accept", "*/*")
				.header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
				.timeout(Duration.ofSeconds(5))
				.GET()
				.build();

		HttpResponse<String> respuesta = CLIENTE.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if (respuesta.statusCode() == 429 || respuesta.statusCode() == 403) {
			throw new Bloqueado();
		}
		if (respuesta.statusCode() != 200) return null;

		return leerRespuesta(respuesta.body(), destino);
	}

	/**
	 * Servidor de respaldo: el mismo que usa la extension oficial de Google Translate
	 * para Chrome. Su formato de respuesta es distinto (mas simple, texto plano con
	 * comillas), asi que se lee distinto al principal.
	 */
	private static String pedirRespaldo(String texto, String origen, String destino) throws Exception {
		String url = "https://clients5.google.com/translate_a/t"
				+ "?client=dict-chrome-ex"
				+ "&sl=" + URLEncoder.encode(origen, StandardCharsets.UTF_8)
				+ "&tl=" + URLEncoder.encode(destino, StandardCharsets.UTF_8)
				+ "&q=" + URLEncoder.encode(texto, StandardCharsets.UTF_8);

		HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", USER_AGENT_NAVEGADOR)
				.header("Referer", "https://translate.google.com/")
				.header("Accept", "*/*")
				.timeout(Duration.ofSeconds(5))
				.GET()
				.build();

		HttpResponse<String> respuesta = CLIENTE.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (respuesta.statusCode() != 200) return null;

		// La respuesta viene como: ["texto traducido"]  o  [["texto traducido"]]
		JsonElement raiz = JsonParser.parseString(respuesta.body());
		if (raiz.isJsonArray()) {
			JsonArray arr = raiz.getAsJsonArray();
			if (arr.size() > 0) {
				JsonElement primero = arr.get(0);
				if (primero.isJsonPrimitive()) return primero.getAsString().trim();
				if (primero.isJsonArray() && primero.getAsJsonArray().size() > 0) {
					return primero.getAsJsonArray().get(0).getAsString().trim();
				}
			}
		}
		return null;
	}

	/**
	 * La respuesta viene como un arreglo raro, algo asi:
	 *   [[["hola","hello",null,null,10]],null,"en",...]
	 *
	 *   - raiz[0] = lista de pedazos traducidos; de cada pedazo, el indice 0 es el texto
	 *   - raiz[2] = idioma que detecto que era el original
	 */
	private static String leerRespuesta(String cuerpo, String destino) {
		JsonElement raizElem = JsonParser.parseString(cuerpo);
		if (!raizElem.isJsonArray()) return null;
		JsonArray raiz = raizElem.getAsJsonArray();

		// Si ya estaba en nuestro idioma, no tiene caso mostrar la traduccion.
		if (raiz.size() > 2 && raiz.get(2).isJsonPrimitive()) {
			String detectado = raiz.get(2).getAsString();
			if (detectado != null && detectado.equalsIgnoreCase(destino)) return null;
		}

		if (raiz.size() == 0 || !raiz.get(0).isJsonArray()) return null;
		JsonArray pedazos = raiz.get(0).getAsJsonArray();

		StringBuilder sb = new StringBuilder();
		for (JsonElement pedazoElem : pedazos) {
			if (!pedazoElem.isJsonArray()) continue;
			JsonArray pedazo = pedazoElem.getAsJsonArray();
			if (pedazo.size() > 0 && pedazo.get(0).isJsonPrimitive()) {
				sb.append(pedazo.get(0).getAsString());
			}
		}
		String r = sb.toString().trim();
		return r.isEmpty() ? null : r;
	}
}
