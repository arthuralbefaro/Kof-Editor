import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class KofWebHost extends Application {

    private static final int PORT = Integer.parseInt(System.getProperty("kof.editor.port", "8765"));
    private Process server;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            String web = System.getProperty("kof.editor.web", "build/editor-web.kf");
            String kofBin = System.getProperty("kof.bin", "kof");
            ProcessBuilder pb = new ProcessBuilder(kofBin, "serve", web, "--port", String.valueOf(PORT));
            pb.redirectErrorStream(true);
            server = pb.start();
            Thread out = new Thread(() -> {
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(server.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println("[kof] " + line);
                    }
                } catch (Exception ignored) {
                }
            });
            out.setDaemon(true);
            out.start();
        } catch (Exception e) {
            System.err.println("falha ao iniciar kof serve: " + e.getMessage());
        }
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
        }

        String user = System.getProperty("user.name", "usuario");
        String host = "maquina";
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }
        WebView view = new WebView();
        view.getEngine().load("http://localhost:" + PORT + "/?user=" + user + "&host=" + host);
        view.getEngine().setJavaScriptEnabled(true);

        Scene scene = new Scene(view, 1280, 820);
        stage.setScene(scene);
        stage.setTitle("Kof Editor");
        String icon = System.getProperty("kof.editor.icon");
        if (icon != null && new java.io.File(icon).exists()) {
            stage.getIcons().add(new javafx.scene.image.Image(new java.io.File(icon).toURI().toString()));
        }
        stage.setOnCloseRequest(e -> {
            if (server != null) {
                server.destroy();
            }
            System.exit(0);
        });
        stage.show();
    }
}