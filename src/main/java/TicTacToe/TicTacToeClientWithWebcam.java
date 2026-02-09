package TicTacToe;

import java.awt.*;
import javax.swing.*;
import java.io.*;
import java.net.*;

public class TicTacToeClientWithWebcam extends JFrame {
    int boardWidth = 600; //Larghezza finestra
    int boardHeight = 700; //Altezza finestra
    JLabel textLabel = new JLabel(); //Etichetta di stato
    JPanel textPanel = new JPanel(); //Pannello contenitore etichetta
    JPanel boardPanel = new JPanel(); //Pannello griglia di gioco
    JPanel lobbyPanel = new JPanel(); //Pannello lobby
    JList<String> playerList; //Lista giocatori connessi
    DefaultListModel<String> playerListModel; //Modello dati lista giocatori
    JButton challengeButton; //Bottone sfida
    JButton refreshButton; //Bottone aggiorna lista
    JButton[][] board = new JButton[3][3]; //Griglia pulsanti 3x3

    //Pannello video
    VideoPanel videoPanel; //Pannello per visualizzazione video
    JButton videoToggleButton; //Bottone attiva/disattiva video

    String myPlayer; //Simbolo giocatore
    String myNickname; //Nickname scelto
    String opponentNickname; //Nickname avversario
    boolean gameOver = false; //Flag fine partita
    boolean myTurn = false; //Flag turno corrente
    boolean inGame = false; //Flag in partita

    private Socket socket; //Socket verso server
    private ObjectOutputStream out; //Stream uscita oggetti
    private ObjectInputStream in; //Stream ingresso oggetti
    private String serverAddress; //Indirizzo server

