# CampusConnect — random text chat (Omegle-style, text only)

Log in → press **Find a stranger** → get paired with another logged-in user → chat → press **Next** to meet someone else.

* **Frontend:** HTML + CSS + JavaScript (no frameworks), talks to the backend with `fetch`
* **Backend:** plain Java (built-in `com.sun.net.httpserver`, no Spring)
* **Database:** MySQL via JDBC

---

## 1. What to install (one time)

| Tool | Version | Check |
|------|---------|-------|
| JDK | **17 or newer** | `java -version` and `javac -version` |
| MySQL Server | 8.x | `mysql --version` |
| VS Code + "Extension Pack for Java" | latest | optional but recommended |

## 2. Create the database

Open a terminal in this folder and run (it asks for your MySQL root password):

```
mysql -u root -p < database/schema.sql
```

(Alternative: open `database/schema.sql` in MySQL Workbench and press the ⚡ Execute button.)

This creates the `campusconnect` database with all tables, the views and the indexes.

## 3. Tell the app your MySQL password

Open `backend/config.properties` and replace `YOUR_MYSQL_PASSWORD_HERE` with your real MySQL password
(change `db.user` too if you don't use `root`).

## 4. Add the MySQL driver (JDBC jar)

1. Download **mysql-connector-j-8.4.0.jar** from  
   https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
2. Put the file inside `backend/lib/`.

## 5. Run it

**Windows:** double-click `run.bat`  
**macOS / Linux:** `bash run.sh`

You should see `Connected to MySQL as 'root'.` and `Open http://localhost:8080`.

**Running from VS Code instead:** open the `CampusConnect` folder, wait for the Java extension to load,
open `backend/src/Main.java` and click **Run** above `main`. (`.vscode/` is already configured.)

## 6. Try it

1. Open http://localhost:8080 and register user **alice**.
2. Open a **private/incognito window** (or another browser) on the same address and register user **bob**.
3. Press **Find a stranger** in both windows → they get paired → chat.
4. **Next stranger** ends the chat for both and starts a new search. **Stop** ends it and goes home.

---

## Folder layout

```
CampusConnect/
├── database/schema.sql              MySQL tables, constraints, views, indexes
├── backend/
│   ├── config.properties            DB password + port
│   ├── lib/                         put the MySQL jar here
│   └── src/
│       ├── Main.java                starts the server
│       ├── model/                   Jsonable, BaseEntity (abstract), User, Room, Message
│       ├── service/                 Service (interface), UserService, RoomService,
│       │                            MessageService, MatchService, SessionManager
│       ├── database/                DatabaseConnection (JDBC)
│       ├── http/                    ApiHandler (REST API), StaticFileHandler
│       ├── exception/               AppException
│       └── util/                    Json, Params, Config
└── frontend/                        index.html, style.css, app.js
```

## How matching works

1. Pressing **Find a stranger** makes the browser call `POST /api/match/find` every 1.5 s.
2. Each call refreshes your row in `waiting_queue` and looks for the oldest *other* fresh waiting user.
3. If one exists, both are removed from the queue and a row is created in `rooms`; both browsers
   see `matched` on their next call and open the chat.
4. While chatting, each browser calls `GET /api/messages?roomId=..&after=..` every second.
5. Queue rows not refreshed for 10 s are ignored (that user closed the tab).

## Where each concept from your blueprint is used

| Concept | Where |
|---------|-------|
| Classes, objects, constructors, `this` | `model/User.java`, `Room.java`, `Message.java` |
| Constructor / method overloading | `User` & `Room` constructors, `RoomService.createRoom(...)`, `MessageService.getMessages(...)` |
| Encapsulation, getters/setters | private fields in every model class |
| Static & `final` | `User.MIN_PASSWORD_LENGTH`, `Message.MAX_LENGTH`, `SessionManager`, `MatchService.MATCH_LOCK` |
| Inheritance, abstract class | `BaseEntity` → `User`, `Room`, `Message` |
| Overriding, dynamic dispatch | `describe()` / `toJson()`; `Json.array(...)`; `Service[]` loop in `Main` |
| Interfaces | `Service`, `Jsonable` |
| Packages & access specifiers | `model`, `service`, `database`, `http`, `exception`, `util` |
| Exception handling | `AppException` + try/catch with SQLException in every service |
| Tables, PK, FK, constraints | `database/schema.sql` |
| INSERT / SELECT / UPDATE / DELETE | register, find match, `endRoom` (UPDATE), leave queue (DELETE) |
| JOIN, view | `message_details` view, `RoomService.getRoom` |
| Aggregates, subqueries | `chat_stats` view, partner query in `MatchService` |
| Indexes, normalization | `idx_messages_room_id`, separate users / rooms / messages / queue tables |
| JDBC | `DatabaseConnection` + PreparedStatements everywhere |
| Loops, arrays, objects, DOM, events, fetch | `frontend/app.js` |

## Troubleshooting

* **"Could not connect to MySQL"** – wrong password in `config.properties`, MySQL not running, or `schema.sql` not run yet.
* **"MySQL JDBC driver not found"** – the jar is missing from `backend/lib/`.
* **Port 8080 in use** – change `server.port` in `config.properties`.
* **`javac` not found** – install a JDK (not just a JRE) and restart the terminal.
* **Both windows stay on "Looking for someone"** – make sure the two windows are logged in as *different* users
  (use a private window; a normal tab shares the same login).

## Notes

* Passwords are stored as salted SHA-256 hashes. That is fine for a college project; for a real product use bcrypt/argon2.
* Login tokens live in server memory, so restarting the server logs everybody out.
