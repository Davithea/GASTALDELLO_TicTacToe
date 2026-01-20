import java.util.Arrays;

public class Game implements Runnable {
    private Player player1;
    private Player player2;
    private String gameId;
    private String[] board = new String[9];
    private String currentPlayer = "X";
    private boolean gameOver = false;

    public Game(Player p1, Player p2, String gameId) {
        this.player1 = p1;
        this.player2 = p2;
        this.gameId = gameId;
        Arrays.fill(board, "");
    }

    @Override
    public void run() {
        player1.sendMessage("GAME_START X " + player2.getNickname());
        player2.sendMessage("GAME_START O " + player1.getNickname());
        player1.sendMessage("YOUR_TURN");
    }

    public synchronized void handleMove(Player player, String position) {
        if (gameOver) return;

        String playerSymbol = (player == player1) ? "X" : "O";
        if (!playerSymbol.equals(currentPlayer)) {
            player.sendMessage("NOT_YOUR_TURN");
            return;
        }

        int pos = Integer.parseInt(position);
        if (pos < 0 || pos >= 9 || !board[pos].equals("")) {
            player.sendMessage("INVALID_MOVE");
            return;
        }

        board[pos] = currentPlayer;
        player1.sendMessage("BOARD " + pos + " " + currentPlayer);
        player2.sendMessage("BOARD " + pos + " " + currentPlayer);

        String winner = checkWinner();
        if (winner != null) {
            gameOver = true;
            player1.sendMessage("GAME_OVER " + winner);
            player2.sendMessage("GAME_OVER " + winner);
            TicTacToeServer.partiteAttive.remove(gameId);
            return;
        }

        if (isBoardFull()) {
            gameOver = true;
            player1.sendMessage("GAME_OVER TIE");
            player2.sendMessage("GAME_OVER TIE");
            TicTacToeServer.partiteAttive.remove(gameId);
            return;
        }

        currentPlayer = currentPlayer.equals("X") ? "O" : "X";
        if (currentPlayer.equals("X")) {
            player1.sendMessage("YOUR_TURN");
            player2.sendMessage("OPPONENT_TURN");
        } else {
            player2.sendMessage("YOUR_TURN");
            player1.sendMessage("OPPONENT_TURN");
        }
    }

    public boolean hasPlayer(Player player) {
        return player == player1 || player == player2;
    }

    public void playerDisconnected(Player player) {
        if (!gameOver) {
            gameOver = true;
            Player other = (player == player1) ? player2 : player1;
            if (other != null) {
                other.sendMessage("OPPONENT_DISCONNECTED");
            }
        }
    }

    private String checkWinner() {
        int[][] wins = {
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
                {0, 4, 8}, {2, 4, 6}
        };

        for (int[] win : wins) {
            if (!board[win[0]].equals("") &&
                    board[win[0]].equals(board[win[1]]) &&
                    board[win[1]].equals(board[win[2]])) {
                return board[win[0]];
            }
        }
        return null;
    }

    private boolean isBoardFull() {
        for (String cell : board) {
            if (cell.equals("")) return false;
        }
        return true;
    }
}