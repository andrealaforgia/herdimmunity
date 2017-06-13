import javax.swing.*;
import java.io.IOException;

public class App {

    public static void main(String[] args) throws IOException {
        JFrame window = new JFrame("Herd immunity");
        window.setContentPane(new AppPanel());
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.pack();
        window.setVisible(true);
    }
}
