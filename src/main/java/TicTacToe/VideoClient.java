package TicTacToe;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class VideoClient implements Runnable {
    private Socket socket; //Socket connessione client
    private DataOutputStream out; //Stream uscita dati
    private DataInputStream in; //Stream ingresso dati
    private String nickname; //Nickname client
    private String opponent; //Nickname avversario
    private volatile boolean running = true; //Flag esecuzione

    //Costruttore con socket
    public VideoClient(Socket socket) {
        this.socket = socket; //Salva socket
    }

    @Override
    public void run() {
        try {
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream())); //Crea stream uscita
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream())); //Crea stream ingresso

            //Registrazione del client
            int nicknameLength = in.readInt(); //Legge lunghezza nickname
            byte[] nicknameBytes = new byte[nicknameLength]; //Crea buffer
            in.readFully(nicknameBytes); //Legge bytes nickname
            nickname = new String(nicknameBytes, "UTF-8"); //Converte in stringa

            //Leggi anche il nickname dell'avversario
            int opponentLength = in.readInt(); //Legge lunghezza nickname avversario
            byte[] opponentBytes = new byte[opponentLength]; //Crea buffer
            in.readFully(opponentBytes); //Legge bytes nickname avversario
            opponent = new String(opponentBytes, "UTF-8"); //Converte in stringa

            TicTacToeServer.videoClients.put(nickname, this); //Registra client nella mappa
            System.out.println("Client video registrato: " + nickname + " (avversario: " + opponent + ")");

            //Registra la coppia
            TicTacToeServer.registerPair(nickname, opponent); //Associa i due giocatori

            //Invia conferma di registrazione
            out.writeInt(1); //Scrive OK
            out.flush(); //Flush immediato

            //Loop di ricezione frame
            while (running) { //Finché attivo
                try {
                    //Leggi la dimensione del frame
                    int frameSize = in.readInt(); //Legge dimensione frame

                    if (frameSize <= 0 || frameSize > 5_000_000) { //Se dimensione non valida
                        System.err.println("Dimensione frame non valida: " + frameSize);
                        break;
                    }

                    //Leggi i dati del frame
                    byte[] frameData = new byte[frameSize]; //Crea buffer frame
                    in.readFully(frameData); //Legge dati frame

                    //Inoltra il frame all'avversario
                    TicTacToeServer.forwardFrame(nickname, frameData); //Invia frame all'avversario

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
            cleanup(); //Pulizia risorse
        }
    }

    //Invia frame al client
    public synchronized void sendFrame(byte[] frameData) {
        try {
            if (out != null && socket.isConnected() && !socket.isClosed()) { //Se socket valido
                out.writeInt(frameData.length); //Scrive dimensione frame
                out.write(frameData); //Scrive dati frame
                out.flush(); //Flush immediato
            }
        } catch (IOException e) {
            System.err.println("Errore invio frame a " + nickname + ": " + e.getMessage());
            running = false; //Ferma esecuzione
        }
    }

    //Pulizia risorse
    private void cleanup() {
        running = false; //Ferma esecuzione
        if (nickname != null) { //Se nickname presente
            TicTacToeServer.videoClients.remove(nickname); //Rimuove dalla mappa
            TicTacToeServer.unregisterPair(nickname); //Rimuove coppia
            System.out.println("Client video disconnesso: " + nickname);
        }
        try {
            if (socket != null && !socket.isClosed()) { //Se socket aperto
                socket.close(); //Chiude socket
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}