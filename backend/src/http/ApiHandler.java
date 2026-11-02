package http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import exception.AppException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import model.Message;
import model.Room;
import model.User;
import service.MatchService;
import service.MessageService;
import service.RoomService;
import service.SessionManager;
import service.UserService;
import util.Json;
import util.Params;

/**
 * Receives every request that starts with /api and calls the right service.
 * Requests send form data (a=1&b=2); responses are always JSON.
 */
public class ApiHandler implements HttpHandler {

    private final UserService userService = new UserService();
    private final RoomService roomService = new RoomService();
    private final MessageService messageService = new MessageService();
    private final MatchService matchService = new MatchService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        int status = 200;
        String body;
        try {
            body = route(exchange);
        } catch (AppException e) {
            status = e.getStatus();
            body = Json.obj("error", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            status = 500;
            body = Json.obj("error", "Unexpected server error.");
        }
        send(exchange, status, body);
    }

    private String route(HttpExchange exchange) throws AppException, IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        String rawParams = "GET".equals(method)
                ? exchange.getRequestURI().getRawQuery()
                : new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = Params.parse(rawParams);

        switch (method + " " + path) {

            case "POST /api/register": {
                User user = userService.register(params.get("username"), params.get("email"), params.get("password"));
                String token = SessionManager.createSession(user.getId());
                return Json.obj("token", token, "user", user);
            }

            case "POST /api/login": {
                User user = userService.login(params.get("identifier"), params.get("password"));
                String token = SessionManager.createSession(user.getId());
                return Json.obj("token", token, "user", user);
            }

            case "POST /api/logout": {
                String token = tokenFrom(exchange);
                Integer userId = SessionManager.getUserId(token);
                if (userId != null) {
                    matchService.cancelSearch(userId);
                    Room active = roomService.getActiveRoomFor(userId);
                    if (active != null) {
                        roomService.endRoom(active.getId(), userId);
                    }
                    SessionManager.remove(token);
                }
                return Json.obj("ok", true);
            }

            case "GET /api/stats":
                return matchService.getStatsJson();

            case "GET /api/me": {
                User user = userService.getById(requireUser(exchange));
                return Json.obj("user", user);
            }

            case "GET /api/state": {
                int userId = requireUser(exchange);
                Room room = roomService.getActiveRoomFor(userId);
                if (room != null) {
                    return Json.obj("status", "chatting", "room", room);
                }
                if (matchService.isWaiting(userId)) {
                    return Json.obj("status", "searching");
                }
                return Json.obj("status", "idle");
            }

            case "POST /api/match/find": {
                int userId = requireUser(exchange);
                Room room = matchService.findMatch(userId);
                if (room == null) {
                    return Json.obj("status", "searching");
                }
                return Json.obj("status", "matched", "room", room);
            }

            case "POST /api/match/cancel": {
                matchService.cancelSearch(requireUser(exchange));
                return Json.obj("status", "idle");
            }

            case "POST /api/rooms/end": {
                int userId = requireUser(exchange);
                roomService.endRoom(intParam(params, "roomId"), userId);
                return Json.obj("status", "ended");
            }

            case "GET /api/messages": {
                int userId = requireUser(exchange);
                int roomId = intParam(params, "roomId");
                int after = params.containsKey("after") ? intParam(params, "after") : 0;
                Room room = roomService.getRoom(roomId, userId);
                List<Message> list = messageService.getMessages(roomId, userId, after);
                return Json.obj(
                        "roomStatus", room.getStatus(),
                        "endedBy", room.getEndedBy(),
                        "messages", Json.raw(Json.array(list)));
            }

            case "POST /api/messages": {
                int userId = requireUser(exchange);
                Message message = messageService.send(intParam(params, "roomId"), userId, params.get("content"));
                return Json.obj("message", message);
            }

            default:
                throw new AppException("Unknown endpoint: " + method + " " + path, 404);
        }
    }

    // ---------- helpers ----------

    private String tokenFrom(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private int requireUser(HttpExchange exchange) throws AppException {
        String token = tokenFrom(exchange);
        if (token == null) {
            throw new AppException("Please log in first.", 401);
        }
        Integer userId = SessionManager.getUserId(token);
        if (userId == null) {
            throw new AppException("Your session expired. Please log in again.", 401);
        }
        return userId;
    }

    private int intParam(Map<String, String> params, String name) throws AppException {
        String raw = params.get(name);
        if (raw == null) {
            throw new AppException("Missing field: " + name, 400);
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new AppException("Field '" + name + "' must be a number.", 400);
        }
    }

    private void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
