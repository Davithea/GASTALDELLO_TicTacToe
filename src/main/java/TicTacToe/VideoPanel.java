package TicTacToe;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.*;

public class VideoPanel extends JPanel {
    private JPanel localVideoPanel; //Pannello per video locale
    private JPanel remoteVideoPanel; //Pannello per video remoto
    private WebcamPanel webcamPanel; //Pannello webcam locale
    private JLabel remoteVideoLabel; //Label per video remoto
    private JLabel localStatusLabel; //Stato video locale
    private JLabel remoteStatusLabel; //Stato video remoto

    private Webcam webcam; //Webcam
    private volatile boolean streaming = false; //Flag streaming attivo
    private Socket videoSocket; //Socket verso server video
    private DataOutputStream videoOut; //Stream output video
    private DataInputStream videoIn; //Stream input video

    private String myNickname; //Nickname client
    private String opponentNickname; //Nickname avversario

    private ExecutorService streamExecutor; //Thread per streaming
    private ExecutorService receiveExecutor; //Thread per ricezione

    private static final int TARGET_WIDTH = 320; //Larghezza video
    private static final int TARGET_HEIGHT = 240; //Altezza video
    private static final int FPS = 15; //Frame per secondo
    private static final float JPEG_QUALITY = 0.7f; //Qualità JPEG

    //Costruttore pannello video
    public VideoPanel() {
        setLayout(new BorderLayout()); //Layout a bordi
        setBackground(Color.DARK_GRAY); //Sfondo scuro
        setupUI(); //Costruisce interfaccia
    }

    //Configura interfaccia utente
    private void setupUI() {
        JPanel videoContainer = new JPanel(new GridLayout(1, 2, 10, 0)); //Griglia 1x2
        videoContainer.setBackground(Color.DARK_GRAY); //Sfondo scuro

        //Pannello video locale
        localVideoPanel = new JPanel(new BorderLayout()); //Layout a bordi
        localVideoPanel.setBackground(Color.BLACK); //Sfondo nero
        localVideoPanel.setPreferredSize(new Dimension(TARGET_WIDTH, TARGET_HEIGHT)); //Dimensioni pannello

        localStatusLabel = new JLabel("Video Non Attivo", SwingConstants.CENTER); //Label stato
        localStatusLabel.setForeground(Color.WHITE); //Colore testo
        localStatusLabel.setFont(new Font("Arial", Font.BOLD, 14)); //Font label
        localVideoPanel.add(localStatusLabel, BorderLayout.CENTER); //Aggiunge label al centro

        JLabel localLabel = new JLabel("La tua webcam", SwingConstants.CENTER); //Label titolo
        localLabel.setForeground(Color.WHITE); //Colore testo
        localLabel.setBackground(new Color(76, 175, 80)); //Sfondo verde
        localLabel.setOpaque(true); //Rende visibile lo sfondo
        localLabel.setFont(new Font("Arial", Font.BOLD, 12)); //Font label
        localVideoPanel.add(localLabel, BorderLayout.SOUTH); //Aggiunge label in basso

        //Pannello video remoto
        remoteVideoPanel = new JPanel(new BorderLayout()); //Layout a bordi
        remoteVideoPanel.setBackground(Color.BLACK); //Sfondo nero
        remoteVideoPanel.setPreferredSize(new Dimension(TARGET_WIDTH, TARGET_HEIGHT)); //Dimensioni pannello

        remoteVideoLabel = new JLabel("In attesa...", SwingConstants.CENTER); //Label video remoto
        remoteVideoLabel.setForeground(Color.WHITE); //Colore testo
        remoteVideoLabel.setFont(new Font("Arial", Font.BOLD, 14)); //Font label
        remoteVideoPanel.add(remoteVideoLabel, BorderLayout.CENTER); //Aggiunge label al centro

        remoteStatusLabel = new JLabel("Avversario", SwingConstants.CENTER); //Label titolo
        remoteStatusLabel.setForeground(Color.WHITE); //Colore testo
        remoteStatusLabel.setBackground(new Color(76, 175, 80)); //Sfondo verde
        remoteStatusLabel.setOpaque(true); //Rende visibile lo sfondo
        remoteStatusLabel.setFont(new Font("Arial", Font.BOLD, 12)); //Font label
        remoteVideoPanel.add(remoteStatusLabel, BorderLayout.SOUTH); //Aggiunge label in basso

        videoContainer.add(localVideoPanel); //Aggiunge pannello locale
        videoContainer.add(remoteVideoPanel); //Aggiunge pannello remoto

        add(videoContainer, BorderLayout.CENTER); //Aggiunge contenitore al centro
    }

