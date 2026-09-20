# Burbujas de Chat — Fabric 1.20.1

**Diseñado por ItsBrauu.**

Cada vez que alguien escribe en el chat, el mensaje aparece **flotando arriba de su cabeza**, y opcionalmente **traducido** a tu idioma. Ahora con menú de configuración dentro del juego y temas de color, igual que tu web BSMP Stream Translator.

Es un mod **solo de cliente**: funciona en cualquier servidor (vanilla, Paper, Spigot, realms, servidores de amigos) sin que el servidor instale nada. Solo tú ves las burbujas.

## Qué hace

- Burbujas flotantes que siguen al jugador y siempre miran a tu cámara
- Se apilan hasta 3 mensajes por jugador (configurable) y se desvanecen solas
- Traducción automática con la API gratuita de Google Translate
- **Menú de configuración dentro del juego** (tecla **O**): activar/desactivar, idioma, duración, distancia, temas de color, etc. — sin tocar archivos a mano
- **4 temas de color** iguales a los de tu web: oscuro, neón, retro y claro
- **Botón para abrir tu web del traductor** desde el propio menú del mod
- Muestra el original arriba y la traducción en gris cursiva abajo (configurable)
- Funciona con servidores que usan plugins de chat (detecta el nombre en formatos tipo `[VIP] Pepe: hola`)
- Tecla **B** para prender/apagar rápido, **O** para el menú completo

---

# Cómo obtener el `.jar`

## Opción A — GitHub lo compila por ti (no instalas nada)

1. Crea una cuenta en **github.com**
2. Botón **New repository** → ponle un nombre → **Create**
3. **Add file → Upload files** → arrastra TODOS los archivos y carpetas de este zip → **Commit**
   - Sube también la carpeta oculta `.github`, ahí está el que hace la magia
   - Debe existir **un solo** archivo dentro de `.github/workflows/`
4. Pestaña **Actions** → espera la palomita verde ✅
5. Clic en la ejecución → **Artifacts** → descarga **chatbubbles-jar**
6. Adentro viene `chatbubbles-1.0.0.jar`

## Opción B — Compilarlo en tu PC

1. Instala el **JDK 17** (adoptium.net → Temurin 17) y **IntelliJ IDEA Community** (gratis)
2. Descomprime el zip, abre la carpeta en IntelliJ
3. Deja que descargue todo la primera vez (5–20 min, necesita internet)
4. Panel **Gradle** → `Tasks` → `build` → doble clic en **build**
5. Tu `.jar` queda en `build/libs/chatbubbles-1.0.0.jar`

## Instalarlo

1. fabricmc.net/use → instalador de Fabric para **1.20.1**
2. Descarga la **Fabric API 1.20.1** — es obligatoria
3. Mete los dos `.jar` en `.minecraft/mods/`
4. Abre Minecraft con el perfil `fabric-loader-1.20.1`

---

# El menú del mod (tecla O)

Dentro de cualquier mundo, presiona **O** para abrir el menú. Desde ahí puedes cambiar todo sin editar archivos:

- Activar/desactivar burbujas y traducción
- Qué se muestra (ambos textos / solo traducción / solo original)
- Idioma destino (es, en, fr, de, it, pt, ru, ja — clic para pasar al siguiente)
- **Tema**: oscuro, neón, retro o claro — cambia colores de texto y fondo al instante
- Duración, distancia máxima, cuántas burbujas se apilan, opacidad de fondo (botones -/+)
- Ver a través de paredes, mostrar tu propia burbuja
- Un campo de texto para pegar la **URL de tu web del traductor**, y un botón para abrirla directo en tu navegador desde el juego

Todo se guarda solo al presionar "Guardar y cerrar" o al salir con Esc. El archivo sigue siendo `.minecraft/config/chatbubbles.json` por si prefieres editarlo a mano.

---

# Sobre la API de traducción

Usa el mismo endpoint que tu web (`translate.googleapis.com`), con protecciones extra: cola de una petición a la vez (como tu `processQueue`), encabezados de navegador real para no verse como bot, caché de mensajes repetidos, y un servidor de respaldo si el principal te bloquea.

Si prefieres algo sin depender de Google, LibreTranslate es libre y se puede autoalojar — el código para cambiarlo está en `Translator.java`, en el método `pedir()`.

---

# Los archivos, en corto

| Archivo | Para qué es |
|---|---|
| `ChatBubblesClient.java` | Arranca todo |
| `ChatListener.java` | Escucha el chat y ve de quién es cada mensaje |
| `ChatBubble.java` | Un mensaje: texto, traducción, cuándo se creó |
| `BubbleManager.java` | Qué burbujas tiene cada jugador ahorita |
| `BubbleRenderer.java` | Dibuja el texto flotando en el mundo, con los colores del tema |
| `Translator.java` | Habla con la API de traducción |
| `ModConfig.java` | Lee, guarda la configuración y aplica los temas de color |
| `Teclas.java` | Las teclas B (burbujas) y O (menú) |
| `PantallaConfiguracion.java` | El menú de configuración dentro del juego |

Todo está comentado en español.

---

# Si algo falla

| Síntoma | Causa |
|---|---|
| No sale ninguna burbuja | Falta la Fabric API, o presionaste B sin querer |
| El menú no abre con O | Puede que ya tengas otra pantalla abierta (inventario, chat) — ciérrala primero |
| Salen en singleplayer pero no en un servidor | Ese servidor formatea el chat raro; revisa `latest.log` |
| Sale el original pero nunca la traducción | Google te limitó (busca "limito" en `latest.log`) o el mensaje ya estaba en tu idioma |
| El botón de abrir web no hace nada | No escribiste la URL en el campo de texto del menú primero |
| Crash al abrir | Versión equivocada: esto es solo para **1.20.1** |

Los logs están en `.minecraft/logs/latest.log` — busca `ChatBubbles`.

