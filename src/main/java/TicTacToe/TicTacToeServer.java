package TicTacToe;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TicTacToeServer {
    private static final int PORT = 12345; //Porta su cui il server ascolta le connessioni
    public static Map<String, Player> connectedClients = new ConcurrentHashMap<>(); //Map dei client connessi (nickname -> Player)
    public static Map<String, Game> activeGames = new ConcurrentHashMap<>(); //Map delle partite attive (id partita -> Game)
    public static Map<String, String> pendingChallenges = new ConcurrentHashMap<>(); //Map delle sfide in sospeso (sfidato -> sfidante)

    public static void main(String[] args) { //Punto di ingresso del server
        System.out.println("Tic-Tac-Toe Server avviato sulla porta " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) { //Crea il ServerSocket sulla porta definita
            while (true) { //Loop infinito per accettare connessioni
                Socket clientSocket = serverSocket.accept(); //Accetta una nuova connessione
                System.out.println("Nuova connessione da: " + clientSocket.getInetAddress());
                new Thread(new Player(clientSocket)).start(); //Avvia un thread dedicato per gestire il client
            }
        } catch (IOException e) {
            System.err.println("Errore del server: " + e.getMessage());
        }
    }
}