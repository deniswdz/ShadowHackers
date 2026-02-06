import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Servidor {
    // Puerto por defecto (se puede sobrescribir con variable de entorno para la nube)
    private static final int PORT = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 12345;
    
    // Estado del juego: ID del nodo -> Nombre del dueño ("Libre" si no tiene dueño)
    private static final Map<Integer, String> nodos = new ConcurrentHashMap<>();
    // Puntuaciones: Nombre jugador -> Puntos
    private static final Map<String, Integer> puntuaciones = new ConcurrentHashMap<>();
    // Lista de clientes conectados para enviar mensajes a todos (broadcast)
    private static final Set<PrintWriter> clientes = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        System.out.println("Shadow Hackers Server iniciando...");
        
        // Inicializar 10 nodos como "Libre"
        for (int i = 1; i <= 10; i++) {
            nodos.put(i, "Libre");
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor escuchando en puerto: " + PORT);

            while (true) {
                // Aceptar nuevos jugadores
                Socket socket = serverSocket.accept();
                System.out.println("Nuevo jugador conectado: " + socket.getInetAddress());
                
                // Crear un hilo para manejar a este jugador
                new Thread(new ManejadorCliente(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Clase interna para manejar cada cliente en un hilo separado
    private static class ManejadorCliente implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String nombreJugador;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Configurar streams de entrada y salida
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Añadir este cliente a la lista de difusión
                clientes.add(out);

                // Pedir nombre o asignar uno temporal
                out.println("Bienvenido a Shadow Hackers! Ingresa tu nombre:");
                nombreJugador = in.readLine();
                if (nombreJugador == null || nombreJugador.trim().isEmpty()) {
                    nombreJugador = "Hacker-" + (int)(Math.random() * 1000);
                }
                puntuaciones.putIfAbsent(nombreJugador, 0);
                
                broadcast(">>> " + nombreJugador + " se ha unido al juego!");
                out.println("Comandos: HACK <num_nodo>, STATUS, EXIT");

                String mensaje;
                while ((mensaje = in.readLine()) != null) {
                    procesarComando(mensaje);
                }
            } catch (IOException e) {
                System.out.println("Error con jugador " + nombreJugador);
            } finally {
                // Desconexión
                try { socket.close(); } catch (IOException e) {}
                clientes.remove(out);
                if (nombreJugador != null) {
                    broadcast("<<< " + nombreJugador + " ha abandonado el juego.");
                }
            }
        }

        // Lógica del juego y comandos
        private void procesarComando(String comando) {
            String[] partes = comando.trim().split(" ");
            String accion = partes[0].toUpperCase();

            if (accion.equals("HACK")) {
                if (partes.length < 2) {
                    out.println("Uso: HACK <numero_nodo>");
                    return;
                }
                try {
                    int nodoId = Integer.parseInt(partes[1]);
                    intentarHackeo(nodoId);
                } catch (NumberFormatException e) {
                    out.println("El ID del nodo debe ser un número.");
                }
            } else if (accion.equals("STATUS")) {
                enviarEstado();
            } else if (accion.equals("EXIT")) {
                try { socket.close(); } catch (IOException e) {}
            } else {
                out.println("Comando desconocido. Usa: HACK, STATUS, EXIT");
            }
        }

        // Sincronización crítica: varios hilos pueden intentar hackear el mismo nodo a la vez
        private synchronized void intentarHackeo(int nodoId) {
            if (!nodos.containsKey(nodoId)) {
                out.println("Error: El nodo " + nodoId + " no existe.");
                return;
            }

            String dueñoActual = nodos.get(nodoId);
            
            // Lógica simple: Siempre se puede hackear si no es tuyo
            if (!dueñoActual.equals(nombreJugador)) {
                nodos.put(nodoId, nombreJugador);
                // Actualizar puntuación
                puntuaciones.put(nombreJugador, puntuaciones.get(nombreJugador) + 10);
                // Avisar a todos
                broadcast("¡ALERTA! " + nombreJugador + " ha hackeado el NODO " + nodoId + " (antes: " + dueñoActual + ")");
            } else {
                out.println("Ya controlas el nodo " + nodoId);
            }
        }

        private void enviarEstado() {
            out.println("=== ESTADO DEL SISTEMA ===");
            // Mostrar nodos ordenados
            nodos.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.println("Nodo " + entry.getKey() + ": [" + entry.getValue() + "]"));
            
            out.println("--- PUNTUACIONES ---");
            puntuaciones.forEach((k, v) -> out.println(k + ": " + v + " pts"));
            out.println("==========================");
        }

        // Enviar mensaje a TODOS los jugadores conectados
        private void broadcast(String msg) {
            for (PrintWriter cliente : clientes) {
                cliente.println(msg);
            }
        }
    }
}
