# Burbujas de Chat — Fabric 1.20.1

Cada vez que alguien escribe en el chat, el mensaje aparece **flotando arriba de su cabeza**, y opcionalmente **traducido** a tu idioma.

Es un mod **solo de cliente**: funciona en cualquier servidor (vanilla, Paper, Spigot, realms, servidores de amigos) sin que el servidor instale nada. Solo tú ves las burbujas.

## Qué hace

- Burbujas flotantes que siguen al jugador y siempre miran a tu cámara
- Se apilan hasta 3 mensajes por jugador y se desvanecen solas a los 8 segundos
- Traducción automática con la API gratuita de Google Translate
- Muestra el original arriba y la traducción en gris cursiva abajo (configurable)
- Funciona con servidores que usan plugins de chat (detecta el nombre en formatos tipo `[VIP] Pepe: hola`)
- Tecla **B** para prender/apagar

---

# Cómo obtener el `.jar`

## Opción A — GitHub lo compila por ti (no instalas nada)

La más fácil si no quieres batallar con Java.

1. Crea una cuenta en **github.com**
2. Botón **New repository** → ponle un nombre → **Create**
3. **Add file → Upload files** → arrastra TODOS los archivos y carpetas de este zip → **Commit**
   - Importante: sube también la carpeta oculta `.github`, ahí está el que hace la magia
4. Entra a la pestaña **Actions** del repositorio
5. Espera unos 3 minutos a que salga la palomita verde ✅
6. Clic en la ejecución → baja hasta **Artifacts** → descarga **chatbubbles-jar**
7. Adentro viene `chatbubbles-1.0.0.jar`

## Opción B — Compilarlo en tu PC

1. Instala el **JDK 17** (adoptium.net → Temurin 17) y **IntelliJ IDEA Community** (gratis)
2. Descomprime el zip, abre la carpeta en IntelliJ (la que tiene `build.gradle`)
3. Deja que descargue todo la primera vez (5–20 min, necesita internet)
4. Panel **Gradle** (derecha) → `Tasks` → `build` → doble clic en **build**
5. Tu `.jar` queda en `build/libs/chatbubbles-1.0.0.jar`

Para probarlo sin compilar: panel Gradle → `Tasks` → `fabric` → **runClient**.

## Instalarlo

1. fabricmc.net/use → instalador de Fabric para **1.20.1**
2. Descarga la **Fabric API 1.20.1** (Modrinth o CurseForge) — es obligatoria
3. Mete los dos `.jar` en `.minecraft/mods/`
4. Abre Minecraft con el perfil `fabric-loader-1.20.1`

---

# Configuración

Se crea sola en `.minecraft/config/chatbubbles.json` la primera vez que juegas. Ábrela con el bloc de notas y reinicia el juego.

| Opción | Qué hace | Por defecto |
|---|---|---|
| `activado` | Prender/apagar todo | `true` |
| `segundosVisible` | Cuánto dura cada burbuja | `8` |
| `maxBurbujasPorJugador` | Cuántas se apilan | `3` |
| `distanciaMaxima` | Bloques de distancia | `48` |
| `anchoMaximo` | Píxeles antes de cortar renglón | `180` |
| `verAtravesDeParedes` | Ver burbujas tras bloques | `false` |
| `mostrarMiPropiaBurbuja` | La tuya (solo en F5) | `true` |
| `opacidadFondo` | 0.0 a 1.0 | `0.35` |
| `traducir` | Traducción automática | `true` |
| `idiomaDestino` | `es`, `en`, `pt`, `fr`, `ja`... | `es` |
| `idiomaOrigen` | `auto` detecta solo | `auto` |
| `mostrar` | `ambos` / `traduccion` / `original` | `ambos` |

---

# Sobre la API de traducción

Usa el endpoint que me pasaste:

```
https://translate.googleapis.com/translate_a/single?client=gtx&sl=..&tl=..&dt=t&q=..
```

Es gratis y no necesita llave, pero hay que saber esto:

- **No es oficial.** Google lo puede cambiar o cerrar cuando quiera, y el día que lo haga el mod dejaría de traducir (las burbujas seguirían funcionando).
- **Tiene límite de peticiones.** Si mandas muchísimas te responde 403 o 429 y te bloquea la IP un rato.

Por eso `Translator.java` ya trae protecciones: caché de mensajes repetidos, máximo 4 peticiones a la vez, no traduce mensajes de más de 200 caracteres, y si te bloquean se pausa 2 minutos solo.

## Cambiarlo por LibreTranslate (open source)

Si prefieres algo sin sorpresas, LibreTranslate es libre y lo puedes autoalojar. En `Translator.java`, reemplaza el método `pedir()` por:

```java
private static String pedir(String texto, String origen, String destino) throws Exception {
    String cuerpo = String.format(
        "{\"q\":%s,\"source\":\"%s\",\"target\":\"%s\",\"format\":\"text\"}",
        new com.google.gson.JsonPrimitive(texto).toString(), origen, destino);

    HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:5000/translate"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
            .build();

    HttpResponse<String> r = CLIENTE.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (r.statusCode() != 200) return null;
    return com.google.gson.JsonParser.parseString(r.body())
            .getAsJsonObject().get("translatedText").getAsString();
}
```

(Cambia `localhost:5000` por tu servidor. LibreTranslate no acepta `auto` en todas las instalaciones; ponle un idioma fijo en `idiomaOrigen`.)

---

# Los archivos, en corto

| Archivo | Para qué es |
|---|---|
| `ChatBubblesClient.java` | Arranca todo |
| `ChatListener.java` | Escucha el chat y ve de quién es cada mensaje |
| `ChatBubble.java` | Un mensaje: texto, traducción, cuándo se creó |
| `BubbleManager.java` | Qué burbujas tiene cada jugador ahorita |
| `BubbleRenderer.java` | Dibuja el texto flotando en el mundo |
| `Translator.java` | Habla con la API de traducción |
| `ModConfig.java` | Lee y guarda la configuración |
| `Teclas.java` | La tecla B |

Todo está comentado en español.

---

# Si algo falla

| Síntoma | Causa |
|---|---|
| No sale ninguna burbuja | Falta la Fabric API, o presionaste B sin querer |
| Salen en singleplayer pero no en un servidor | Ese servidor formatea el chat raro; revisa `latest.log` |
| Sale el original pero nunca la traducción | Google te limitó (busca "limito" en `latest.log`) o el mensaje ya estaba en tu idioma |
| Crash al abrir | Versión equivocada: esto es solo para **1.20.1** |
| Las burbujas tapan la pantalla en primera persona | Pon `mostrarMiPropiaBurbuja: false` |

Los logs están en `.minecraft/logs/latest.log` — busca `ChatBubbles`.