    //Costruttore con indirizzo server
    TicTacToeClientWithWebcam(String serverAddress) {
        this.serverAddress = serverAddress; //Salva indirizzo server
        try {
            socket = new Socket(serverAddress, 12345); //Apre socket sulla porta 12345
            out = new ObjectOutputStream(socket.getOutputStream()); //Crea stream uscita
            out.flush();
            in = new ObjectInputStream(socket.getInputStream()); //Crea stream ingresso

            setupGUI(); //Costruisce interfaccia
            new Thread(this::receiveMessages).start(); //Thread per ricezione messaggi

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, //Mostra errore
                    "Impossibile connettersi al server: " + e.getMessage(),
                    "Errore di Connessione",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1); //Termina applicazione
        }
    }

    void setupGUI() { //Imposta GUI principale
        setTitle("Tic-Tac-Toe Multiplayer con Video"); //Titolo finestra
        setVisible(true); //Rende visibile la finestra
        setSize(boardWidth, boardHeight); //Dimensioni finestra
        setLocationRelativeTo(null); //Centra sullo schermo
        setResizable(false); //Disabilita ridimensionamento
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); //Si chiude su exit
        setLayout(new BorderLayout()); //Layout principale

        textLabel.setBackground(Color.darkGray); //Colore sfondo etichetta
        textLabel.setForeground(Color.white); //Colore testo etichetta
        textLabel.setFont(new Font("Arial", Font.BOLD, 30)); //Font etichetta
        textLabel.setHorizontalAlignment(JLabel.CENTER); //Allineamento centrato
        textLabel.setText("Connessione al server..."); //Testo iniziale
        textLabel.setOpaque(true); //Rende visibile lo sfondo

        textPanel.setLayout(new BorderLayout()); //Layout pannello etichetta
        textPanel.add(textLabel, BorderLayout.CENTER); //Aggiunge etichetta al pannello

        //Aggiungi pulsante video
        videoToggleButton = new JButton("📹 Video OFF"); //Crea bottone video
        videoToggleButton.setFont(new Font("Arial", Font.BOLD, 14)); //Font bottone video
        videoToggleButton.setEnabled(false); //Disabilita inizialmente
        videoToggleButton.addActionListener(e -> toggleVideo()); //Listener toggle video
        textPanel.add(videoToggleButton, BorderLayout.EAST); //Posiziona bottone a destra

        add(textPanel, BorderLayout.NORTH); //Posiziona pannello in alto

        //Pannello video
        videoPanel = new VideoPanel(); //Crea pannello video
        videoPanel.setPreferredSize(new Dimension(boardWidth, 250)); //Dimensioni pannello video
        videoPanel.setVisible(false); //Nasconde inizialmente
        add(videoPanel, BorderLayout.SOUTH); //Posiziona pannello video in basso

        setupLobbyPanel(); //Costruisce pannello lobby
        add(lobbyPanel, BorderLayout.CENTER); //Mostra lobby al centro
        setupBoardPanel(); //Costruisce pannello di gioco
        boardPanel.setVisible(false); //Nasconde griglia finché non si gioca
    }

    //Configura pannello lobby
    void setupLobbyPanel() {
        lobbyPanel.setLayout(new BorderLayout()); //Layout a bordi
        lobbyPanel.setBackground(Color.darkGray); //Sfondo scuro

        JLabel lobbyLabel = new JLabel("Giocatori Connessi"); //Titolo lobby
        lobbyLabel.setForeground(Color.white); //Colore testo titolo
        lobbyLabel.setFont(new Font("Arial", Font.BOLD, 20)); //Font titolo
        lobbyLabel.setHorizontalAlignment(JLabel.CENTER); //Allineamento titolo
        lobbyPanel.add(lobbyLabel, BorderLayout.NORTH); //Aggiunge titolo in alto

        playerListModel = new DefaultListModel<>(); //Crea modello lista
        playerList = new JList<>(playerListModel); //Crea lista con modello
        playerList.setBackground(Color.gray); //Sfondo lista
        playerList.setForeground(Color.white); //Testo lista
        playerList.setFont(new Font("Arial", Font.PLAIN, 16)); //Font lista
        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); //Selezione singola

        JScrollPane scrollPane = new JScrollPane(playerList); //Scroll per lista
        lobbyPanel.add(scrollPane, BorderLayout.CENTER); //Posiziona lista al centro

        JPanel buttonPanel = new JPanel(); //Pannello bottoni lobby
        buttonPanel.setBackground(Color.darkGray); //Sfondo bottoni

        challengeButton = new JButton("Sfida"); //Bottone sfida
        challengeButton.setFont(new Font("Arial", Font.BOLD, 16)); //Font bottone sfida
        challengeButton.addActionListener(e -> challengeSelectedPlayer()); //Listener sfida

        refreshButton = new JButton("Aggiorna"); //Bottone aggiorna
        refreshButton.setFont(new Font("Arial", Font.BOLD, 16)); //Font bottone aggiorna
        refreshButton.addActionListener(e -> requestPlayerList()); //Listener aggiorna lista

        buttonPanel.add(challengeButton); //Aggiunge bottone sfida
        buttonPanel.add(refreshButton); //Aggiunge bottone aggiorna
        lobbyPanel.add(buttonPanel, BorderLayout.SOUTH); //Posiziona bottoni in basso
    }

    //Configura pannello griglia di gioco
    void setupBoardPanel() {
        boardPanel.setLayout(new GridLayout(3, 3)); //Griglia 3x3
        boardPanel.setBackground(Color.darkGray); //Sfondo scuro

        for (int r = 0; r < 3; r++) { //Itera righe
            for (int c = 0; c < 3; c++) { //Itera colonne
                JButton tile = new JButton(); //Crea bottone cella
                board[r][c] = tile; //Salva in matrice
                boardPanel.add(tile); //Aggiunge al pannello

                tile.setBackground(Color.darkGray); //Sfondo cella
                tile.setForeground(Color.white); //Colore testo cella
                tile.setFont(new Font("Arial", Font.BOLD, 120)); //Font grande
                tile.setFocusable(false); //Evita focus tastiera

                int row = r; //Riga
                int col = c; //Colonna
                tile.addActionListener(e -> { //Listener click cella
                    if (gameOver || !myTurn || !inGame) return; //Ignora se non si può giocare
                    JButton clickedTile = (JButton) e.getSource(); //Cella cliccata
                    if (clickedTile.getText().equals("")) { //Se vuota
                        int position = row * 3 + col; //Calcola indice 0-8
                        sendMessage(GameMessage.move(String.valueOf(position))); //Invia mossa al server
                    }
                });
            }
        }
    }

    //Loop di ricezione messaggi dal server
    void receiveMessages() {
        try {
            Object message; //Buffer messaggio
            while ((message = in.readObject()) != null) { //Legge finché esistono messaggi
                if (message instanceof GameMessage) { //Se è di tipo messaggio
                    System.out.println("Ricevuto: " + message);
                    processMessage((GameMessage<?>) message); //Processa il messaggio
                }
            }
        } catch (IOException e) {
            if (!gameOver) { //Se la partita non è finita
                SwingUtilities.invokeLater(() -> { //Aggiorna UI nel thread separato
                    textLabel.setText("Connessione persa!"); //Mostra errore
                });
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Errore di deserializzazione: " + e.getMessage());
        }
    }

    //Gestisce i messaggi ricevuti
    void processMessage(GameMessage<?> message) {
        SwingUtilities.invokeLater(() -> { //Esegue su thread dell'UI
            String type = message.getType(); //Tipo messaggio

            switch (type) {
                case "SET_NICKNAME": //Richiesta nickname
                    askNickname(); //Chiede nickname
                    break;
                case "NICKNAME_SUCCESS": //Nickname accettato
                    myNickname = message.getPayloadAsString(); //Salva nickname
                    textLabel.setText("Benvenuto, " + myNickname + "!"); //Aggiorna testo
                    requestPlayerList(); //Richiede lista giocatori
                    break;
                case "NICKNAME_ERROR": //Nickname non valido
                    JOptionPane.showMessageDialog(this, "Nickname non valido o già in uso!"); //Mostra errore
                    askNickname(); //Richiede di nuovo
                    break;
                case "LIST_PLAYERS": //Lista giocatori ricevuta
                    String[] players = message.getPayloadAsStringArray(); //Estrae array
                    updatePlayerList(players != null ? players : new String[]{}); //Aggiorna lista
                    break;
                case "CHALLENGE_REQUEST": //Ricevuta sfida
                    String challenger = message.getPayloadAsString(); //Estrae sfidante
                    if (challenger != null) //Se valido
                        handleChallengeRequest(challenger); //Gestisce richiesta
                    break;
                case "CHALLENGE_SENT": //Conferma invio sfida
                    String opponent = message.getPayloadAsString(); //Estrae avversario
                    textLabel.setText("Sfida inviata a " + opponent); //Aggiorna stato
                    break;
                case "CHALLENGE_ACCEPTED": //Sfida accettata
                    opponentNickname = message.getPayloadAsString(); //Salva avversario
                    textLabel.setText("Sfida accettata! Inizio partita..."); //Aggiorna stato
                    break;
                case "CHALLENGE_REJECTED": //Sfida rifiutata
                    String rejectNickname = message.getPayloadAsString(); //Estrae chi ha rifiutato
                    JOptionPane.showMessageDialog(this, rejectNickname + " ha rifiutato la sfida"); //Mostra notifica
                    textLabel.setText("Benvenuto, " + myNickname + "!"); //Ripristina stato
                    break;
                case "CHALLENGE_ERROR": //Errore sfida
                    String error = message.getPayloadAsString(); //Estrae errore
                    JOptionPane.showMessageDialog(this, error != null ? error : "Errore sfida"); //Mostra errore
                    break;
                case "GAME_START": //Inizio partita
                    String[] gameStart = message.getPayloadAsStringArray(); //Estrae dati start
                    if (gameStart != null && gameStart.length == 2) //Se validi
                        startGame(gameStart[0], gameStart[1]); //Avvia partita, passa simbolo e avversario
                    break;
                case "YOUR_TURN": //Tuo turno
                    myTurn = true; //Imposta turno
                    textLabel.setText("Il tuo turno (" + myPlayer + ")"); //Aggiorna stato
                    break;
                case "OPPONENT_TURN": //Turno avversario
                    myTurn = false; //Disattiva turno
                    textLabel.setText("Turno di " + opponentNickname + "..."); //Aggiorna stato
                    break;
                case "BOARD": //Aggiornamento board
                    String[] boardData = message.getPayloadAsStringArray(); //Dati mossa
                    if (boardData != null && boardData.length == 2) { //Se validi
                        int position = Integer.parseInt(boardData[0]); //Posizione 0-8
                        String player = boardData[1]; //Simbolo
                        int row = position / 3; //Calcola riga
                        int col = position % 3; //Calcola colonna
                        board[row][col].setText(player); //Aggiorna cella
                    }
                    break;
                case "GAME_OVER": //Fine partita
                    String result = message.getPayloadAsString(); //Risultato
                    if (result != null) //Se valido
                        handleGameOver(result); //Gestisce esito
                    break;
                case "OPPONENT_DISCONNECTED": //Avversario disconnesso
                    gameOver = true; //Segna fine
                    inGame = false; //Non in partita
                    textLabel.setText(opponentNickname + " si è disconnesso"); //Aggiorna stato
                    JOptionPane.showMessageDialog(this, "L'avversario si è disconnesso"); //Notifica utente
                    returnToLobby(); //Torna in lobby
                    break;
                case "NOT_YOUR_TURN": //Mossa fuori turno
                    JOptionPane.showMessageDialog(this, "Non è il tuo turno!"); //Avvisa
                    break;
                case "INVALID_MOVE": //Mossa non valida
                    JOptionPane.showMessageDialog(this, "Mossa non valida!"); //Avvisa
                    break;
            }
        });
    }

    //Chiede nickname all'utente
    void askNickname() {
        String nickname = JOptionPane.showInputDialog(this, //Testo per input
                "Inserisci il tuo nickname:",
                "Scelta Nickname",
                JOptionPane.QUESTION_MESSAGE);

        if (nickname == null || nickname.trim().isEmpty()) //Se vuoto o null
            nickname = "Player" + (int)(Math.random() * 1000); //Genera default

        sendMessage(GameMessage.nickname(nickname)); //Invia al server
    }

    //Richiede lista giocatori
    void requestPlayerList() {
        sendMessage(GameMessage.listPlayers()); //Invia comando lista
    }

    //Aggiorna UI lista
    void updatePlayerList(String[] players) {
        playerListModel.clear(); //Svuota modello
        if (players != null) { //Se sono presenti dei dati
            for (String player : players) { //Per ogni giocatore
                playerListModel.addElement(player); //Lo aggiunge alla lista
            }
        }
    }

    //Invia un messaggio al server
    void sendMessage(GameMessage<?> message) {
        try {
            out.writeObject(message); //Scrive oggetto
            out.flush(); //Flush immediato
        } catch (IOException e) {
            System.err.println("Errore nell'invio del messaggio: " + e.getMessage());
        }
    }

    //Invia sfida al giocatore selezionato
    void challengeSelectedPlayer() {
        String selectedPlayer = playerList.getSelectedValue(); //Recupera selezione giocatore
        if (selectedPlayer == null) { //Se nessuno selezionato
            JOptionPane.showMessageDialog(this, "Seleziona un giocatore da sfidare!");
            return;
        }
        sendMessage(GameMessage.challenge(selectedPlayer)); //Invia sfida
    }

    //Gestisce sfida ricevuta
    void handleChallengeRequest(String challenger) {
        int response = JOptionPane.showConfirmDialog(this, //Testo conferma
                challenger + " ti ha sfidato! Accetti?",
                "Richiesta di Sfida",
                JOptionPane.YES_NO_OPTION);

        if (response == JOptionPane.YES_OPTION) { //Se accetta
            sendMessage(GameMessage.challengeAccept(challenger)); //Invia accettazione
            opponentNickname = challenger; //Salva avversario
        } else { //Rifiuta
            sendMessage(GameMessage.challengeReject(challenger)); //Invia rifiuto
        }
    }

    //Avvia la partita
    void startGame(String symbol, String opponent) {
        myPlayer = symbol; //Assegna simbolo
        opponentNickname = opponent; //Assegna avversario
        inGame = true; //Segna in partita
        gameOver = false; //Reset fine partita
        myTurn = symbol.equals("X"); //X inizia

        for (int r = 0; r < 3; r++) { //Per ogni riga
            for (int c = 0; c < 3; c++) { //E ogni colonna
                board[r][c].setText(""); //Pulisce testo
                board[r][c].setBackground(Color.darkGray); //Sfondo default
                board[r][c].setForeground(Color.white); //Testo default
                board[r][c].setEnabled(true); //Abilita pulsante
            }
        }

        lobbyPanel.setVisible(false); //Nasconde lobby
        remove(lobbyPanel); //Rimuove lobby dal frame
        add(boardPanel, BorderLayout.CENTER); //Aggiunge griglia
        boardPanel.setVisible(true); //Mostra griglia
        revalidate(); //Aggiorna layout

        textLabel.setText("Partita vs " + opponentNickname + " - Sei " + myPlayer); //Aggiorna stato

        //Abilita il pulsante video
        videoToggleButton.setEnabled(true); //Attiva bottone video

        //Chiedi se attivare il video
        int videoResponse = JOptionPane.showConfirmDialog(this, //Testo conferma video
                "Vuoi attivare la video chat con " + opponentNickname + "?",
                "Video Chat",
                JOptionPane.YES_NO_OPTION);

        if (videoResponse == JOptionPane.YES_OPTION) { //Se accetta
            toggleVideo(); //Attiva video
        }
    }

    //Attiva o disattiva il video
    void toggleVideo() {
        if (videoPanel.isStreaming()) { //Se il video è attivo
            //Ferma il video
            videoPanel.stopStreaming(); //Stoppa streaming
            videoPanel.setVisible(false); //Nasconde pannello video
            videoToggleButton.setText("📹 Video OFF"); //Aggiorna testo bottone
            setSize(boardWidth, boardHeight); //Ripristina dimensioni normali
        } else {
            //Avvia il video
            videoPanel.setVisible(true); //Mostra pannello video
            setSize(boardWidth, boardHeight + 250); //Espande finestra per video
            videoToggleButton.setText("📹 Video ON"); //Aggiorna testo bottone

            boolean success = videoPanel.startStreaming(myNickname, opponentNickname, serverAddress); //Avvia streaming
            if (!success) { //Se non riesce ad avviare
                videoPanel.setVisible(false); //Nasconde pannello
                videoToggleButton.setText("📹 Video OFF"); //Ripristina testo
                setSize(boardWidth, boardHeight); //Ripristina dimensioni
            }
        }
        revalidate(); //Aggiorna layout
        repaint(); //Ridisegna
    }

    //Gestisce fine partita
    void handleGameOver(String result) {
        gameOver = true; //Segna fine
        inGame = false; //Non in partita
        myTurn = false; //Disabilita turno

        if (result.equals("TIE")) { //Se si verifica un pareggio
            textLabel.setText("Pareggio!"); //Invia un messaggio
            setTieColors(); //Colora di pareggio
        } else {
            if (result.equals(myPlayer)) { //Se vittoria
                textLabel.setText("Hai vinto contro " + opponentNickname + "!"); //Messaggio vittoria
            } else { //Sconfitta
                textLabel.setText("Hai perso contro " + opponentNickname + "!"); //Messaggio sconfitta
            }
            highlightWinner(); //Evidenzia linea vincente
        }

        Timer timer = new Timer(3000, e -> returnToLobby()); //Timer ritorno alla lobby
        timer.setRepeats(false); //Esegue una volta
        timer.start(); //Avvia timer
    }

    //Ritorna alla lobby
    void returnToLobby() {
        //Ferma il video se attivo
        if (videoPanel.isStreaming()) { //Se video in corso
            videoPanel.stopStreaming(); //Stoppa streaming
        }
        videoPanel.setVisible(false); //Nasconde pannello video
        videoToggleButton.setEnabled(false); //Disabilita bottone video
        videoToggleButton.setText("📹 Video OFF"); //Ripristina testo bottone

        boardPanel.setVisible(false); //Nasconde griglia
        remove(boardPanel); //Rimuove griglia dal frame
        lobbyPanel.setVisible(true); //Mostra lobby
        add(lobbyPanel, BorderLayout.CENTER); //Posiziona lobby

        setSize(boardWidth, boardHeight); //Ripristina dimensioni normali
        revalidate(); //Aggiorna layout
        repaint(); //Ridisegna

        textLabel.setText("Benvenuto, " + myNickname + "!"); //Ripristina testo
        requestPlayerList(); //Aggiorna lista
    }

    //Evidenzia combinazione vincente
    void highlightWinner() {
        int[][] wins = { //Pattern vincenti
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
                {0, 4, 8}, {2, 4, 6}
        };

        for (int[] win : wins) { //Per ogni pattern
            int r1 = win[0] / 3; //Prima riga
            int c1 = win[0] % 3; //Prima colonna
            int r2 = win[1] / 3; //Seconda riga
            int c2 = win[1] % 3; //Seconda colonna
            int r3 = win[2] / 3; //Terza riga
            int c3 = win[2] % 3; //Terza colonna

            if (!board[r1][c1].getText().equals("") && //Se le celle non sono vuote
                    board[r1][c1].getText().equals(board[r2][c2].getText()) && //E la prima è uguale seconda
                    board[r2][c2].getText().equals(board[r3][c3].getText())) { //E la seconda uguale terza

                board[r1][c1].setForeground(Color.green); //Evidenzia prima
                board[r1][c1].setBackground(Color.gray); //Sfondo prima
                board[r2][c2].setForeground(Color.green); //Evidenzia seconda
                board[r2][c2].setBackground(Color.gray); //Sfondo seconda
                board[r3][c3].setForeground(Color.green); //Evidenzia terza
                board[r3][c3].setBackground(Color.gray); //Sfondo terza
                break;
            }
        }
    }

    //Colora la griglia in caso di pareggio
    void setTieColors() {
        for (int r = 0; r < 3; r++) { //Per ogni riga
            for (int c = 0; c < 3; c++) { //E colonna
                board[r][c].setForeground(Color.orange); //Colore testo pareggio
                board[r][c].setBackground(Color.gray); //Sfondo pareggio
            }
        }
    }

    //Entry point client
    public static void main(String[] args) {
        String serverAddress = JOptionPane.showInputDialog( //Chiede indirizzo server
                null,
                "Inserisci l'indirizzo IP del server:",
                "Connessione al Server",
                JOptionPane.QUESTION_MESSAGE
        );
        if (serverAddress == null || serverAddress.trim().isEmpty()) //Se vuoto o null
            serverAddress = "localhost"; //Default localhost

        new TicTacToeClientWithWebcam(serverAddress); //Avvia client
    }
}