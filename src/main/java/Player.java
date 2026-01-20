import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

public class Player implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String nickname;

    public Player(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Gestione scelta nickname
            out.println("SET_NICKNAME");
            String nicknameInput = in.readLine();

            if (nicknameInput != null && nicknameInput.startsWith("NICKNAME ")) {
                nickname = nicknameInput.substring(9).trim();

                if (nickname.isEmpty() || TicTacToeServer.clientConnessi.containsKey(nickname)) {
                    out.println("NICKNAME_ERROR");
                    socket.close();
                    return;
                }

                TicTacToeServer.clientConnessi.put(nickname, this);
                out.println("NICKNAME_SUCCESS " + nickname);
                System.out.println("Utente registrato: " + nickname);

                broadcastPlayerList();

                // Loop messaggi
                String message;
                while ((message = in.readLine()) != null) {
                    processMessage(message);
                }
            }
        } catch (IOException e) {
            System.err.println("Errore con client " + nickname + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void processMessage(String message) {
        String[] parts = message.split(" ", 2);
        String command = parts[0];

        switch (command) {
            case "LIST_PLAYERS":
                sendPlayerList();
                break;

            case "CHALLENGE":
                if (parts.length > 1) {
                    String opponent = parts[1];
                    sendChallenge(opponent);
                }
                break;

            case "CHALLENGE_ACCEPT":
                if (parts.length > 1) {
                    String challenger = parts[1];
                    acceptChallenge(challenger);
                }
                break;

            case "CHALLENGE_REJECT":
                if (parts.length > 1) {
                    String challenger = parts[1];
                    rejectChallenge(challenger);
                }
                break;

            case "MOVE":
                if (parts.length > 1) {
                    handleMove(parts[1]);
                }
                break;
        }
    }

    private void sendPlayerList() {
        StringBuilder playerList = new StringBuilder("LIST_PLAYERS");
        for (String player : TicTacToeServer.clientConnessi.keySet()) {
            if (!player.equals(nickname)) {
                playerList.append(" ").append(player);
            }
        }
        out.println(playerList.toString());
    }

    private void broadcastPlayerList() {
        for (Player client : TicTacToeServer.clientConnessi.values()) {
            client.sendPlayerList();
        }
    }

    private void sendChallenge(String opponent) {
        Player opponentHandler = TicTacToeServer.clientConnessi.get(opponent);
        if (opponentHandler == null) {
            out.println("CHALLENGE_ERROR Giocatore non disponibile");
            return;
        }

        String gameId = nickname + "-" + opponent;
        if (TicTacToeServer.partiteAttive.containsKey(gameId) || TicTacToeServer.partiteAttive.containsKey(opponent + "-" + nickname)) {
            out.println("CHALLENGE_ERROR Partita già in corso");
            return;
        }

        TicTacToeServer.pendingChallenges.put(opponent, nickname);
        opponentHandler.out.println("CHALLENGE_REQUEST " + nickname);
        out.println("CHALLENGE_SENT " + opponent);
        System.out.println(nickname + " ha sfidato " + opponent);
    }

    private void acceptChallenge(String challenger) {
        String pendingChallenger = TicTacToeServer.pendingChallenges.get(nickname);
        if (pendingChallenger == null || !pendingChallenger.equals(challenger)) {
            out.println("CHALLENGE_ERROR Sfida non valida");
            return;
        }

        TicTacToeServer.pendingChallenges.remove(nickname);
        Player challengerHandler;
        challengerHandler = TicTacToeServer.clientConnessi.get(challenger);

        if (challengerHandler == null) {
            out.println("CHALLENGE_ERROR Sfidante non disponibile");
            return;
        }

        // Crea la partita
        String gameId = challenger + "-" + nickname;
        Game game = new Game(challengerHandler, this, gameId);
        TicTacToeServer.partiteAttive.put(gameId, game);

        challengerHandler.out.println("CHALLENGE_ACCEPTED " + nickname);
        out.println("CHALLENGE_ACCEPTED " + challenger);

        // Avvia la partita
        new Thread(game).start();
        System.out.println("Partita avviata: " + gameId);
    }

    private void rejectChallenge(String challenger) {
        String pendingChallenger = TicTacToeServer.pendingChallenges.get(nickname);
        if (pendingChallenger != null && pendingChallenger.equals(challenger)) {
            TicTacToeServer.pendingChallenges.remove(nickname);
            Player challengerHandler;
            challengerHandler = TicTacToeServer.clientConnessi.get(challenger);
            if (challengerHandler != null) {
                challengerHandler.out.println("CHALLENGE_REJECTED " + nickname);
            }
            System.out.println(nickname + " ha rifiutato la sfida di " + challenger);
        }
    }

    private void handleMove(String position) {
        // Trova la partita in cui è coinvolto questo giocatore
        for (Map.Entry<String, Game> entry : TicTacToeServer.partiteAttive.entrySet()) {
            Game game = entry.getValue();
            if (!game.hasPlayer(this)) {
                continue;
            }
            game.handleMove(this, position);
            break;
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String getNickname() {
        return nickname;
    }

    private void cleanup() {
        if (nickname != null) {
            TicTacToeServer.clientConnessi.remove(nickname);
            TicTacToeServer.pendingChallenges.remove(nickname);

            // Rimuovi dalle partite attive
            TicTacToeServer.partiteAttive.entrySet().removeIf(entry -> {
                if (entry.getValue().hasPlayer(this)) {
                    entry.getValue().playerDisconnected(this);
                    return true;
                }
                return false;
            });

            broadcastPlayerList();
            System.out.println("Utente disconnesso: " + nickname);
        }

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}