import com.sun.net.httpserver.HttpServer;
import database.DatabaseConnection;
import exception.AppException;
import http.ApiHandler;
import http.StaticFileHandler;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import service.MatchService;
import service.MessageService;
import service.RoomService;
import service.Service;
import service.UserService;
import util.Config;

/** Starts CampusConnect: checks MySQL, then serves the API and the website. */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("=== CampusConnect ===");

        // Dynamic method dispatch: one Service[] array, each element answers with its own serviceName()
        Service[] services = {new UserService(), new RoomService(), new MessageService(), new MatchService()};
        for (Service service : services) {
            System.out.println("  loaded " + service.serviceName());
        }

        try {
            DatabaseConnection.testConnection();
        } catch (AppException e) {
            System.err.println();
            System.err.println("Could not connect to MySQL: " + e.getMessage());
            System.err.println("Check backend/config.properties (db.user / db.password) and that MySQL is running.");
            System.err.println("Also make sure you ran database/schema.sql once.");
            System.exit(1);
        }

        Path frontend = findFrontendFolder();
        int port = Config.getInt("server.port", 8080);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler());
        server.createContext("/", new StaticFileHandler(frontend));
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println();
        System.out.println("Serving website from: " + frontend.toAbsolutePath().normalize());
        System.out.println("Open  http://localhost:" + port + "  in your browser.");
        System.out.println("(Press Ctrl+C to stop.)");
    }

    private static Path findFrontendFolder() {
        for (String candidate : new String[]{"frontend", "../frontend"}) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        System.err.println("Could not find the 'frontend' folder. Run the server from the CampusConnect folder.");
        System.exit(1);
        return null;
    }
}
