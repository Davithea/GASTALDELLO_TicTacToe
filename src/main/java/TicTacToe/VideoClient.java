package TicTacToe;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class VideoClient implements Runnable {
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

            TicTacToeServer.videoClients.put(nickname, this);
            System.out.println("Client video registrato: " + nickname + " (avversario: " + opponent + ")");

            // Registra la coppia
            TicTacToeServer.registerPair(nickname, opponent);

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
                    TicTacToeServer.forwardFrame(nickname, frameData);

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

    private void cleanup() {
        running = false;
        if (nickname != null) {
            TicTacToeServer.videoClients.remove(nickname);
            TicTacToeServer.unregisterPair(nickname);
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