# ❎🅾️ Tic-Tac-Toe Multiplayer

Un mini gioco **Tic-Tac-Toe multiplayer** implementato in Java con architettura **client-server**. I giocatori possono connettersi a un server centralizzato, sfidarsi a vicenda e giocare partite in tempo reale.

---

## 🏗️ Architettura del Progetto

L'applicazione utilizza un'architettura **Client-Server**:

```
┌─────────────────────────────────────────────────────┐
│                SERVER (TicTacToeServer)             │
│  • Gestisce connessioni multiple                    │
│  • Mantiene lista giocatori connessi                │
│  • Arbitro partite (Game)                           │
│  • Gestisce sfide tra giocatori                     │
└─────────────────────────────────────────────────────┘
          ↑                    ↑                    ↑
    Socket TCP            Socket TCP           Socket TCP
          ↓                    ↓                    ↓
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│     CLIENT 1     │ │     CLIENT 2     │ │     CLIENT N     │
│(TicTacToeClient) │ │(TicTacToeClient) │ │(TicTacToeClient) │
│  + GUI Swing     │ │  + GUI Swing     │ │  + GUI Swing     │
└──────────────────┘ └──────────────────┘ └──────────────────┘
```

---

## 🔧 Componenti Principali

### 1. **TicTacToeServer.java**
Il server centrale che gestisce tutte le connessioni.

**Compiti:**
- Ascolta su porta 12345
- Accetta nuove connessioni client
- Crea un thread `Player` per ogni client connesso
- Mantiene tre strutture dati principali:
  - `connectedClients`: mappa nickname → Player
  - `activeGames`: mappa ID-partita → Game
  - `pendingChallenges`: mappa sfidato → sfidante

---

### 2. **Player.java**
Rappresenta un giocatore connesso al server (su un thread dedicato).

**Compiti:**
- Gestisce la connessione del singolo client
- Legge messaggi dal client tramite stream serializzati
- Invia messaggi al client
- Elabora comandi ricevuti (NICKNAME, LIST_PLAYERS, CHALLENGE, MOVE, etc.)
- Gestisce l'accettazione/rifiuto di sfide
- Notifica il server quando un giocatore si disconnette

**Ciclo di vita:**
1. Client si connette → Server crea istanza `Player`
2. `receiveMessages()` richiede il nickname
3. Nickname validato e aggiunto a `connectedClients`
4. Loop infinito per ricezione messaggi finché il giocatore rimane connesso

---

### 3. **Game.java**
Rappresenta una singola partita di Tic-Tac-Toe (su un thread dedicato).

**Responsabilità:**
- Gestisce lo stato della partita (tabellone, turno, vincitore)
- Valida le mosse dei giocatori
- Controlla condizioni di vittoria e pareggio
- Sincronizza accesso al tabellone (metodo `handleMove` è sincronizzato)
- Notifica entrambi i giocatori dello stato

---

### 4. **GameMessage.java**
Classe generica serializzabile per la comunicazione client-server.

**Tipi di messaggio principali:**
- `SET_NICKNAME`: Server richiede il nickname al client
- `NICKNAME_SUCCESS/ERROR`: Risposta validazione nickname
- `LIST_PLAYERS`: Server invia lista giocatori disponibili
- `CHALLENGE`: Client sfida un giocatore
- `CHALLENGE_REQUEST`: Server comunica sfida ricevuta
- `GAME_START`: Inizio partita
- `BOARD`: Aggiornamento tabellone
- `YOUR_TURN/OPPONENT_TURN`: Notifica turno
- `GAME_OVER`: Fine partita (winner o TIE)
- `MOVE`: Client invia mossa

---

### 5. **TicTacToeClient.java**
Client con GUI Swing per l'interfaccia utente.

**Componenti GUI:**
- **Lobby Panel**: Lista giocatori connessi con bottoni "Sfida" e "Aggiorna"
- **Board Panel**: Griglia 3x3 di pulsanti per giocare
- **Text Label**: Mostra lo stato corrente

**Responsabilità:**
- Connessione al server su porta 12345
- Invio nickname per registrazione
- Ricezione della lista giocatori aggiornata
- Sfida di altri giocatori
- Ricezione e visualizzazione dello stato della partita
- Gestione dei click del giocatore sulla griglia

---

## 🔄 Flusso dell'Applicazione

### **Fase 1: Avvio e Connessione**

```
1. Server avviato
2. Client 1 si connette → TicTacToeServer accetta e crea Thread(Player 1)
3. Player 1 invia SET_NICKNAME
4. Client 1 riceve SET_NICKNAME e chiede all'utente un nickname
5. Client 1 invia NICKNAME con il valore
6. Player 1 valido:
   - Non vuoto
   - Non già in uso
7. Se valido:
   - Aggiunge a connectedClients[nickname] = Player 1
   - Invia NICKNAME_SUCCESS
   - Invia lista aggiornata a TUTTI i client
   Se non valido:
   - Invia NICKNAME_ERROR
   - Chiude connessione
```

---

