package TicTacToe;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

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