import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {
    // Puerto compatible con Railway (variable PORT) o 8080 local
    private static final int PORT = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;

    // Estado del juego
    private static final Map<Integer, String> nodos = new ConcurrentHashMap<>();
    private static final Map<String, Integer> puntuaciones = new ConcurrentHashMap<>();
    
    // Sistema de eventos (Chat/Log del juego)
    // Guardamos una lista de eventos y cada cliente pide los que le faltan
    private static final List<String> eventos = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws IOException {
        // Inicializar juego
        for (int i = 1; i <= 10; i++) nodos.put(i, "Libre");
        registrarEvento(">>> SERVIDOR INICIADO");

        // Crear servidor HTTP ligero
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Endpoint para enviar comandos (HACK, LOGIN)
        server.createContext("/api/comando", new ManejadorComando());
        
        // Endpoint para leer novedades (Polling)
        server.createContext("/api/eventos", new ManejadorEventos());
        
        // Endpoint simple para verificar que funciona en navegador
        server.createContext("/", exchange -> {
            String response = "Shadow Hackers Server Online! Usa el Cliente Java para jugar.";
            enviarRespuesta(exchange, 200, response);
        });

        server.setExecutor(null); // Default executor
        System.out.println("Servidor HTTP escuchando en el puerto " + PORT);
        server.start();
    }

    // --- MANEJADORES ---

    static class ManejadorComando implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String cuerpo = leerCuerpo(exchange);
                // Formato esperado: "JUGADOR|COMANDO|ARGUMENTO"
                String[] partes = cuerpo.split("\\|");
                
                if (partes.length < 2) {
                    enviarRespuesta(exchange, 400, "Formato invalido");
                    return;
                }

                String jugador = partes[0];
                String comando = partes[1].toUpperCase();
                String argumento = partes.length > 2 ? partes[2] : "";

                String respuesta = procesarLogica(jugador, comando, argumento);
                enviarRespuesta(exchange, 200, respuesta);
            } else {
                enviarRespuesta(exchange, 405, "Metodo no permitido");
            }
        }
    }

    static class ManejadorEventos implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // El cliente envia "ultimoEventoRecibido" (int)
            String query = exchange.getRequestURI().getQuery();
            int lastIndex = 0;
            if (query != null && query.contains("index=")) {
                lastIndex = Integer.parseInt(query.split("=")[1]);
            }

            StringBuilder respuesta = new StringBuilder();
            
            // Sincronizamos para leer la lista
            synchronized (eventos) {
                if (lastIndex < eventsSize()) {
                    for (int i = lastIndex; i < eventsSize(); i++) {
                        respuesta.append(eventos.get(i)).append("\n");
                    }
                }
            }
            
            // Devolver también el nuevo índice
            enviarRespuesta(exchange, 200, respuesta.toString());
        }
    }

    // --- LOGICA DEL JUEGO ---

    private static synchronized String procesarLogica(String jugador, String comando, String arg) {
        puntuaciones.putIfAbsent(jugador, 0);

        if (comando.equals("LOGIN")) {
            registrarEvento(">>> " + jugador + " se ha conectado.");
            return "Bienvenido " + jugador;
        }
        else if (comando.equals("HACK")) {
            try {
                int nodoId = Integer.parseInt(arg);
                if (!nodos.containsKey(nodoId)) return "Error: Nodo inexistente";
                
                String dueño = nodos.get(nodoId);
                if (!dueño.equals(jugador)) {
                    nodos.put(nodoId, jugador);
                    puntuaciones.put(jugador, puntuaciones.get(jugador) + 10);
                    registrarEvento("¡HACK! " + jugador + " capturó el NODO " + nodoId + " (era de " + dueño + ")");
                    return "Hackeo exitoso!";
                } else {
                    return "Ya controlas ese nodo.";
                }
            } catch (NumberFormatException e) {
                return "Error: ID de nodo invalido";
            }
        }
        else if (comando.equals("STATUS")) {
            StringBuilder sb = new StringBuilder("--- ESTADO ---\n");
            // Nodos
            new TreeMap<>(nodos).forEach((k, v) -> sb.append("Nodo ").append(k).append(": ").append(v).append("\n"));
            // Puntos
            sb.append("--- RANKING ---\n");
            puntuaciones.forEach((k, v) -> sb.append(k).append(": ").append(v).append(" pts\n"));
            return sb.toString();
        }
        
        return "Comando desconocido";
    }

    // --- UTILIDADES ---

    private static void registrarEvento(String msg) {
        synchronized (eventos) {
            eventos.add(msg);
            // Limpieza básica: si hay demasiados eventos, borrar antiguos para no llenar memoria
            if (eventos.size() > 1000) {
                eventos.subList(0, 100).clear();
            }
        }
        System.out.println("[LOG] " + msg);
    }
    
    private static int eventsSize() {
        synchronized (eventos) { return eventos.size(); }
    }

    private static String leerCuerpo(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder b = new StringBuilder();
        String linea;
        while ((linea = br.readLine()) != null) b.append(linea);
        return b.toString();
    }

    private static void enviarRespuesta(HttpExchange exchange, int codigo, String respuesta) throws IOException {
        byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(codigo, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