### **Fase 2: Lobby e Lista Giocatori**

```
1. Client 1 vede la GUI Lobby con lista vuota
2. Client 2 si connette e completa registrazione
3. Server invia automaticamente lista aggiornata a Client 1 e Client 2
4. Entrambi vedono nella lista l'altro giocatore
5. Client 1 clicca "Aggiorna" per richiedere lista manualmente
   - Invia LIST_PLAYERS
   - Riceve lista aggiornata
```

---

### **Fase 3: Sfida tra Giocatori**

```
1. Client 1 seleziona "Player2" dalla lista e clicca "Sfida"
2. Client 1 invia: CHALLENGE con payload "Player2"
3. Player 1 processa il messaggio:
   - Cerca Player2 in connectedClients
   - Aggiunge a pendingChallenges["Player2"] = "Player1"
   - Invia al Player 2 → CHALLENGE_REQUEST con payload "Player1"
4. Client 2 riceve CHALLENGE_REQUEST
   - Mostra dialogo "Player1 ti sfida!"
   - Opzioni: Accetta o Rifiuta
5a. Se ACCETTA:
   - Client 2 invia: CHALLENGE_ACCEPT con payload "Player1"
   - Player 2 processa:
     - Crea nuovo Game(player1, player2, gameId)
     - Aggiunge a activeGames[gameId] = game
     - Rimuove da pendingChallenges
     - Avvia thread game
   - Game.run() esegue:
     - Invia GAME_START a player1 (sei X, avversario è Player2)
     - Invia GAME_START a player2 (sei O, avversario è Player1)
     - Invia YOUR_TURN a player1
     - Invia OPPONENT_TURN a player2
   - Entrambi i client visualizzano la griglia di gioco
5b. Se RIFIUTA:
   - Client 2 invia: CHALLENGE_REJECT con payload "Player1"
   - Player 2 processa:
     - Rimuove da pendingChallenges
     - Invia al player1 messaggio di rifiuto
     - Lobby rimane invariato
```

---

### **Fase 4: Gioco - Esecuzione Mosse**

```
1. È il turno di Player1 (simbolo X)
2. Player1 clicca sulla cella di posizione 4 (centro)
3. Client 1 invia: MOVE con payload "4"
4. Player 1 chiama game.handleMove(player1, "4")
5. Game.handleMove() sincronizzato:
   - Verifica gameOver → false, continua
   - Verifica sia il turno di X → sì, continua
   - Verifica posizione valida (0-8, non occupata) → sì
   - Assegna board[4] = "X"
   - Invia BOARD a entrambi i giocatori con [posizione="4", simbolo="X"]
6. Verifica vincitore:
   - Controlla 8 combinazioni vincenti
   - Se nessun vincitore → continua
   - Se tabellone pieno → PAREGGIO (TIE)
7. Se partita non finita:
   - Cambia turno: currentPlayer = "O"
   - Invia YOUR_TURN a player2
   - Invia OPPONENT_TURN a player1
8. Client 1 visualizza la mossa sulla griglia
9. Client 2 visualizza la mossa e sa che è il suo turno
10. Player2 clicca su una cella e ripete da step 2 con O
```

---

### **Fase 5: Fine Partita**

```
1. Dopo una mossa, Game.handleMove() controlla:
   a) Se c'è VINCITORE:
      - gameOver = true
      - Invia GAME_OVER a entrambi con winner = "X" o "O"
      - Rimuove partita da activeGames
      - Thread game termina
      
   b) Se PAREGGIO:
      - gameOver = true
      - Invia GAME_OVER a entrambi con winner = "TIE"
      - Rimuove partita da activeGames
      - Thread game termina
      
   c) Se UN GIOCATORE DISCONNETTE:
      - Game.playerDisconnected() viene chiamato
      - gameOver = true
      - Invia OPPONENT_DISCONNECTED all'altro giocatore
      - Rimuove partita da activeGames
2. Client riceve GAME_OVER:
   - Mostra risultato (vittoria, sconfitta, pareggio)
   - Disabilita griglia di gioco
   - Mostra tasto "Torna alla Lobby"
3. Client ritorna alla lobby
   - Visualizza di nuovo lista giocatori
   - Può sfidare altri giocatori
```

---

## 🔍 Dettagli Tecnici

### **Thread e Concorrenza:**

1. **Main Thread (Server)**
   - Ascolta su ServerSocket
   - Accetta connessioni e crea thread Player

2. **Thread Player (uno per client)**
   - Legge messaggi dal client
   - Processa comandi
   - Invia messaggi di risposta
   - Termina quando il client si disconnette

3. **Thread Game (uno per partita attiva)**
   - Esegue la logica di gioco
   - Sincronizzato su `handleMove()` per evitare race conditions

### **Serializzazione:**

Tutti gli oggetti `GameMessage` sono `Serializable`, quindi possono essere trasmessi su socket tramite `ObjectOutputStream` e `ObjectInputStream`.

## 👥 Autori

**Gastaldello Davide** & **Dalla Santa Manuel** - Classe: **5AII**