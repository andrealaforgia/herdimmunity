import java.awt.*;
import java.io.IOException;

public abstract class State {
    protected AppState appState;
    public abstract void init() throws IOException;
    public abstract void update() throws IOException;
    public abstract void draw(Graphics2D graphics2D);
    public abstract void keyPressed(int k);
    public abstract void keyReleased(int k);
}
