import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class AppPanel extends JPanel implements Runnable, KeyListener {

    // dimensions
    public static final int WIDTH = 1500;
    public static final int HEIGHT = 700;
    public static final int SCALE = 1;

    // game thread
    private Thread thread;
    private boolean running;
    private int FPS = 60;
    private long targetTime = 1000 / FPS;

    // image
    private BufferedImage image;
    private Graphics2D g;

    // game state manager
    private AppState appState;

    public AppPanel() throws IOException {
        super();
        setPreferredSize(
                new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
        setFocusable(true);
        requestFocus();
    }

    public void addNotify() {
        super.addNotify();
        if (thread == null) {
            thread = new Thread(this);
            addKeyListener(this);
            thread.start();
        }
    }

    private void init() throws IOException {
        image = new BufferedImage(
                WIDTH, HEIGHT,
                BufferedImage.TYPE_INT_RGB
        );
        g = (Graphics2D) image.getGraphics();

        running = true;

        appState = new AppState();
    }

    public void run() {

        try {
            init();
        } catch (IOException e) {
            e.printStackTrace();
        }

        long start;
        long elapsed;
        long wait;

        // game loop
        while (running) {

            start = System.nanoTime();

            try {
                update();
            } catch (IOException e) {
                e.printStackTrace();
            }
            draw();
            drawToScreen();

            elapsed = System.nanoTime() - start;

            wait = targetTime - elapsed / 1000000;
            if (wait < 0) wait = 5;

            try {
                Thread.sleep(wait);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

    private void update() throws IOException {
        appState.update();
    }

    private void draw() {
        appState.draw(g);
    }

    private void drawToScreen() {
        Graphics g2 = getGraphics();
        g2.drawImage(image, 0, 0,
                WIDTH * SCALE, HEIGHT * SCALE,
                null);
        g2.dispose();
    }

    public void keyTyped(KeyEvent key) {
    }

    public void keyPressed(KeyEvent key) {
        appState.keyPressed(key.getKeyCode());
    }

    public void keyReleased(KeyEvent key) {
        appState.keyReleased(key.getKeyCode());
    }
}
