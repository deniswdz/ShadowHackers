import java.io.*;
import java.net.*;
import java.util.Scanner;
import javax.net.ssl.SSLSocketFactory;

public class Cliente {
    public static void main(String[] args) {
        // Si se pasa un argumento, úsalo como IP/Host, si no, usa localhost
        String host = args.length > 0 ? args[0] : "localhost";
        // El puerto debe coincidir con el del servidor (si es local 12345, si es nube será el asignado, 
        // pero aquí definimos el de conexión por defecto o lo pasamos como segundo arg)
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 12345;

        System.out.println("Conectando a " + host + ":" + port + "...");

        Socket socket = null;
        try {
            // Detección automática de SSL si el puerto es 443 (común en Railway/Render)
            if (port == 443) {
                System.out.println("🔒 Detectado puerto seguro (443). Usando SSL/TLS...");
                socket = SSLSocketFactory.getDefault().createSocket(host, port);
            } else {
                socket = new Socket(host, port);
            }
            
            System.out.println("¡Conectado al servidor Shadow Hackers!");

            // Hilo para recibir mensajes del servidor en tiempo real
            new Thread(new EscuchaServidor(socket)).start();

            // Hilo principal para enviar comandos del usuario
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            while (scanner.hasNextLine()) {
                String comando = scanner.nextLine();
                out.println(comando);
                if (comando.equalsIgnoreCase("EXIT")) {
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("❌ Error: No se pudo conectar al servidor.");
            System.out.println("Detalles: " + e.getMessage());
            if (port == 443) {
                System.out.println("Nota: Si usas Railway/Render, asegúrate de que el dominio es correcto.");
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }

    // Clase interna para escuchar mensajes del servidor constantemente
    private static class EscuchaServidor implements Runnable {
        private Socket socket;

        public EscuchaServidor(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String mensajeServidor;
                while ((mensajeServidor = in.readLine()) != null) {
                    System.out.println(mensajeServidor);
                }
            } catch (IOException e) {
                System.out.println("⚠️ Desconectado del servidor.");
                System.exit(0);
            }
        }
    }
}
