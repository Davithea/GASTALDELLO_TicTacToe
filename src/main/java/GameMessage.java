import java.io.Serializable;

public class GameMessage<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type;        // Tipo di messaggio (es: NICKNAME_SUCCESS, SET_NICKNAME, BOARD, ecc.)
    private T payload;          // Dati associati al messaggio

    // Costruttore vuoto per deserializzazione
    public GameMessage() {
    }

    // Costruttore con tipo e payload
    public GameMessage(String type, T payload) {
        this.type = type;
        this.payload = payload;
    }

    // Costruttore solo con tipo (per messaggi senza payload)
    public GameMessage(String type) {
        this.type = type;
        this.payload = null;
    }

    // Getter per il tipo di messaggio
    public String getType() {
        return type;
    }

    // Setter per il tipo di messaggio
    public void setType(String type) {
        this.type = type;
    }

    // Getter per il payload
    public T getPayload() {
        return payload;
    }

    // Setter per il payload
    public void setPayload(T payload) {
        this.payload = payload;
    }

    // Controlla se il messaggio ha un payload
    public boolean hasPayload() {
        return payload != null;
    }

    // Converte il payload in String (se possibile)
    public String getPayloadAsString() {
        if (payload == null) return null;
        if (payload instanceof String) {
            return (String) payload;
        }
        return payload.toString();
    }

    // Converte il payload in String[] (se possibile)
    public String[] getPayloadAsStringArray() {
        if (payload == null) return null;
        if (payload instanceof String[]) {
            return (String[]) payload;
        }
        if (payload instanceof String) {
            return new String[]{(String) payload};
        }
        return null;
    }

    // Converte il payload in Integer (se possibile)
    public Integer getPayloadAsInteger() {
        if (payload == null) return null;
        if (payload instanceof Integer) {
            return (Integer) payload;
        }
        if (payload instanceof String) {
            try {
                return Integer.parseInt((String) payload);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "GameMessage{" +
                "type='" + type + '\'' +
                ", payload=" + payload +
                '}';
    }

    // ========== METODI FACTORY PER CREARE MESSAGGI COMUNI ==========

    // Messaggi senza payload
    public static GameMessage<Void> setNickname() {
        return new GameMessage<>("SET_NICKNAME");
    }

    public static GameMessage<Void> listPlayers() {
        return new GameMessage<>("LIST_PLAYERS");
    }

    public static GameMessage<Void> yourTurn() {
        return new GameMessage<>("YOUR_TURN");
    }

    public static GameMessage<Void> opponentTurn() {
        return new GameMessage<>("OPPONENT_TURN");
    }

    public static GameMessage<Void> invalidMove() {
        return new GameMessage<>("INVALID_MOVE");
    }

    public static GameMessage<Void> opponentDisconnected() {
        return new GameMessage<>("OPPONENT_DISCONNECTED");
    }

    // Messaggi con payload String
    public static GameMessage<String> nicknameSuccess(String nickname) {
        return new GameMessage<>("NICKNAME_SUCCESS", nickname);
    }

    public static GameMessage<String> nicknameError(String error) {
        return new GameMessage<>("NICKNAME_ERROR", error);
    }

    public static GameMessage<String> nickname(String nickname) {
        return new GameMessage<>("NICKNAME", nickname);
    }

    public static GameMessage<String> challenge(String opponent) {
        return new GameMessage<>("CHALLENGE", opponent);
    }

    public static GameMessage<String> challengeRequest(String challenger) {
        return new GameMessage<>("CHALLENGE_REQUEST", challenger);
    }

    public static GameMessage<String> challengeSent(String opponent) {
        return new GameMessage<>("CHALLENGE_SENT", opponent);
    }

    public static GameMessage<String> challengeAccept(String challenger) {
        return new GameMessage<>("CHALLENGE_ACCEPT", challenger);
    }

    public static GameMessage<String> challengeReject(String challenger) {
        return new GameMessage<>("CHALLENGE_REJECT", challenger);
    }

    public static GameMessage<String> challengeAccepted(String opponent) {
        return new GameMessage<>("CHALLENGE_ACCEPTED", opponent);
    }

    public static GameMessage<String> challengeRejected(String opponent) {
        return new GameMessage<>("CHALLENGE_REJECTED", opponent);
    }

    public static GameMessage<String> challengeError(String error) {
        return new GameMessage<>("CHALLENGE_ERROR", error);
    }

    public static GameMessage<String> gameOver(String result) {
        return new GameMessage<>("GAME_OVER", result);
    }

    public static GameMessage<String> move(String position) {
        return new GameMessage<>("MOVE", position);
    }

    // Messaggi con payload String[]
    public static GameMessage<String[]> playersList(String[] players) {
        return new GameMessage<>("LIST_PLAYERS", players);
    }

    public static GameMessage<String[]> gameStart(String symbol, String opponent) {
        return new GameMessage<>("GAME_START", new String[]{symbol, opponent});
    }

    public static GameMessage<String[]> board(String position, String player) {
        return new GameMessage<>("BOARD", new String[]{position, player});
    }

    // Messaggio generico con Integer
    public static GameMessage<Integer> movePosition(int position) {
        return new GameMessage<>("MOVE", position);
    }
}