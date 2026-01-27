package TicTacToe;

import java.io.*;
import java.net.Socket;
import java.util.Map;

//Classe Player che implementa Runnable per eseguire thread
public class Player implements Runnable {
    private final Socket socket; //Socket per la connessione al client
    private ObjectOutputStream out; //Stream di output per inviare oggetti serializzati
    private ObjectInputStream in; //Stream di input per ricevere oggetti serializzati
    private String nickname; //Variabile per memorizzare il nickname del giocatore

    //Costruttore che inizializza il Player con un socket
    public Player(Socket socket) {
        this.socket = socket;
    }

    //Metodo pubblico che restituisce il nickname del giocatore
    public String getNickname() { return nickname; }

    //Metodo run che esegue il thread del player
    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream()); //Inizializza lo stream di output per inviare oggetti al client
            out.flush(); //Svuota il buffer dello stream di output
            in = new ObjectInputStream(socket.getInputStream()); //Inizializza lo stream di input per ricevere oggetti dal client

            sendMessage(GameMessage.setNickname()); //Invia un messaggio al client per richiedere il nickname
            GameMessage<?> msg = (GameMessage<?>) in.readObject(); //Legge il messaggio dal client contenente il nickname (usa ? per indicare un qualsiasi tipo) 

            if (msg.getType().equals("NICKNAME")) { //Se il messaggio ricevuto è di tipo "NICKNAME"
                nickname = msg.getPayloadAsString(); //Estrae il nickname dal messaggio e lo memorizza

                //Se il nickname non è valido e già in uso
                if (nickname == null || nickname.isEmpty() || TicTacToeServer.connectedClients.containsKey(nickname)) {
                    sendMessage(GameMessage.nicknameError("Nickname non valido o già in uso")); //Invia un messaggio di errore al client
                    socket.close(); //Chiude il socket
                    return;
                }

                TicTacToeServer.connectedClients.put(nickname, this); //Aggiunge il nuovo player alla map dei client connessi associando il nickname al player
                sendMessage(GameMessage.nicknameSuccess(nickname)); //Invia un messaggio di successo al client con il nickname confermato
                System.out.println("Utente registrato: " + nickname);

                broadcastPlayerList(); //Invia la lista aggiornata dei giocatori a tutti i client

                Object message; //Dichiara una variabile per memorizzare il messaggio ricevuto
                while ((message = in.readObject()) != null) { //Ciclo infinito che legge i messaggi dal client finché non è null
                    if (message instanceof GameMessage) //Se il messaggio è un'istanza di GameMessage
                        processMessage((GameMessage<?>) message); //Elabora il messaggio ricevuto
                }
            }
        } catch (IOException e) {
            System.err.println("Errore con client " + nickname + ": " + e.getMessage()); 
        } catch (ClassNotFoundException e) { 
            System.err.println("Errore di deserializzazione: " + e.getMessage()); 
        } finally { 
            cleanup(); //Esegue la pulizia e la disconnessione del player
        }
    }

    //Metodo privato che elabora i messaggi ricevuti dal client
    private void processMessage(GameMessage<?> message) { 
        String command = message.getType(); //Estrae il tipo di comando dal messaggio

        //Switch per gestire i diversi tipi di comando
        switch (command) { 
            case "LIST_PLAYERS": //Client richiede la lista dei giocatori disponibili
                sendPlayerList(); //Invia la lista dei giocatori al client
                break;
            case "CHALLENGE": //Client invia una sfida a un altro giocatore
                String opponent = message.getPayloadAsString(); //Estrae il nickname dell'avversario dal messaggio
                if (opponent != null) //Se l'avversario non è null
                    sendChallenge(opponent); //Invia la sfida all'avversario
                break;
            case "CHALLENGE_ACCEPT": //Client accetta una sfida ricevuta
                String challenger = message.getPayloadAsString(); //Estrae nickname del giocatore che ha sfidato dal messaggio
                if (challenger != null) //Se lo sfidante non è nullo
                    acceptChallenge(challenger); //Accetta la sfida dello sfidante
                break;
            case "CHALLENGE_REJECT": //Client rifiuta una sfida ricevuta
                String challenger2 = message.getPayloadAsString(); //Estrae il nickname del giocatore che ha sfidato dal messaggio
                if (challenger2 != null) //Se lo sfidante non è nullo
                    rejectChallenge(challenger2); //Rifiuta la sfida dello sfidante
                break;
            case "MOVE": //Caso in cui il client invia una mossa durante la partita
                String position = message.getPayloadAsString(); //Estrae la posizione della mossa dal messaggio
                if (position != null) //Se la posizione non è nulla
                    handleMove(position); //Gestisce la mossa del giocatore
                break; 
        }
    }

    //Metodo che invia la lista dei giocatori al client
    private void sendPlayerList() { 
        String[] players = TicTacToeServer.connectedClients.keySet().stream() //Ottiene l'insieme delle chiavi (nickname) dei client connessi
                .filter(p -> !p.equals(nickname)) //Filtra i giocatori escludendo il giocatore corrente
                .toArray(String[]::new); //Converte il flusso in un array di stringhe
        sendMessage(GameMessage.playersList(players)); //Invia il messaggio con la lista dei giocatori al client
    }

    //Metodo che invia la lista aggiornata dei giocatori a tutti i client connessi
    private void broadcastPlayerList() { 
        for (Player client : TicTacToeServer.connectedClients.values()) { //Per ogni valore (Player) della map dei client connessi
            client.sendPlayerList(); //Invia la lista dei giocatori a ciascun client
        }
    }

    //Metodo che gestisce l'invio di una sfida a un avversario
    private void sendChallenge(String opponent) { 
        Player opponentHandler = TicTacToeServer.connectedClients.get(opponent); //Ottiene l'oggetto Player associato al nickname dell'avversario
        if (opponentHandler == null) { //Se l'avversario non è disponibile
            sendMessage(GameMessage.challengeError("Giocatore non disponibile")); //Invia un messaggio di errore al client che ha inviato la sfida
            return; 
        }

        String gameId = nickname + "-" + opponent; //Crea un id univoco per la partita combinando i nickname
        if (TicTacToeServer.activeGames.containsKey(gameId) || TicTacToeServer.activeGames.containsKey(opponent + "-" + nickname)) { //Se una partita è già in corso tra i due giocatori
            sendMessage(GameMessage.challengeError("Partita già in corso")); //Invia un messaggio di errore al client
            return; 
        }

        TicTacToeServer.pendingChallenges.put(opponent, nickname); //Aggiunge la sfida in sospeso alla mappa
        opponentHandler.sendMessage(GameMessage.challengeRequest(nickname)); //Invia un messaggio di richiesta di sfida all'avversario
        sendMessage(GameMessage.challengeSent(opponent)); //Invia un messaggio di conferma al client che ha inviato la sfida
        System.out.println(nickname + " ha sfidato " + opponent);
    }

    //Metodo privato che gestisce l'accettazione di una sfida da parte del giocatore
    private void acceptChallenge(String challenger) {
        String pendingChallenger = TicTacToeServer.pendingChallenges.get(nickname); //Ottiene il nickname dello sfidante dalla mappa delle sfide in sospeso
        if (pendingChallenger == null || !pendingChallenger.equals(challenger)) { //Se non c'è una sfida pendente o se lo sfidante è diverso
            sendMessage(GameMessage.challengeError("Sfida non valida")); //Invia un messaggio di errore al client
            return; 
        }

        TicTacToeServer.pendingChallenges.remove(nickname); //Rimuove la sfida in sospeso dalla map
        Player challengerHandler = TicTacToeServer.connectedClients.get(challenger); //Ottiene l'oggetto Player associato al nickname dello sfidante

        if (challengerHandler == null) { //Se lo sfidante non è più disponibile
            sendMessage(GameMessage.challengeError("Sfidante non disponibile")); //Invia un messaggio di errore al client
            return; 
        }

        String gameId = challenger + "-" + nickname; //Crea un id univoco per la partita combinando i nickname
        Game game = new Game(challengerHandler, this, gameId); //Crea una nuova istanza di Game con lo sfidante e il giocatore corrente
        TicTacToeServer.activeGames.put(gameId, game); //Aggiunge la partita alla map delle partite attive

        challengerHandler.sendMessage(GameMessage.challengeAccepted(nickname)); //Invia un messaggio di accettazione al client dello sfidante
        sendMessage(GameMessage.challengeAccepted(challenger)); //Invia un messaggio di accettazione al client del giocatore corrente

        new Thread(game).start(); //Crea e avvia un nuovo thread per gestire la partita
        System.out.println("Partita avviata: " + gameId);
    }

    //Metodo privato che gestisce il rifiuto di una sfida da parte del giocatore
    private void rejectChallenge(String challenger) {
        String pendingChallenger = TicTacToeServer.pendingChallenges.get(nickname); //Ottiene il nickname dello sfidante dalla map delle sfide in sospeso
        if (pendingChallenger != null && pendingChallenger.equals(challenger)) { //Se esiste una sfida in sospeso e se è dello sfidante specificato
            TicTacToeServer.pendingChallenges.remove(nickname); //Rimuove la sfida in sospeso dalla map
            Player challengerHandler = TicTacToeServer.connectedClients.get(challenger); //Ottiene l'oggetto Player associato al nickname dello sfidante
            if (challengerHandler != null)  //Se lo sfidante è ancora disponibile
                challengerHandler.sendMessage(GameMessage.challengeRejected(nickname)); //Invia un messaggio di rifiuto della sfida al client dello sfidante

            System.out.println(nickname + " ha rifiutato la sfida di " + challenger);
        }
    }

    //Metodo che gestisce la mossa del giocatore durante la partita
    private void handleMove(String position) {
        for (Map.Entry<String, Game> entry : TicTacToeServer.activeGames.entrySet()) { //Per ogni partita attiva nella map
            Game game = entry.getValue(); //Ottiene l'oggetto Game dalla coppia chiave-valore
            if (game.hasPlayer(this)) { //Se il giocatore corrente è coinvolto nella partita
                game.handleMove(this, position); //Invia la mossa alla partita specificando il giocatore e la posizione
                break;
            }
        }
    }

    //Metodo che invia un messaggio al client del giocatore
    public void sendMessage(GameMessage<?> message) {
        try {
            out.writeObject(message); //Scrive l'oggetto messaggio nello stream di output
            out.flush(); //Svuota il buffer dello stream
        } catch (IOException e) {
            System.err.println("Errore nell'invio del messaggio: " + e.getMessage());
        }
    }

    //Metodo che gestisce la pulizia e la disconnessione del giocatore
    private void cleanup() {
        if (nickname != null) { //Se il nickname del giocatore non è null
            TicTacToeServer.connectedClients.remove(nickname); //Rimuove il giocatore dalla map dei client connessi
            TicTacToeServer.pendingChallenges.remove(nickname); //Rimuove il giocatore dalla map delle sfide in sospeso

            TicTacToeServer.activeGames.entrySet().removeIf(entry -> { //Per ogni partita attiva rimuove quella in cui il giocatore è coinvolto
                if (entry.getValue().hasPlayer(this)) { //Se il giocatore è coinvolto nella partita
                    entry.getValue().playerDisconnected(this); //Notifica al gioco che il giocatore si è disconnesso
                    return true; //Ritorna true per indicare che la partita deve essere rimossa
                }
                return false; //Ritorna false per indicare che la partita non deve essere rimossa
            });
            broadcastPlayerList(); //Invia la lista aggiornata dei giocatori a tutti i client rimasti connessi
            System.out.println("Utente disconnesso: " + nickname);
        }
        try {
            socket.close(); //Chiude il socket del client
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}