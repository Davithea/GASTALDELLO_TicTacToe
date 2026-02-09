package TicTacToe;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TicTacToeServer {
    //Porte per i diversi servizi
    private static final int GAME_PORT = 12345; //Porta per la gestione del gioco
    private static final int VIDEO_PORT = 12347; //Porta per lo streaming video

    //Mappe condivise per la gestione dei client e delle partite
    public static Map<String, Player> connectedClients = new ConcurrentHashMap<>(); //Mappa client connessi
    public static Map<String, Game> activeGames = new ConcurrentHashMap<>(); //Mappa partite attive
    public static Map<String, String> pendingChallenges = new ConcurrentHashMap<>(); //Mappa sfide pendenti

    //Mappe per la gestione dello streaming video
    public static Map<String, VideoClient> videoClients = new ConcurrentHashMap<>(); //Mappa client video
    public static Map<String, String> activePairs = new ConcurrentHashMap<>(); //Mappa coppie video attive

    //Entry point server
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("Tic-Tac-Toe Unified Server Avviato");
        System.out.println("Porta Game: " + GAME_PORT);
        System.out.println("Porta Video: " + VIDEO_PORT);
        System.out.println("=================================================");

        //Avvia il server di gioco in un thread separato
        Thread gameServerThread = new Thread(() -> startGameServer()); //Crea thread server gioco
        gameServerThread.setName("GameServerThread"); //Assegna nome thread
        gameServerThread.start(); //Avvia thread

        //Avvia il server video in un thread separato
        Thread videoServerThread = new Thread(() -> startVideoServer()); //Crea thread server video
        videoServerThread.setName("VideoServerThread"); //Assegna nome thread
        videoServerThread.start(); //Avvia thread
    }

    //Server per la gestione del gioco
    private static void startGameServer() {
        System.out.println("Game Server in ascolto sulla porta " + GAME_PORT);
        try (ServerSocket serverSocket = new ServerSocket(GAME_PORT)) { //Apre socket sulla porta gioco
            while (true) { //Loop infinito
                Socket clientSocket = serverSocket.accept(); //Accetta connessione
                System.out.println("Nuova connessione GAME da: " + clientSocket.getInetAddress());
                new Thread(new Player(clientSocket)).start(); //Crea thread per gestire client
            }
        } catch (IOException e) {
            System.err.println("Errore Game Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //Server per lo streaming video
    private static void startVideoServer() {
        System.out.println("Video Server in ascolto sulla porta " + VIDEO_PORT);
        try (ServerSocket serverSocket = new ServerSocket(VIDEO_PORT)) { //Apre socket sulla porta video
            while (true) { //Loop infinito
                Socket clientSocket = serverSocket.accept(); //Accetta connessione
                System.out.println("Nuova connessione VIDEO da: " + clientSocket.getInetAddress());
                new Thread(new VideoClient(clientSocket)).start(); //Crea thread per gestire client video
            }
        } catch (IOException e) {
            System.err.println("Errore Video Server: " + e.getMessage());
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