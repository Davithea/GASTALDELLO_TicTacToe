package TicTacToe; 

import java.io.Serializable;

//Classe generica che implementa Serializable per poter essere inviata in rete
public class GameMessage<T> implements Serializable {
    private final String type; //Tipo di messaggio (es: NICKNAME_SUCCESS, SET_NICKNAME, BOARD, ...)
    private final T payload; //Dato generico associato al messaggio

    //Costruttore che inizializza tipo e payload
    public GameMessage(String type, T payload) { 
        this.type = type; //Assegna il tipo del messaggio
        this.payload = payload; //Assegna il payload associato
    }

    //Costruttore che crea un messaggio senza payload
    public GameMessage(String type) { 
        this.type = type; //Assegna il tipo del messaggio
        this.payload = null; //Imposta il payload a null
    }

    public String getType() { return type; } //Getter per tipo del messaggio

    public String getPayloadAsString() { //Restituisce il payload come String se possibile
        if (payload == null) return null; //Se il payload è null restituisce null
        if (payload instanceof String)  //Se il payload è una stringa
            return (String) payload; //Restituisce la stringa con cast
    
        return payload.toString(); //Converte il payload in stringa usando toString
    }

    public String[] getPayloadAsStringArray() { //Restituisce il payload come array di stringhe se possibile
        if (payload == null) return null; //Se il payload è null restituisce null
        if (payload instanceof String[]) { //Se il payload è già un array di stringhe
            return (String[]) payload; //Restituisce il payload castato
        }
        if (payload instanceof String) { //Se il payload è una singola stringa
            return new String[]{(String) payload}; //Trasforma la stringa in un array di dimensione 1
        }
        return null; //Restituisce null se non è convertibile in String[]
    }

    @Override
    public String toString() { //Rappresentazione testuale dell'oggetto GameMessage
        return "GameMessage{" + //Inizio costruzione della stringa descrittiva
                "type='" + type + '\'' + //Aggiunge il campo type
                ", payload=" + payload + //Aggiunge il campo payload
                '}'; 
    }

    //Metodi statici factory

    public static GameMessage<Void> setNickname() { //Crea un messaggio per richiedere il set del nickname
        return new GameMessage<>("SET_NICKNAME"); //Restituisce un GameMessage senza payload con tipo SET_NICKNAME
    }

    public static GameMessage<Void> listPlayers() { //Crea un messaggio per richiedere la lista dei giocatori
        return new GameMessage<>("LIST_PLAYERS"); //Restituisce un GameMessage senza payload con tipo LIST_PLAYERS
    }

    public static GameMessage<Void> yourTurn() { //Crea un messaggio che segnala che è il tuo turno
        return new GameMessage<>("YOUR_TURN"); //Restituisce un GameMessage senza payload con tipo YOUR_TURN
    }

    public static GameMessage<Void> opponentTurn() { //Crea un messaggio che segnala il turno dell'avversario
        return new GameMessage<>("OPPONENT_TURN"); //Restituisce un GameMessage senza payload con tipo OPPONENT_TURN
    }

    public static GameMessage<Void> invalidMove() { //Crea un messaggio per mossa non valida
        return new GameMessage<>("INVALID_MOVE"); //Restituisce un GameMessage senza payload con tipo INVALID_MOVE
    }

    public static GameMessage<Void> opponentDisconnected() { //Crea un messaggio che segnala la disconnessione dell'avversario
        return new GameMessage<>("OPPONENT_DISCONNECTED"); //Restituisce un GameMessage senza payload con tipo OPPONENT_DISCONNECTED
    }

    public static GameMessage<String> nicknameSuccess(String nickname) { //Crea un messaggio di successo con il nickname scelto
        return new GameMessage<>("NICKNAME_SUCCESS", nickname); //Restituisce un GameMessage con payload nickname e tipo NICKNAME_SUCCESS
    }

    public static GameMessage<String> nicknameError(String error) { //Crea un messaggio di errore relativo al nickname
        return new GameMessage<>("NICKNAME_ERROR", error); //Restituisce un GameMessage con payload errore e tipo NICKNAME_ERROR
    }

