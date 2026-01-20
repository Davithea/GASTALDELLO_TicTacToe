import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.*;

public class TicTacToeClient {
    int boardWidth = 600;
    int boardHeight = 700;

    JFrame frame = new JFrame("Tic-Tac-Toe Multiplayer");
    JLabel textLabel = new JLabel();
    JPanel textPanel = new JPanel();
    JPanel boardPanel = new JPanel();
    JPanel lobbyPanel = new JPanel();

    JList<String> playerList;
    DefaultListModel<String> playerListModel;
    JButton challengeButton;
    JButton refreshButton;

    JButton[][] board = new JButton[3][3];
    String playerX = "X";
    String playerO = "O";
    String myPlayer;
    String myNickname;
    String opponentNickname;

    boolean gameOver = false;
    boolean myTurn = false;
    boolean inGame = false;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    TicTacToeClient(String serverAddress) {
        try {
            socket = new Socket(serverAddress, 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            setupGUI();
            new Thread(this::receiveMessages).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame,
                    "Impossibile connettersi al server: " + e.getMessage(),
                    "Errore di Connessione",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    void setupGUI() {
        frame.setVisible(true);
        frame.setSize(boardWidth, boardHeight);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        textLabel.setBackground(Color.darkGray);
        textLabel.setForeground(Color.white);
        textLabel.setFont(new Font("Arial", Font.BOLD, 30));
        textLabel.setHorizontalAlignment(JLabel.CENTER);
        textLabel.setText("Connessione al server...");
        textLabel.setOpaque(true);

        textPanel.setLayout(new BorderLayout());
        textPanel.add(textLabel);
        frame.add(textPanel, BorderLayout.NORTH);

        // Setup lobby panel
        setupLobbyPanel();
        frame.add(lobbyPanel, BorderLayout.CENTER);

        // Setup board panel (nascosto inizialmente)
        setupBoardPanel();
        boardPanel.setVisible(false);
    }

    void setupLobbyPanel() {
        lobbyPanel.setLayout(new BorderLayout());
        lobbyPanel.setBackground(Color.darkGray);

        JLabel lobbyLabel = new JLabel("Giocatori Connessi");
        lobbyLabel.setForeground(Color.white);
        lobbyLabel.setFont(new Font("Arial", Font.BOLD, 20));
        lobbyLabel.setHorizontalAlignment(JLabel.CENTER);
        lobbyPanel.add(lobbyLabel, BorderLayout.NORTH);

        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        playerList.setBackground(Color.gray);
        playerList.setForeground(Color.white);
        playerList.setFont(new Font("Arial", Font.PLAIN, 16));
        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(playerList);
        lobbyPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(Color.darkGray);

        challengeButton = new JButton("Sfida");
        challengeButton.setFont(new Font("Arial", Font.BOLD, 16));
        challengeButton.addActionListener(e -> challengeSelectedPlayer());

        refreshButton = new JButton("Aggiorna");
        refreshButton.setFont(new Font("Arial", Font.BOLD, 16));
        refreshButton.addActionListener(e -> requestPlayerList());

        buttonPanel.add(challengeButton);
        buttonPanel.add(refreshButton);
        lobbyPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    void setupBoardPanel() {
        boardPanel.setLayout(new GridLayout(3, 3));
        boardPanel.setBackground(Color.darkGray);

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                JButton tile = new JButton();
                board[r][c] = tile;
                boardPanel.add(tile);

                tile.setBackground(Color.darkGray);
                tile.setForeground(Color.white);
                tile.setFont(new Font("Arial", Font.BOLD, 120));
                tile.setFocusable(false);

                int row = r;
                int col = c;
                tile.addActionListener(e -> {
                    if (gameOver || !myTurn || !inGame) return;
                    JButton clickedTile = (JButton) e.getSource();
                    if (clickedTile.getText().equals("")) {
                        int position = row * 3 + col;
                        out.println("MOVE " + position);
                    }
                });
            }
        }
    }

    void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Ricevuto: " + message);
                processMessage(message);
            }
        } catch (IOException e) {
            if (!gameOver) {
                SwingUtilities.invokeLater(() -> {
                    textLabel.setText("Connessione persa!");
                });
            }
        }
    }

    void processMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String[] parts = message.split(" ", 2);

            switch (parts[0]) {
                case "SET_NICKNAME":
                    askNickname();
                    break;

                case "NICKNAME_SUCCESS":
                    myNickname = parts[1];
                    textLabel.setText("Benvenuto, " + myNickname + "!");
                    requestPlayerList();
                    break;

                case "NICKNAME_ERROR":
                    JOptionPane.showMessageDialog(frame, "Nickname non valido o già in uso!");
                    askNickname();
                    break;

                case "LIST_PLAYERS":
                    updatePlayerList(parts.length > 1 ? parts[1] : "");
                    break;

                case "CHALLENGE_REQUEST":
                    handleChallengeRequest(parts[1]);
                    break;

                case "CHALLENGE_SENT":
                    textLabel.setText("Sfida inviata a " + parts[1]);
                    break;

                case "CHALLENGE_ACCEPTED":
                    opponentNickname = parts[1];
                    textLabel.setText("Sfida accettata! Inizio partita...");
                    break;

                case "CHALLENGE_REJECTED":
                    JOptionPane.showMessageDialog(frame, parts[1] + " ha rifiutato la sfida");
                    textLabel.setText("Benvenuto, " + myNickname + "!");
                    break;

                case "CHALLENGE_ERROR":
                    JOptionPane.showMessageDialog(frame, parts.length > 1 ? parts[1] : "Errore sfida");
                    break;

                case "GAME_START":
                    startGame(parts[1].split(" ")[0], parts[1].split(" ")[1]);
                    break;

                case "YOUR_TURN":
                    myTurn = true;
                    textLabel.setText("Il tuo turno (" + myPlayer + ")");
                    break;

                case "OPPONENT_TURN":
                    myTurn = false;
                    textLabel.setText("Turno di " + opponentNickname + "...");
                    break;

                case "BOARD":
                    String[] boardParts = parts[1].split(" ");
                    int position = Integer.parseInt(boardParts[0]);
                    String player = boardParts[1];
                    int row = position / 3;
                    int col = position % 3;
                    board[row][col].setText(player);
                    break;

                case "GAME_OVER":
                    handleGameOver(parts[1]);
                    break;

                case "OPPONENT_DISCONNECTED":
                    gameOver = true;
                    inGame = false;
                    textLabel.setText(opponentNickname + " si è disconnesso");
                    JOptionPane.showMessageDialog(frame, "L'avversario si è disconnesso");
                    returnToLobby();
                    break;

                case "NOT_YOUR_TURN":
                    JOptionPane.showMessageDialog(frame, "Non è il tuo turno!");
                    break;

                case "INVALID_MOVE":
                    JOptionPane.showMessageDialog(frame, "Mossa non valida!");
                    break;
            }
        });
    }

    void askNickname() {
        String nickname = JOptionPane.showInputDialog(frame,
                "Inserisci il tuo nickname:",
                "Scelta Nickname",
                JOptionPane.QUESTION_MESSAGE);

        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = "Player" + (int)(Math.random() * 1000);
        }

        out.println("NICKNAME " + nickname);
    }

    void requestPlayerList() {
        out.println("LIST_PLAYERS");
    }

    void updatePlayerList(String players) {
        playerListModel.clear();
        if (!players.isEmpty()) {
            String[] playerArray = players.split(" ");
            for (String player : playerArray) {
                playerListModel.addElement(player);
            }
        }
    }

    void challengeSelectedPlayer() {
        String selectedPlayer = playerList.getSelectedValue();
        if (selectedPlayer == null) {
            JOptionPane.showMessageDialog(frame, "Seleziona un giocatore da sfidare!");
            return;
        }
        out.println("CHALLENGE " + selectedPlayer);
    }

    void handleChallengeRequest(String challenger) {
        int response = JOptionPane.showConfirmDialog(frame,
                challenger + " ti ha sfidato! Accetti?",
                "Richiesta di Sfida",
                JOptionPane.YES_NO_OPTION);

        if (response == JOptionPane.YES_OPTION) {
            out.println("CHALLENGE_ACCEPT " + challenger);
            opponentNickname = challenger;
        } else {
            out.println("CHALLENGE_REJECT " + challenger);
        }
    }

    void startGame(String symbol, String opponent) {
        myPlayer = symbol;
        opponentNickname = opponent;
        inGame = true;
        gameOver = false;
        myTurn = symbol.equals("X");

        // Pulisci la griglia
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                board[r][c].setText("");
                board[r][c].setBackground(Color.darkGray);
                board[r][c].setForeground(Color.white);
                board[r][c].setEnabled(true);
            }
        }

        // Mostra il pannello di gioco
        lobbyPanel.setVisible(false);
        frame.add(boardPanel, BorderLayout.CENTER);
        boardPanel.setVisible(true);
        frame.revalidate();

        textLabel.setText("Partita vs " + opponentNickname + " - Sei " + myPlayer);
    }

    void handleGameOver(String result) {
        gameOver = true;
        inGame = false;
        myTurn = false;

        if (result.equals("TIE")) {
            textLabel.setText("Pareggio!");
            setTieColors();
        } else {
            if (result.equals(myPlayer)) {
                textLabel.setText("Hai vinto contro " + opponentNickname + "!");
            } else {
                textLabel.setText("Hai perso contro " + opponentNickname + "!");
            }
            highlightWinner();
        }

        Timer timer = new Timer(3000, e -> returnToLobby());
        timer.setRepeats(false);
        timer.start();
    }

    void returnToLobby() {
        boardPanel.setVisible(false);
        frame.remove(boardPanel);
        lobbyPanel.setVisible(true);
        frame.add(lobbyPanel, BorderLayout.CENTER);
        frame.revalidate();
        textLabel.setText("Benvenuto, " + myNickname + "!");
        requestPlayerList();
    }

    void highlightWinner() {
        int[][] wins = {
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
                {0, 4, 8}, {2, 4, 6}
        };

        for (int[] win : wins) {
            int r1 = win[0] / 3, c1 = win[0] % 3;
            int r2 = win[1] / 3, c2 = win[1] % 3;
            int r3 = win[2] / 3, c3 = win[2] % 3;

            if (!board[r1][c1].getText().equals("") &&
                    board[r1][c1].getText().equals(board[r2][c2].getText()) &&
                    board[r2][c2].getText().equals(board[r3][c3].getText())) {

                board[r1][c1].setForeground(Color.green);
                board[r1][c1].setBackground(Color.gray);
                board[r2][c2].setForeground(Color.green);
                board[r2][c2].setBackground(Color.gray);
                board[r3][c3].setForeground(Color.green);
                board[r3][c3].setBackground(Color.gray);
                break;
            }
        }
    }

    void setTieColors() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                board[r][c].setForeground(Color.orange);
                board[r][c].setBackground(Color.gray);
            }
        }
    }

    public static void main(String[] args) {
        String serverAddress = JOptionPane.showInputDialog(
                null,
                "Inserisci l'indirizzo IP del server:",
                "Connessione al Server",
                JOptionPane.QUESTION_MESSAGE
        );

        if (serverAddress == null || serverAddress.trim().isEmpty()) {
            serverAddress = "localhost";
        }

        new TicTacToeClient(serverAddress);
    }
}