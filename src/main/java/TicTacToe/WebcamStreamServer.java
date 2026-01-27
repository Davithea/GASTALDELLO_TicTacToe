package TicTacToe;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server per gestire lo streaming video tra peers
 * Gestisce la registrazione dei client e l'inoltro dei frame video
 */
public class WebcamStreamServer {
    private static final int VIDEO_PORT = 12347; // Porta dedicata per video streaming
    public static Map<String, VideoClient> videoClients = new ConcurrentHashMap<>();
    public static Map<String, String> activePairs = new ConcurrentHashMap<>(); // nickname -> opponent

    public static void main(String[] args) {
        System.out.println("Webcam Stream Server avviato sulla porta " + VIDEO_PORT);
        try (ServerSocket serverSocket = new ServerSocket(VIDEO_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuova connessione video da: " + clientSocket.getInetAddress());
                new Thread(new VideoClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Errore server video: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Registra una coppia di giocatori per lo streaming video
     */
    public static void registerPair(String player1, String player2) {
        activePairs.put(player1, player2);
        activePairs.put(player2, player1);
        System.out.println("Coppia video registrata: " + player1 + " <-> " + player2);
    }

    /**
     * Rimuove una coppia di giocatori
     */
    public static void unregisterPair(String player) {
        String opponent = activePairs.remove(player);
        if (opponent != null) {
            activePairs.remove(opponent);
            System.out.println("Coppia video rimossa: " + player + " <-> " + opponent);
        }
    }

    /**
     * Inoltra un frame video all'avversario
     */
    public static void forwardFrame(String sender, byte[] frameData) {
        String recipient = activePairs.get(sender);
        if (recipient != null) {
            VideoClient client = videoClients.get(recipient);
            if (client != null) {
                client.sendFrame(frameData);
            } else {
                System.out.println("Client destinatario non trovato: " + recipient);
            }
        } else {
            System.out.println("Nessun avversario registrato per: " + sender);
        }
    }
}

/**
 * Gestisce la connessione video di un singolo client
 */
class VideoClient implements Runnable {
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String nickname;
    private String opponent;
    private volatile boolean running = true;

    public VideoClient(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            // Registrazione del client
            int nicknameLength = in.readInt();
            byte[] nicknameBytes = new byte[nicknameLength];
            in.readFully(nicknameBytes);
            nickname = new String(nicknameBytes, "UTF-8");

            // Leggi anche il nickname dell'avversario
            int opponentLength = in.readInt();
            byte[] opponentBytes = new byte[opponentLength];
            in.readFully(opponentBytes);
            opponent = new String(opponentBytes, "UTF-8");

            WebcamStreamServer.videoClients.put(nickname, this);
            System.out.println("Client video registrato: " + nickname + " (avversario: " + opponent + ")");

            // Registra la coppia
            WebcamStreamServer.registerPair(nickname, opponent);

            // Invia conferma di registrazione
            out.writeInt(1); // OK
            out.flush();

            // Loop di ricezione frame
            while (running) {
                try {
                    // Leggi la dimensione del frame
                    int frameSize = in.readInt();

                    if (frameSize <= 0 || frameSize > 5_000_000) { // Max 5MB per frame
                        System.err.println("Dimensione frame non valida: " + frameSize);
                        break;
                    }

                    // Leggi i dati del frame
                    byte[] frameData = new byte[frameSize];
                    in.readFully(frameData);

                    // Inoltra il frame all'avversario
                    WebcamStreamServer.forwardFrame(nickname, frameData);

                } catch (EOFException e) {
                    System.out.println("Connessione video chiusa: " + nickname);
                    break;
                } catch (SocketException e) {
                    System.out.println("Socket video chiuso: " + nickname);
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Errore client video " + nickname + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Invia un frame video al client
     */
    public synchronized void sendFrame(byte[] frameData) {
        try {
            if (out != null && socket.isConnected() && !socket.isClosed()) {
                out.writeInt(frameData.length);
                out.write(frameData);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Errore invio frame a " + nickname + ": " + e.getMessage());
            running = false;
        }
    }

    /**
     * Chiude la connessione e pulisce le risorse
     */
    private void cleanup() {
        running = false;
        if (nickname != null) {
            WebcamStreamServer.videoClients.remove(nickname);
            WebcamStreamServer.unregisterPair(nickname);
            System.out.println("Client video disconnesso: " + nickname);
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}