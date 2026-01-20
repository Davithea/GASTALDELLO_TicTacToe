package TicTacToe; 

import java.util.Arrays; 

//Dichiarazione classe Game che implementa interfaccia Runnable per eseguire gioco in un thread
public class Game implements Runnable { 
    private Player player1; //Primo giocatore
    private Player player2; //Secondo giocatore
    private String gameId; //Id univoco della partita
    private String[] board = new String[9]; //Array con 9 celle che rappresenta il tabellone di gioco
    private String currentPlayer = "X"; //Variabile che indica il giocatore corrente (inizia con X)
    private boolean gameOver = false; //Variabile gameOver che indica se la partita è terminata

    //Costruttore della classe Game che riceve due giocatori e l'ID della partita
    public Game(Player p1, Player p2, String gameId) { 
        this.player1 = p1; //Assegnazione del primo giocatore alla variabile player1
        this.player2 = p2; //E anche del secondo
        this.gameId = gameId; //Assegnazione ID della partita alla variabile gameId
        Arrays.fill(board, ""); //Riempimento di tutte le celle del tabellone con stringhe vuote
    }

    //Metodo run che viene eseguito quando il thread della partita viene avviato
    @Override
    public void run() { 
        player1.sendMessage(GameMessage.gameStart("X", player2.getNickname())); //Invio messaggio al player1 per inizio partita, gioca con X e mostra il nickname dell'avversario
        player2.sendMessage(GameMessage.gameStart("O", player1.getNickname())); //Stessa cosa, ma per il player2 che gioca con O
        player1.sendMessage(GameMessage.yourTurn()); //Invio messaggio al player1 che è il suo turno di gioco
    }

    //Metodo sincronizzato che gestisce la mossa di un giocatore ricevendo il giocatore e la posizione
    public synchronized void handleMove(Player player, String position) { 
        if (gameOver) return; //Se la partita è terminata, esce dal metodo senza eseguire la mossa

        String playerSymbol = (player == player1) ? "X" : "O"; //Determina il simbolo del giocatore (X se è player1, altrimenti O)
        if (!playerSymbol.equals(currentPlayer)) { //Se il simbolo del giocatore non corrisponde al giocatore corrente
            player.sendMessage(new GameMessage<>("NOT_YOUR_TURN")); //Invia un messaggio al giocatore per informarlo che non è il suo turno
            return;
        }

        int pos = Integer.parseInt(position); //Converte la stringa position in un numero intero
        if (pos < 0 || pos >= 9 || !board[pos].equals("")) { //Se la posizione è fuori dai limiti (0-8) o se la cella è già occupata
            player.sendMessage(GameMessage.invalidMove()); //Invia un messaggio al giocatore per informarlo che la mossa non è valida
            return;
        }

        board[pos] = currentPlayer; //Assegna il simbolo del giocatore corrente alla cella selezionata del tabellone
        player1.sendMessage(new GameMessage<>("BOARD", new String[]{String.valueOf(pos), currentPlayer})); //Invia al player1 l'aggiornamento del tabellone con la posizione e il simbolo
        player2.sendMessage(new GameMessage<>("BOARD", new String[]{String.valueOf(pos), currentPlayer})); //Stessa cosa per il player2

        String winner = checkWinner(); //Verifica se c'è un vincitore
        if (winner != null) { //Se c'è un vincitore
            gameOver = true; //Imposta la partita come terminata
            player1.sendMessage(GameMessage.gameOver(winner)); //Invia al player1 il messaggio che la partita è finita con il vincitore
            player2.sendMessage(GameMessage.gameOver(winner)); //Anche al player2
            TicTacToeServer.activeGames.remove(gameId); //Rimuove la partita dalla lista delle partite attive nel server
            return;
        }

        if (isBoardFull()) { //Se il tabellone è pieno (tutte le celle sono occupate)
            gameOver = true; //Imposta la partita come terminata
            player1.sendMessage(GameMessage.gameOver("TIE")); //Invia al player1 il messaggio che la partita è finita in pareggio
            player2.sendMessage(GameMessage.gameOver("TIE")); //Anche al player2
            TicTacToeServer.activeGames.remove(gameId); //Rimuove la partita dalla lista delle partite attive nel server
            return;
        }

        currentPlayer = currentPlayer.equals("X") ? "O" : "X"; //Cambia il giocatore corrente (da X a O o viceversa)
        if (currentPlayer.equals("X")) { //Se il giocatore corrente è X
            player1.sendMessage(GameMessage.yourTurn()); //Notifica al player1 che è il suo turno
            player2.sendMessage(GameMessage.opponentTurn()); //Notifica al player2 che è il turno dell'avversario
        } else { //Altrimenti (il giocatore corrente è O)
            player2.sendMessage(GameMessage.yourTurn()); //Notifica al player2 che è il suo turno
            player1.sendMessage(GameMessage.opponentTurn()); //Notifica al player1 che è il turno dell'avversario
        }
    }

    //Metodo che verifica se un giocatore fa parte di questa partita
    public boolean hasPlayer(Player player) { 
        return player == player1 || player == player2; //Restituisce true se il giocatore è player1 o player2
    }

    //Metodo che gestisce la disconnessione di un giocatore
    public void playerDisconnected(Player player) { 
        if (!gameOver) { //Se la partita non è ancora terminata
            gameOver = true; //Imposta la partita come terminata
            Player other = (player == player1) ? player2 : player1; //Determina l'altro giocatore
            if (other != null) { //Se l'altro giocatore esiste
                other.sendMessage(GameMessage.opponentDisconnected()); //Notifica all'altro giocatore che l'avversario si è disconnesso
            }
        }
    }

    //Metodo che controlla se c'è un vincitore
    private String checkWinner() { 
        int[][] wins = { //Array bidimensionale che contiene tutte le combinazioni vincenti
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, //Combinazioni vincenti orizzontali
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, //Verticali
                {0, 4, 8}, {2, 4, 6} //E diagonali
        };

        for (int[] win : wins) { //Per combinazione vincente
            if (!board[win[0]].equals("") && //Se la prima cella della combinazione non è vuota e
                    board[win[0]].equals(board[win[1]]) && //La prima cella è uguale alla seconda e
                    board[win[1]].equals(board[win[2]])) { //La seconda cella è uguale alla terza
                return board[win[0]]; //Restituisce il simbolo del vincitore (X o O)
            }
        }
        return null; //Restituisce null se non c'è nessun vincitore
    }

    //Metodo privato che verifica se il tabellone è pieno
    private boolean isBoardFull() { 
        for (String cell : board) { //Per ogni cella del tabellone
            if (cell.equals("")) return false; //Se trova una cella vuota restituisce false
        }
        return true; //Restituisce true se tutte le celle sono occupate
    }
}