    //Avvia lo streaming video
    public boolean startStreaming(String myNickname, String opponentNickname, String serverAddress) {
        this.myNickname = myNickname; //Salva nickname
        this.opponentNickname = opponentNickname; //Salva nickname avversario

        try {
            //Crea nuovi executor ogni volta che si avvia lo streaming
            if (streamExecutor == null || streamExecutor.isShutdown()) { //Se executor non esiste o è chiuso
                streamExecutor = Executors.newSingleThreadExecutor(); //Crea nuovo executor
            }
            if (receiveExecutor == null || receiveExecutor.isShutdown()) { //Se executor non esiste o è chiuso
                receiveExecutor = Executors.newSingleThreadExecutor(); //Crea nuovo executor
            }

            //Connetti al server video
            System.out.println("Connessione al server video: " + serverAddress + ":12347");
            videoSocket = new Socket(serverAddress, 12347); //Apre socket sulla porta video
            videoOut = new DataOutputStream(new BufferedOutputStream(videoSocket.getOutputStream())); //Crea stream uscita
            videoIn = new DataInputStream(new BufferedInputStream(videoSocket.getInputStream())); //Crea stream ingresso

            //Invia il nickname per la registrazione
            byte[] nicknameBytes = myNickname.getBytes("UTF-8"); //Converte nickname in bytes
            videoOut.writeInt(nicknameBytes.length); //Scrive lunghezza
            videoOut.write(nicknameBytes); //Scrive bytes nickname

            //Invia anche il nickname dell'avversario
            byte[] opponentBytes = opponentNickname.getBytes("UTF-8"); //Converte nickname avversario in bytes
            videoOut.writeInt(opponentBytes.length); //Scrive lunghezza
            videoOut.write(opponentBytes); //Scrive bytes nickname avversario

            videoOut.flush(); //Flush immediato
            System.out.println("Inviata registrazione: " + myNickname + " -> " + opponentNickname);

            //Attendi conferma
            int response = videoIn.readInt(); //Legge risposta
            if (response != 1) { //Se non OK
                throw new IOException("Registrazione video fallita");
            }
            System.out.println("Registrazione video confermata");

            //Inizializza la webcam
            webcam = Webcam.getDefault(); //Ottiene webcam di default
            if (webcam == null) { //Se non trovata
                JOptionPane.showMessageDialog(this,
                        "Nessuna webcam trovata sul sistema!",
                        "Errore Webcam",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }

            //Imposta la risoluzione
            Dimension resolution = WebcamResolution.VGA.getSize(); //Risoluzione VGA 640x480
            webcam.setViewSize(resolution); //Imposta dimensioni

            //Crea il pannello webcam
            webcamPanel = new WebcamPanel(webcam, false); //Crea pannello webcam
            webcamPanel.setPreferredSize(new Dimension(TARGET_WIDTH, TARGET_HEIGHT)); //Dimensioni pannello
            webcamPanel.setFillArea(true); //Riempie area
            webcamPanel.setMirrored(true); //Effetto specchio

            //Sostituisci la label con il pannello webcam
            localVideoPanel.remove(localStatusLabel); //Rimuove label stato
            localVideoPanel.add(webcamPanel, BorderLayout.CENTER); //Aggiunge pannello webcam
            localVideoPanel.revalidate(); //Aggiorna layout
            localVideoPanel.repaint(); //Ridisegna

            //Avvia la webcam
            webcamPanel.start(); //Avvia cattura webcam

            streaming = true; //Segna streaming attivo

            //Avvia thread di streaming
            streamExecutor.submit(this::streamLoop); //Esegue loop streaming

            //Avvia thread di ricezione
            receiveExecutor.submit(this::receiveLoop); //Esegue loop ricezione

            System.out.println("Streaming video avviato");

            //Aggiorna label remoto
            SwingUtilities.invokeLater(() -> { //Esegue su thread UI
                remoteStatusLabel.setText(opponentNickname); //Aggiorna testo
            });

            return true;

        } catch (Exception e) {
            System.err.println("Errore avvio streaming: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Errore nell'avvio del video: " + e.getMessage(),
                    "Errore",
                    JOptionPane.ERROR_MESSAGE);
            stopStreaming(); //Ferma streaming
            return false;
        }
    }

    //Loop di invio frame
    private void streamLoop() {
        long frameDelay = 1000 / FPS; //Millisecondi tra frame
        System.out.println("Stream loop avviato - FPS: " + FPS);

        while (streaming && webcam != null && webcam.isOpen()) { //Finché attivo e webcam aperta
            try {
                long startTime = System.currentTimeMillis(); //Tempo inizio

                //Cattura frame dalla webcam
                BufferedImage image = webcam.getImage(); //Ottiene immagine
                if (image == null) { //Se immagine non disponibile
                    Thread.sleep(100); //Attende
                    continue;
                }

                //Ridimensiona per ridurre banda
                BufferedImage scaled = scaleImage(image, TARGET_WIDTH, TARGET_HEIGHT); //Scala immagine

                //Comprimi in JPEG
                ByteArrayOutputStream baos = new ByteArrayOutputStream(); //Crea stream bytes
                ImageIO.write(scaled, "jpg", baos); //Scrive immagine in JPEG
                byte[] frameData = baos.toByteArray(); //Ottiene array bytes

                //Invia al server
                synchronized (videoOut) { //Sincronizza stream
                    videoOut.writeInt(frameData.length); //Scrive dimensione
                    videoOut.write(frameData); //Scrive dati frame
                    videoOut.flush(); //Flush immediato
                }

                //Mantieni il frame rate
                long elapsed = System.currentTimeMillis() - startTime; //Tempo trascorso
                long sleepTime = frameDelay - elapsed; //Tempo da attendere
                if (sleepTime > 0) { //Se necessario attendere
                    Thread.sleep(sleepTime); //Attende
                }

            } catch (InterruptedException e) {
                System.out.println("Stream loop interrotto");
                break;
            } catch (IOException e) {
                System.err.println("Errore streaming: " + e.getMessage());
                break;
            }
        }

        System.out.println("Stream loop terminato");
    }

    //Loop di ricezione frame
    private void receiveLoop() {
        System.out.println("Receive loop avviato - In attesa di frame da " + opponentNickname);

        while (streaming) { //Finché attivo
            try {
                //Leggi dimensione frame
                int frameSize = videoIn.readInt(); //Legge dimensione

                if (frameSize <= 0 || frameSize > 5_000_000) { //Se dimensione non valida
                    System.err.println("Dimensione frame non valida: " + frameSize);
                    break;
                }

                //Leggi dati frame
                byte[] frameData = new byte[frameSize]; //Crea buffer
                videoIn.readFully(frameData); //Legge dati completi

                //Decodifica immagine
                ByteArrayInputStream bais = new ByteArrayInputStream(frameData); //Crea stream da bytes
                BufferedImage image = ImageIO.read(bais); //Legge immagine

                if (image != null) { //Se immagine valida
                    //Aggiorna l'interfaccia nel thread EDT
                    final ImageIcon icon = new ImageIcon(image); //Crea icona
                    SwingUtilities.invokeLater(() -> { //Esegue su thread UI
                        remoteVideoLabel.setIcon(icon); //Imposta icona
                        remoteVideoLabel.setText(""); //Pulisce testo
                    });
                }

            } catch (EOFException | SocketException e) {
                System.out.println("Connessione video remota chiusa");
                break;
            } catch (IOException e) {
                System.err.println("Errore ricezione: " + e.getMessage());
                break;
            }
        }

        SwingUtilities.invokeLater(() -> { //Esegue su thread UI
            remoteVideoLabel.setIcon(null); //Rimuove icona
            remoteVideoLabel.setText("Disconnesso"); //Aggiorna testo
        });

        System.out.println("Receive loop terminato");
    }

    //Ridimensiona immagine
    private BufferedImage scaleImage(BufferedImage original, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); //Crea immagine scalata
        Graphics2D g2d = scaled.createGraphics(); //Ottiene contesto grafico
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); //Interpolazione bilineare
        g2d.drawImage(original, 0, 0, width, height, null); //Disegna immagine scalata
        g2d.dispose(); //Rilascia risorse
        return scaled; //Restituisce immagine
    }

    //Ferma lo streaming
    public void stopStreaming() {
        System.out.println("Richiesta di stop streaming");
        streaming = false; //Disattiva flag

        //Chiudi webcam
        if (webcamPanel != null) { //Se pannello esiste
            webcamPanel.stop(); //Ferma pannello
        }
        if (webcam != null && webcam.isOpen()) { //Se webcam aperta
            webcam.close(); //Chiude webcam
        }

        //Chiudi socket
        try {
            if (videoSocket != null && !videoSocket.isClosed()) { //Se socket aperto
                videoSocket.close(); //Chiude socket
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Shutdown executor
        if (streamExecutor != null && !streamExecutor.isShutdown()) { //Se executor attivo
            streamExecutor.shutdownNow(); //Termina executor
        }
        if (receiveExecutor != null && !receiveExecutor.isShutdown()) { //Se executor attivo
            receiveExecutor.shutdownNow(); //Termina executor
        }

        //Ripristina UI
        SwingUtilities.invokeLater(() -> { //Esegue su thread UI
            if (webcamPanel != null) { //Se pannello webcam esiste
                localVideoPanel.remove(webcamPanel); //Rimuove pannello webcam
            }
            localVideoPanel.add(localStatusLabel, BorderLayout.CENTER); //Aggiunge label stato
            localStatusLabel.setText("Video Fermato"); //Aggiorna testo
            remoteVideoLabel.setIcon(null); //Rimuove icona
            remoteVideoLabel.setText("In attesa..."); //Ripristina testo
            remoteStatusLabel.setText("Avversario"); //Ripristina testo
            localVideoPanel.revalidate(); //Aggiorna layout
            localVideoPanel.repaint(); //Ridisegna
        });

        System.out.println("Streaming video fermato");
    }

    //Verifica se streaming attivo
    public boolean isStreaming() {
        return streaming; //Restituisce stato
    }
}