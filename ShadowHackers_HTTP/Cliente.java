import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Cliente {
    private static String SERVER_URL; // Ej: https://shadow-hackers.up.railway.app
    private static String MI_NOMBRE;
    private static int ultimoEvento = 0;
    private static boolean ejecutando = true;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java Cliente <URL_SERVIDOR> [NOMBRE]");
            System.out.println("Ej: java Cliente https://mi-juego.up.railway.app HackerOne");
            return;
        }

        SERVER_URL = args[0];
        // Quitar barra final si la tiene
        if (SERVER_URL.endsWith("/")) SERVER_URL = SERVER_URL.substring(0, SERVER_URL.length() - 1);
        
        // Si no empieza por http, añadirlo (asumimos https para nube, http para local)
        if (!SERVER_URL.startsWith("http")) {
            SERVER_URL = "https://" + SERVER_URL; 
        }

        Scanner scanner = new Scanner(System.in);

        // Obtener nombre
        if (args.length > 1) {
            MI_NOMBRE = args[1];
        } else {
            System.out.print("Introduce tu nombre de Hacker: ");
            MI_NOMBRE = scanner.nextLine();
        }

        System.out.println("Conectando a " + SERVER_URL + " como " + MI_NOMBRE + "...");
        
        // 1. Enviar Login
        enviarComando("LOGIN", "");

        // 2. Iniciar Hilo de Escucha (Polling)
        new Thread(() -> {
            while (ejecutando) {
                try {
                    pollEventos();
                    Thread.sleep(1000); // Consultar cada 1 segundo (Polling)
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();

        // 3. Bucle principal de comandos
        System.out.println("¡Conectado! Comandos: HACK <n>, STATUS, EXIT");
        while (ejecutando && scanner.hasNextLine()) {
            String linea = scanner.nextLine();
            String[] partes = linea.split(" ");
            String cmd = partes[0].toUpperCase();

            if (cmd.equals("EXIT")) {
                ejecutando = false;
                break;
            } else if (cmd.equals("HACK")) {
                if (partes.length > 1) enviarComando("HACK", partes[1]);
                else System.out.println("Falta el numero de nodo.");
            } else if (cmd.equals("STATUS")) {
                enviarComando("STATUS", "");
            } else {
                System.out.println("Comando desconocido.");
            }
        }
        System.out.println("Desconectado.");
    }

    // --- FUNCIONES HTTP ---

    private static void enviarComando(String comando, String arg) {
        try {
            URL url = new URL(SERVER_URL + "/api/comando");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            // Cuerpo: "NOMBRE|COMANDO|ARGUMENTO"
            String cuerpo = MI_NOMBRE + "|" + comando + "|" + arg;
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = cuerpo.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Leer respuesta del servidor
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim()).append("\n");
                }
                // Mostrar respuesta directa (ej: "Hackeo exitoso")
                String res = response.toString().trim();
                if (!res.isEmpty() && !comando.equals("LOGIN")) {
                    System.out.println("[SISTEMA] " + res);
                }
            }
        } catch (Exception e) {
            System.out.println("Error enviando comando: " + e.getMessage());
        }
    }

    private static void pollEventos() {
        try {
            // GET /api/eventos?index=X
            URL url = new URL(SERVER_URL + "/api/eventos?index=" + ultimoEvento);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    String linea;
                    while ((linea = br.readLine()) != null) {
                        if (!linea.trim().isEmpty()) {
                            System.out.println(linea);
                            ultimoEvento++; // Incrementamos contador localmente
                            // Nota: En un sistema real robusto, el servidor debería devolver el ID del último evento.
                            // Aquí simplificamos asumiendo que recibimos todo en orden.
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silencioso para no molestar si falla un poll puntual
        }
    }
}
