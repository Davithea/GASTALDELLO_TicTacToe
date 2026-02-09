package TicTacToe;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class WebcamStreamServer {
    private static final int VIDEO_PORT = 12347; //Porta dedicata per video streaming
    public static Map<String, VideoClient> videoClients = new ConcurrentHashMap<>(); //Mappa client video
    public static Map<String, String> activePairs = new ConcurrentHashMap<>(); //Mappa coppie video attive

    //Entry point server video
    public static void main(String[] args) {
        System.out.println("Webcam Stream Server avviato sulla porta " + VIDEO_PORT);
        try (ServerSocket serverSocket = new ServerSocket(VIDEO_PORT)) { //Apre socket sulla porta video
            while (true) { //Loop infinito
                Socket clientSocket = serverSocket.accept(); //Accetta connessione
                System.out.println("Nuova connessione video da: " + clientSocket.getInetAddress());
                new Thread(new VideoClient(clientSocket)).start(); //Crea thread per gestire client video
            }
        } catch (IOException e) {
            System.err.println("Errore server video: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //Registra coppia di giocatori per video
    public static void registerPair(String player1, String player2) {
        activePairs.put(player1, player2); //Associa player1 a player2
        activePairs.put(player2, player1); //Associa player2 a player1
        System.out.println("Coppia video registrata: " + player1 + " <-> " + player2);
    }

    //Rimuove coppia video
    public static void unregisterPair(String player) {
        String opponent = activePairs.remove(player); //Rimuove player e ottiene avversario
        if (opponent != null) { //Se aveva un avversario
            activePairs.remove(opponent); //Rimuove anche avversario
            System.out.println("Coppia video rimossa: " + player + " <-> " + opponent);
        }
    }

    //Inoltra frame video all'avversario
    public static void forwardFrame(String sender, byte[] frameData) {
        String recipient = activePairs.get(sender); //Ottiene destinatario
        if (recipient != null) { //Se esiste destinatario
            VideoClient client = videoClients.get(recipient); //Recupera client video
            if (client != null) { //Se client esiste
                client.sendFrame(frameData); //Invia frame
            } else {
                System.out.println("Client destinatario non trovato: " + recipient);
            }
        } else {
            System.out.println("Nessun avversario registrato per: " + sender);
        }
    }
}