    public static GameMessage<String> nickname(String nickname) { //Crea un messaggio contenente un nickname
        return new GameMessage<>("NICKNAME", nickname); //Restituisce un GameMessage con payload nickname e tipo NICKNAME
    }

    public static GameMessage<String> challenge(String opponent) { //Crea un messaggio di sfida verso un avversario
        return new GameMessage<>("CHALLENGE", opponent); //Restituisce un GameMessage con payload avversario e tipo CHALLENGE
    }

    public static GameMessage<String> challengeRequest(String challenger) { //Crea un messaggio che indica una richiesta di sfida ricevuta
        return new GameMessage<>("CHALLENGE_REQUEST", challenger); //Restituisce un GameMessage con payload sfidante e tipo CHALLENGE_REQUEST
    }

    public static GameMessage<String> challengeSent(String opponent) { //Crea un messaggio che conferma l'invio di una sfida
        return new GameMessage<>("CHALLENGE_SENT", opponent); //Restituisce un GameMessage con payload avversario e tipo CHALLENGE_SENT
    }

    public static GameMessage<String> challengeAccept(String challenger) { //Crea un messaggio che indica l'accettazione di una sfida
        return new GameMessage<>("CHALLENGE_ACCEPT", challenger); //Restituisce un GameMessage con payload sfidante e tipo CHALLENGE_ACCEPT
    }

    public static GameMessage<String> challengeReject(String challenger) { //Crea un messaggio che indica il rifiuto di una sfida
        return new GameMessage<>("CHALLENGE_REJECT", challenger); //Restituisce un GameMessage con payload sfidante e tipo CHALLENGE_REJECT
    }

    public static GameMessage<String> challengeAccepted(String opponent) { //Crea un messaggio che conferma che la sfida è stata accettata
        return new GameMessage<>("CHALLENGE_ACCEPTED", opponent); //Restituisce un GameMessage con payload avversario e tipo CHALLENGE_ACCEPTED
    }

    public static GameMessage<String> challengeRejected(String opponent) { //Crea un messaggio che conferma che la sfida è stata rifiutata
        return new GameMessage<>("CHALLENGE_REJECTED", opponent); //Restituisce un GameMessage con payload avversario e tipo CHALLENGE_REJECTED
    }

    public static GameMessage<String> challengeError(String error) { //Crea un messaggio che segnala un errore nelle sfide
        return new GameMessage<>("CHALLENGE_ERROR", error); //Restituisce un GameMessage con payload errore e tipo CHALLENGE_ERROR
    }

    public static GameMessage<String> gameOver(String result) { //Crea un messaggio che comunica l'esito della partita
        return new GameMessage<>("GAME_OVER", result); //Restituisce un GameMessage con payload risultato e tipo GAME_OVER
    }

    public static GameMessage<String> move(String position) { //Crea un messaggio di mossa con la posizione indicata come stringa
        return new GameMessage<>("MOVE", position); //Restituisce un GameMessage con payload posizione e tipo MOVE
    }

    public static GameMessage<String[]> playersList(String[] players) { //Crea un messaggio che contiene la lista dei giocatori
        return new GameMessage<>("LIST_PLAYERS", players); //Restituisce un GameMessage con payload array di giocatori e tipo LIST_PLAYERS
    }

    public static GameMessage<String[]> gameStart(String symbol, String opponent) { //Crea un messaggio di inizio partita con simbolo e avversario
        return new GameMessage<>("GAME_START", new String[]{symbol, opponent}); //Restituisce un GameMessage con payload [simbolo, avversario] e tipo GAME_START
    }

    public static GameMessage<String[]> board(String position, String player) { //Crea un messaggio che aggiorna il tabellone con posizione e giocatore
        return new GameMessage<>("BOARD", new String[]{position, player}); //Restituisce un GameMessage con payload [posizione, giocatore] e tipo BOARD
    }

    public static GameMessage<Integer> movePosition(int position) { //Crea un messaggio di mossa usando un intero per la posizione
        return new GameMessage<>("MOVE", position); //Restituisce un GameMessage con payload intero posizione e tipo MOVE
    }
}