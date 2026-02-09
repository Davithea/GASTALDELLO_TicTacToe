package TicTacToe;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TicTacToeServer {
    // Porte per i diversi servizi
    private static final int GAME_PORT = 12345;  // Porta per il gioco
    private static final int VIDEO_PORT = 12347; // Porta per lo streaming video

    // Mappe condivise per la gestione dei client e delle partite
    public static Map<String, Player> connectedClients = new ConcurrentHashMap<>();
    public static Map<String, Game> activeGames = new ConcurrentHashMap<>();
    public static Map<String, String> pendingChallenges = new ConcurrentHashMap<>();

    // Mappe per la gestione dello streaming video
    public static Map<String, VideoClient> videoClients = new ConcurrentHashMap<>();
    public static Map<String, String> activePairs = new ConcurrentHashMap<>(); // nickname -> opponent

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("Tic-Tac-Toe Unified Server Avviato");
        System.out.println("Porta Game: " + GAME_PORT);
        System.out.println("Porta Video: " + VIDEO_PORT);
        System.out.println("=================================================");

        // Avvia il server di gioco in un thread separato
        Thread gameServerThread = new Thread(() -> startGameServer());
        gameServerThread.setName("GameServerThread");
        gameServerThread.start();

        // Avvia il server video in un thread separato
        Thread videoServerThread = new Thread(() -> startVideoServer());
        videoServerThread.setName("VideoServerThread");
        videoServerThread.start();
    }

    // Server per la gestione del gioco
    private static void startGameServer() {
        System.out.println("Game Server in ascolto sulla porta " + GAME_PORT);
        try (ServerSocket serverSocket = new ServerSocket(GAME_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuova connessione GAME da: " + clientSocket.getInetAddress());
                new Thread(new Player(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Errore Game Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Server per lo streaming video
    private static void startVideoServer() {
        System.out.println("Video Server in ascolto sulla porta " + VIDEO_PORT);
        try (ServerSocket serverSocket = new ServerSocket(VIDEO_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuova connessione VIDEO da: " + clientSocket.getInetAddress());
                new Thread(new VideoClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Errore Video Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Metodi per la gestione delle coppie video
    public static void registerPair(String player1, String player2) {
        activePairs.put(player1, player2);
        activePairs.put(player2, player1);
        System.out.println("Coppia video registrata: " + player1 + " <-> " + player2);
    }

    public static void unregisterPair(String player) {
        String opponent = activePairs.remove(player);
        if (opponent != null) {
            activePairs.remove(opponent);
            System.out.println("Coppia video rimossa: " + player + " <-> " + opponent);
        }
    }

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