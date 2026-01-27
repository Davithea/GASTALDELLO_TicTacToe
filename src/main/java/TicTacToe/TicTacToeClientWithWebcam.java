package TicTacToe;

import java.awt.*;
import javax.swing.*;
import java.io.*;
import java.net.*;

/**
 * Client Tic-Tac-Toe con supporto video integrato
 * Usa la libreria webcam-capture per gestire lo streaming video direttamente in Swing
 */
public class TicTacToeClientWithWebcam extends JFrame {
    int boardWidth = 600;
    int boardHeight = 700;
    JLabel textLabel = new JLabel();
    JPanel textPanel = new JPanel();
    JPanel boardPanel = new JPanel();
    JPanel lobbyPanel = new JPanel();
    JList<String> playerList;
    DefaultListModel<String> playerListModel;
    JButton challengeButton;
    JButton refreshButton;
    JButton[][] board = new JButton[3][3];

    // Pannello video
    VideoPanel videoPanel;
    JButton videoToggleButton;

    String myPlayer;
    String myNickname;
    String opponentNickname;
    boolean gameOver = false;
    boolean myTurn = false;
    boolean inGame = false;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String serverAddress;

    TicTacToeClientWithWebcam(String serverAddress) {
        this.serverAddress = serverAddress;
        try {
            socket = new Socket(serverAddress, 12345);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            setupGUI();
            new Thread(this::receiveMessages).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Impossibile connettersi al server: " + e.getMessage(),
                    "Errore di Connessione",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    void setupGUI() {
        setTitle("Tic-Tac-Toe Multiplayer con Video");
        setVisible(true);
        setSize(boardWidth, boardHeight); // Spazio extra per video
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        textLabel.setBackground(Color.darkGray);
        textLabel.setForeground(Color.white);
        textLabel.setFont(new Font("Arial", Font.BOLD, 30));
        textLabel.setHorizontalAlignment(JLabel.CENTER);
        textLabel.setText("Connessione al server...");
        textLabel.setOpaque(true);

        textPanel.setLayout(new BorderLayout());
        textPanel.add(textLabel, BorderLayout.CENTER);

        // Aggiungi pulsante video
        videoToggleButton = new JButton("📹 Video OFF");
        videoToggleButton.setFont(new Font("Arial", Font.BOLD, 14));
        videoToggleButton.setEnabled(false);
        videoToggleButton.addActionListener(e -> toggleVideo());
        textPanel.add(videoToggleButton, BorderLayout.EAST);

        add(textPanel, BorderLayout.NORTH);

        // Pannello video
        videoPanel = new VideoPanel();
        videoPanel.setPreferredSize(new Dimension(boardWidth, 250));
        videoPanel.setVisible(false);
        add(videoPanel, BorderLayout.SOUTH);

        setupLobbyPanel();
        add(lobbyPanel, BorderLayout.CENTER);
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
                        sendMessage(GameMessage.move(String.valueOf(position)));
                    }
                });
            }
        }
    }

    void receiveMessages() {
        try {
            Object message;
            while ((message = in.readObject()) != null) {
                if (message instanceof GameMessage) {
                    System.out.println("Ricevuto: " + message);
                    processMessage((GameMessage<?>) message);
                }
            }
        } catch (IOException e) {
            if (!gameOver) {
                SwingUtilities.invokeLater(() -> {
                    textLabel.setText("Connessione persa!");
                });
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Errore di deserializzazione: " + e.getMessage());
        }
    }

    void processMessage(GameMessage<?> message) {
        SwingUtilities.invokeLater(() -> {
            String type = message.getType();

            switch (type) {
                case "SET_NICKNAME":
                    askNickname();
                    break;
                case "NICKNAME_SUCCESS":
                    myNickname = message.getPayloadAsString();
                    textLabel.setText("Benvenuto, " + myNickname + "!");
                    requestPlayerList();
                    break;
                case "NICKNAME_ERROR":
                    JOptionPane.showMessageDialog(this, "Nickname non valido o già in uso!");
                    askNickname();
                    break;
                case "LIST_PLAYERS":
                    String[] players = message.getPayloadAsStringArray();
                    updatePlayerList(players != null ? players : new String[]{});
                    break;
                case "CHALLENGE_REQUEST":
                    String challenger = message.getPayloadAsString();
                    if (challenger != null)
                        handleChallengeRequest(challenger);
                    break;
                case "CHALLENGE_SENT":
                    String opponent = message.getPayloadAsString();
                    textLabel.setText("Sfida inviata a " + opponent);
                    break;
                case "CHALLENGE_ACCEPTED":
                    opponentNickname = message.getPayloadAsString();
                    textLabel.setText("Sfida accettata! Inizio partita...");
                    break;
                case "CHALLENGE_REJECTED":
                    String rejectNickname = message.getPayloadAsString();
                    JOptionPane.showMessageDialog(this, rejectNickname + " ha rifiutato la sfida");
                    textLabel.setText("Benvenuto, " + myNickname + "!");
                    break;
                case "CHALLENGE_ERROR":
                    String error = message.getPayloadAsString();
                    JOptionPane.showMessageDialog(this, error != null ? error : "Errore sfida");
                    break;
                case "GAME_START":
                    String[] gameStart = message.getPayloadAsStringArray();
                    if (gameStart != null && gameStart.length == 2)
                        startGame(gameStart[0], gameStart[1]);
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
                    String[] boardData = message.getPayloadAsStringArray();
                    if (boardData != null && boardData.length == 2) {
                        int position = Integer.parseInt(boardData[0]);
                        String player = boardData[1];
                        int row = position / 3;
                        int col = position % 3;
                        board[row][col].setText(player);
                    }
                    break;
                case "GAME_OVER":
                    String result = message.getPayloadAsString();
                    if (result != null)
                        handleGameOver(result);
                    break;
                case "OPPONENT_DISCONNECTED":
                    gameOver = true;
                    inGame = false;
                    textLabel.setText(opponentNickname + " si è disconnesso");
                    JOptionPane.showMessageDialog(this, "L'avversario si è disconnesso");
                    returnToLobby();
                    break;
                case "NOT_YOUR_TURN":
                    JOptionPane.showMessageDialog(this, "Non è il tuo turno!");
                    break;
                case "INVALID_MOVE":
                    JOptionPane.showMessageDialog(this, "Mossa non valida!");
                    break;
            }
        });
    }

    void askNickname() {
        String nickname = JOptionPane.showInputDialog(this,
                "Inserisci il tuo nickname:",
                "Scelta Nickname",
                JOptionPane.QUESTION_MESSAGE);

        if (nickname == null || nickname.trim().isEmpty())
            nickname = "Player" + (int)(Math.random() * 1000);

        sendMessage(GameMessage.nickname(nickname));
    }

    void requestPlayerList() {
        sendMessage(GameMessage.listPlayers());
    }

    void updatePlayerList(String[] players) {
        playerListModel.clear();
        if (players != null) {
            for (String player : players) {
                playerListModel.addElement(player);
            }
        }
    }

    void sendMessage(GameMessage<?> message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            System.err.println("Errore nell'invio del messaggio: " + e.getMessage());
        }
    }

    void challengeSelectedPlayer() {
        String selectedPlayer = playerList.getSelectedValue();
        if (selectedPlayer == null) {
            JOptionPane.showMessageDialog(this, "Seleziona un giocatore da sfidare!");
            return;
        }
        sendMessage(GameMessage.challenge(selectedPlayer));
    }

    void handleChallengeRequest(String challenger) {
        int response = JOptionPane.showConfirmDialog(this,
                challenger + " ti ha sfidato! Accetti?",
                "Richiesta di Sfida",
                JOptionPane.YES_NO_OPTION);

        if (response == JOptionPane.YES_OPTION) {
            sendMessage(GameMessage.challengeAccept(challenger));
            opponentNickname = challenger;
        } else {
            sendMessage(GameMessage.challengeReject(challenger));
        }
    }

    void startGame(String symbol, String opponent) {
        myPlayer = symbol;
        opponentNickname = opponent;
        inGame = true;
        gameOver = false;
        myTurn = symbol.equals("X");

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                board[r][c].setText("");
                board[r][c].setBackground(Color.darkGray);
                board[r][c].setForeground(Color.white);
                board[r][c].setEnabled(true);
            }
        }

        lobbyPanel.setVisible(false);
        remove(lobbyPanel);
        add(boardPanel, BorderLayout.CENTER);
        boardPanel.setVisible(true);
        revalidate();

        textLabel.setText("Partita vs " + opponentNickname + " - Sei " + myPlayer);

        // Abilita il pulsante video
        videoToggleButton.setEnabled(true);

        // Chiedi se attivare il video
        int videoResponse = JOptionPane.showConfirmDialog(this,
                "Vuoi attivare la video chat con " + opponentNickname + "?",
                "Video Chat",
                JOptionPane.YES_NO_OPTION);

        if (videoResponse == JOptionPane.YES_OPTION) {
            toggleVideo();
        }
    }

    void toggleVideo() {
        if (videoPanel.isStreaming()) {
            // Ferma il video
            videoPanel.stopStreaming();
            videoPanel.setVisible(false);
            videoToggleButton.setText("📹 Video OFF");
            setSize(boardWidth, boardHeight);
        } else {
            // Avvia il video
            videoPanel.setVisible(true);
            setSize(boardWidth, boardHeight + 250);
            videoToggleButton.setText("📹 Video ON");

            boolean success = videoPanel.startStreaming(myNickname, opponentNickname, serverAddress);
            if (!success) {
                videoPanel.setVisible(false);
                videoToggleButton.setText("📹 Video OFF");
                setSize(boardWidth, boardHeight);
            }
        }
        revalidate();
        repaint();
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
        // Ferma il video se attivo
        if (videoPanel.isStreaming()) {
            videoPanel.stopStreaming();
        }
        videoPanel.setVisible(false);
        videoToggleButton.setEnabled(false);
        videoToggleButton.setText("📹 Video OFF");

        boardPanel.setVisible(false);
        remove(boardPanel);
        lobbyPanel.setVisible(true);
        add(lobbyPanel, BorderLayout.CENTER);

        setSize(boardWidth, boardHeight);
        revalidate();
        repaint();

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
            int r1 = win[0] / 3;
            int c1 = win[0] % 3;
            int r2 = win[1] / 3;
            int c2 = win[1] % 3;
            int r3 = win[2] / 3;
            int c3 = win[2] % 3;

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
        if (serverAddress == null || serverAddress.trim().isEmpty())
            serverAddress = "localhost";

        new TicTacToeClientWithWebcam(serverAddress);
    }
}