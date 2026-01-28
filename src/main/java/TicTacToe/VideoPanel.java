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
    private JPanel localVideoPanel; // Pannello per video locale
    private JPanel remoteVideoPanel; // Pannello per video remoto
    private WebcamPanel webcamPanel; // Pannello webcam locale
    private JLabel remoteVideoLabel; // Label per video remoto
    private JLabel localStatusLabel; // Stato video locale
    private JLabel remoteStatusLabel; // Stato video remoto

    private Webcam webcam; // Webcam
    private volatile boolean streaming = false; // Flag streaming attivo
    private Socket videoSocket; // Socket verso server video
    private DataOutputStream videoOut; // Stream output video
    private DataInputStream videoIn; // Stream input video

    private String myNickname;
    private String opponentNickname;

    private ExecutorService streamExecutor; // Thread per streaming
    private ExecutorService receiveExecutor; // Thread per ricezione

    private static final int TARGET_WIDTH = 320; // Larghezza video
    private static final int TARGET_HEIGHT = 240; // Altezza video
    private static final int FPS = 15; // Frame per secondo
    private static final float JPEG_QUALITY = 0.7f; // Qualità JPEG

    public VideoPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.DARK_GRAY);
        setupUI();
        streamExecutor = Executors.newSingleThreadExecutor();
        receiveExecutor = Executors.newSingleThreadExecutor();
    }

    private void setupUI() {
        JPanel videoContainer = new JPanel(new GridLayout(1, 2, 10, 0));
        videoContainer.setBackground(Color.DARK_GRAY);

        // Pannello video locale
        localVideoPanel = new JPanel(new BorderLayout());
        localVideoPanel.setBackground(Color.BLACK);
        localVideoPanel.setPreferredSize(new Dimension(TARGET_WIDTH, TARGET_HEIGHT));

        localStatusLabel = new JLabel("Video Non Attivo", SwingConstants.CENTER);
        localStatusLabel.setForeground(Color.WHITE);
        localStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        localVideoPanel.add(localStatusLabel, BorderLayout.CENTER);

        JLabel localLabel = new JLabel("La tua webcam", SwingConstants.CENTER);
        localLabel.setForeground(Color.WHITE);
        localLabel.setBackground(new Color(76, 175, 80));
        localLabel.setOpaque(true);
        localLabel.setFont(new Font("Arial", Font.BOLD, 12));
        localVideoPanel.add(localLabel, BorderLayout.SOUTH);

        // Pannello video remoto
        remoteVideoPanel = new JPanel(new BorderLayout());
        remoteVideoPanel.setBackground(Color.BLACK);
        remoteVideoPanel.setPreferredSize(new Dimension(TARGET_WIDTH, TARGET_HEIGHT));

        remoteVideoLabel = new JLabel("In attesa...", SwingConstants.CENTER);
        remoteVideoLabel.setForeground(Color.WHITE);
        remoteVideoLabel.setFont(new Font("Arial", Font.BOLD, 14));
        remoteVideoPanel.add(remoteVideoLabel, BorderLayout.CENTER);

        remoteStatusLabel = new JLabel("Avversario", SwingConstants.CENTER);
        remoteStatusLabel.setForeground(Color.WHITE);
        remoteStatusLabel.setBackground(new Color(76, 175, 80));
        remoteStatusLabel.setOpaque(true);
        remoteStatusLabel.setFont(new Font("Arial", Font.BOLD, 12));
        remoteVideoPanel.add(remoteStatusLabel, BorderLayout.SOUTH);

        videoContainer.add(localVideoPanel);
        videoContainer.add(remoteVideoPanel);

        add(videoContainer, BorderLayout.CENTER);
    }

    /**
     * Avvia lo streaming video
     */
    public boolean startStreaming(String myNickname, String opponentNickname, String serverAddress) {
        this.myNickname = myNickname;
        this.opponentNickname = opponentNickname;

        try {
            // Connetti al server video
            System.out.println("Connessione al server video: " + serverAddress + ":12347");
            videoSocket = new Socket(serverAddress, 12347);
            videoOut = new DataOutputStream(new BufferedOutputStream(videoSocket.getOutputStream()));
            videoIn = new DataInputStream(new BufferedInputStream(videoSocket.getInputStream()));

            // Invia il nickname per la registrazione
            byte[] nicknameBytes = myNickname.getBytes("UTF-8");
            videoOut.writeInt(nicknameBytes.length);
            videoOut.write(nicknameBytes);

            // IMPORTANTE: Invia anche il nickname dell'avversario
            byte[] opponentBytes = opponentNickname.getBytes("UTF-8");
            videoOut.writeInt(opponentBytes.length);
            videoOut.write(opponentBytes);

            videoOut.flush();
            System.out.println("Inviata registrazione: " + myNickname + " -> " + opponentNickname);

            // Attendi conferma
            int response = videoIn.readInt();
            if (response != 1) {
                throw new IOException("Registrazione video fallita");
            }
            System.out.println("Registrazione video confermata");

            // Inizializza la webcam
            webcam = Webcam.getDefault();
            if (webcam == null) {
                JOptionPane.showMessageDialog(this,
                        "Nessuna webcam trovata sul sistema!",
                        "Errore Webcam",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }

            // Imposta la risoluzione
            Dimension resolution = WebcamResolution.VGA.getSize(); // 640x480
            webcam.setViewSize(resolution);

            // Crea il pannello webcam
            webcamPanel = new WebcamPanel(webcam, false);
            webcamPanel.setPreferredSize(new Dimension(TARGET_WIDTH, TARGET_HEIGHT));
            webcamPanel.setFillArea(true);
            webcamPanel.setMirrored(true); // Effetto specchio

            // Sostituisci la label con il pannello webcam
            localVideoPanel.remove(localStatusLabel);
            localVideoPanel.add(webcamPanel, BorderLayout.CENTER);
            localVideoPanel.revalidate();
            localVideoPanel.repaint();

            // Avvia la webcam
            webcamPanel.start();

            streaming = true;

            // Avvia thread di streaming
            streamExecutor.submit(this::streamLoop);

            // Avvia thread di ricezione
            receiveExecutor.submit(this::receiveLoop);

            System.out.println("Streaming video avviato");

            // Aggiorna label remoto
            SwingUtilities.invokeLater(() -> {
                remoteStatusLabel.setText(opponentNickname);
            });

            return true;

        } catch (Exception e) {
            System.err.println("Errore avvio streaming: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Errore nell'avvio del video: " + e.getMessage(),
                    "Errore",
                    JOptionPane.ERROR_MESSAGE);
            stopStreaming();
            return false;
        }
    }

    private void streamLoop() {
        long frameDelay = 1000 / FPS; // Millisecondi tra frame
        System.out.println("Stream loop avviato - FPS: " + FPS);

        while (streaming && webcam != null && webcam.isOpen()) {
            try {
                long startTime = System.currentTimeMillis();

                // Cattura frame dalla webcam
                BufferedImage image = webcam.getImage();
                if (image == null) {
                    Thread.sleep(100);
                    continue;
                }

                // Ridimensiona per ridurre banda
                BufferedImage scaled = scaleImage(image, TARGET_WIDTH, TARGET_HEIGHT);

                // Comprimi in JPEG
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(scaled, "jpg", baos);
                byte[] frameData = baos.toByteArray();

                // Invia al server
                synchronized (videoOut) {
                    videoOut.writeInt(frameData.length);
                    videoOut.write(frameData);
                    videoOut.flush();
                }

                // Mantieni il frame rate
                long elapsed = System.currentTimeMillis() - startTime;
                long sleepTime = frameDelay - elapsed;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
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

    private void receiveLoop() {
        System.out.println("Receive loop avviato - In attesa di frame da " + opponentNickname);

        while (streaming) {
            try {
                // Leggi dimensione frame
                int frameSize = videoIn.readInt();

                if (frameSize <= 0 || frameSize > 5_000_000) {
                    System.err.println("Dimensione frame non valida: " + frameSize);
                    break;
                }

                // Leggi dati frame
                byte[] frameData = new byte[frameSize];
                videoIn.readFully(frameData);

                // Decodifica immagine
                ByteArrayInputStream bais = new ByteArrayInputStream(frameData);
                BufferedImage image = ImageIO.read(bais);

                if (image != null) {
                    // Aggiorna l'interfaccia nel thread EDT
                    final ImageIcon icon = new ImageIcon(image);
                    SwingUtilities.invokeLater(() -> {
                        remoteVideoLabel.setIcon(icon);
                        remoteVideoLabel.setText("");
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

        SwingUtilities.invokeLater(() -> {
            remoteVideoLabel.setIcon(null);
            remoteVideoLabel.setText("Disconnesso");
        });

        System.out.println("Receive loop terminato");
    }

    private BufferedImage scaleImage(BufferedImage original, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, width, height, null);
        g2d.dispose();
        return scaled;
    }

    public void stopStreaming() {
        System.out.println("Richiesta di stop streaming");
        streaming = false;

        // Chiudi webcam
        if (webcamPanel != null) {
            webcamPanel.stop();
        }
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }

        // Chiudi socket
        try {
            if (videoSocket != null && !videoSocket.isClosed()) {
                videoSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Shutdown executor
        if (streamExecutor != null) {
            streamExecutor.shutdownNow();
        }
        if (receiveExecutor != null) {
            receiveExecutor.shutdownNow();
        }

        // Ripristina UI
        SwingUtilities.invokeLater(() -> {
            if (webcamPanel != null) {
                localVideoPanel.remove(webcamPanel);
            }
            localVideoPanel.add(localStatusLabel, BorderLayout.CENTER);
            localStatusLabel.setText("Video Fermato");
            remoteVideoLabel.setIcon(null);
            remoteVideoLabel.setText("In attesa...");
            remoteStatusLabel.setText("Avversario");
            localVideoPanel.revalidate();
            localVideoPanel.repaint();
        });

        System.out.println("Streaming video fermato");
    }

    public boolean isStreaming() {
        return streaming;
    }
}