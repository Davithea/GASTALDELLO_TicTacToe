import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TicTacToeServer {
    private static final int PORT = 12345;
    public static HashMap<String, Player> clientConnessi = new HashMap<>();
    public static HashMap<String, Game> partiteAttive = new HashMap<>();
    public static HashMap<String, String> pendingChallenges = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("Tic-Tac-Toe Server avviato sulla porta " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuova connessione da: " + clientSocket.getInetAddress());
                new Thread(new Player(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Errore del server: " + e.getMessage());
        }
    }
}