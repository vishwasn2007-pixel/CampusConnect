"use strict";

/* =====================================================================
   CampusConnect - frontend logic (plain JavaScript, no frameworks)
   Talks to the Java backend with the Fetch API.
   ===================================================================== */

// ---------- constants ----------
const API = "/api";
const SEARCH_INTERVAL_MS = 1500;   // how often we ask "found someone yet?"
const POLL_INTERVAL_MS = 1000;     // how often we ask "any new messages?"
const STATS_INTERVAL_MS = 5000;
const COUNTER_DIGITS = 6;

// ---------- application state (variables) ----------
let token = localStorage.getItem("cc_token");
let currentUser = null;
let currentRoom = null;
let lastFetchedId = 0;             // highest message id received from the server
let renderedIds = new Set();       // ids already drawn, to avoid duplicates
let chatEnded = false;
let searchTimer = null;
let pollTimer = null;
let searchStartedAt = 0;
let searchBusy = false;
let pollBusy = false;

// ---------- tiny helpers ----------
function $(id) {
    return document.getElementById(id);
}

function setAll(selector, text) {
    const list = document.querySelectorAll(selector);
    for (let i = 0; i < list.length; i++) {
        list[i].textContent = text;
    }
}

const SCREENS = ["auth", "idle", "searching", "chat"];

function showScreen(name) {
    for (let i = 0; i < SCREENS.length; i++) {
        $(SCREENS[i] + "-screen").classList.toggle("hidden", SCREENS[i] !== name);
    }
}

function setUserName(name) {
    setAll(".user-name", name);
    $("idle-name").textContent = name;
}

function showError(message) {
    const box = $("auth-error");
    box.textContent = message;
    box.classList.remove("hidden");
}

function clearError() {
    $("auth-error").classList.add("hidden");
}

function formatTime(epochSeconds) {
    return new Date(epochSeconds * 1000).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

// ---------- talking to the backend ----------
async function api(path, method = "GET", data = {}) {
    const options = { method: method, headers: {} };
    if (token) {
        options.headers["Authorization"] = "Bearer " + token;
    }

    let url = API + path;
    const body = new URLSearchParams(data).toString();
    if (method === "GET") {
        if (body !== "") {
            url += "?" + body;
        }
    } else {
        options.headers["Content-Type"] = "application/x-www-form-urlencoded";
        options.body = body;
    }

    let response;
    try {
        response = await fetch(url, options);
    } catch (err) {
        throw new Error("Cannot reach the server. Is the Java backend running?");
    }

    let json = {};
    try {
        json = await response.json();
    } catch (err) {
        // empty or non-JSON body - keep json as {}
    }

    if (response.status === 401 && token) {
        handleSessionExpired();
    }
    if (!response.ok) {
        throw new Error(json.error || "Something went wrong.");
    }
    return json;
}

// ---------- login / register ----------
function switchTab(tabName) {
    const tabs = document.querySelectorAll(".tab");
    for (let i = 0; i < tabs.length; i++) {
        tabs[i].classList.toggle("active", tabs[i].dataset.tab === tabName);
    }
    const isLogin = tabName === "login";
    $("login-form").classList.toggle("hidden", !isLogin);
    $("register-form").classList.toggle("hidden", isLogin);
    $("note-login").classList.toggle("hidden", !isLogin);
    $("note-register").classList.toggle("hidden", isLogin);
    $("auth-legend").textContent = isLogin ? "Member login" : "New user sign-up";
    $("auth-title").textContent = isLogin ? "CampusConnect - Log in" : "CampusConnect - New user";
    clearError();
}

async function handleLogin(event) {
    event.preventDefault();
    clearError();

    const identifier = $("login-identifier").value.trim();
    const password = $("login-password").value;

    if (identifier === "" || password === "") {
        showError("Please fill in both fields.");
        return;
    }

    try {
        const result = await api("/login", "POST", { identifier: identifier, password: password });
        await startSession(result);
    } catch (err) {
        showError(err.message);
    }
}

async function handleRegister(event) {
    event.preventDefault();
    clearError();

    const username = $("reg-username").value.trim();
    const email = $("reg-email").value.trim();
    const password = $("reg-password").value;

    if (!/^[A-Za-z0-9_]{3,30}$/.test(username)) {
        showError("Username must be 3-30 characters: letters, numbers or underscore.");
        return;
    }
    if (!email.includes("@") || !email.includes(".")) {
        showError("Please enter a valid email address.");
        return;
    }
    if (password.length < 6) {
        showError("Password must be at least 6 characters.");
        return;
    }

    try {
        const result = await api("/register", "POST", { username: username, email: email, password: password });
        await startSession(result);
    } catch (err) {
        showError(err.message);
    }
}

async function startSession(result) {
    token = result.token;
    localStorage.setItem("cc_token", token);
    currentUser = result.user;
    setUserName(currentUser.username);
    $("login-form").reset();
    $("register-form").reset();
    await restoreState();
}

async function logout() {
    stopAllTimers();
    try {
        await api("/logout", "POST");
    } catch (err) {
        // ignore - we are leaving anyway
    }
    resetLocalSession();
}

function handleSessionExpired() {
    stopAllTimers();
    resetLocalSession();
}

function resetLocalSession() {
    token = null;
    currentUser = null;
    currentRoom = null;
    localStorage.removeItem("cc_token");
    switchTab("login");
    showScreen("auth");
    loadStats();
}

// ---------- deciding which screen to show ----------
async function restoreState() {
    try {
        const state = await api("/state");
        if (state.status === "chatting") {
            openChat(state.room);
        } else if (state.status === "searching") {
            startSearch();
        } else {
            showIdle();
        }
    } catch (err) {
        showIdle();
    }
}

function showIdle() {
    stopAllTimers();
    currentRoom = null;
    showScreen("idle");
    loadStats();
}

// ---------- searching for a stranger ----------
function startSearch() {
    stopAllTimers();
    currentRoom = null;
    searchStartedAt = Date.now();
    $("search-timer").textContent = "Waiting 0s";
    showScreen("searching");

    findOnce();
    searchTimer = setInterval(findOnce, SEARCH_INTERVAL_MS);
}

async function findOnce() {
    if (searchBusy) {
        return;
    }
    searchBusy = true;
    try {
        const seconds = Math.floor((Date.now() - searchStartedAt) / 1000);
        $("search-timer").textContent = "Waiting " + seconds + "s";

        const result = await api("/match/find", "POST");
        if (result.status === "matched") {
            openChat(result.room);
        }
    } catch (err) {
        // network hiccup: the next tick will try again
    } finally {
        searchBusy = false;
    }
}

async function cancelSearch() {
    stopAllTimers();
    try {
        await api("/match/cancel", "POST");
    } catch (err) {
        // ignore
    }
    showIdle();
}

// ---------- the chat ----------
function openChat(room) {
    stopAllTimers();
    currentRoom = room;
    chatEnded = false;
    lastFetchedId = 0;
    renderedIds = new Set();

    $("messages").innerHTML = "";
    $("partner-name").textContent = room.partner;
    $("chat-title-text").textContent = room.partner + " - Chat";
    $("chat-status").textContent = "Connected to " + room.partner;
    $("msg-count").textContent = "0 messages";
    document.querySelector(".chat-title .dot").classList.remove("off");
    $("message-input").disabled = false;
    $("send-btn").disabled = false;
    $("next-btn").classList.remove("pulse");

    showScreen("chat");
    addSystemMessage("You're now chatting with " + room.partner + ". Say hi!");
    $("message-input").focus();

    loadMessages();
    pollTimer = setInterval(loadMessages, POLL_INTERVAL_MS);
}

async function loadMessages() {
    if (!currentRoom || pollBusy) {
        return;
    }
    pollBusy = true;
    const roomId = currentRoom.id;
    try {
        const data = await api("/messages", "GET", { roomId: roomId, after: lastFetchedId });
        if (!currentRoom || currentRoom.id !== roomId) {
            return;     // user already moved on to another chat
        }
        for (let i = 0; i < data.messages.length; i++) {
            const message = data.messages[i];
            addMessageToView(message);
            if (message.id > lastFetchedId) {
                lastFetchedId = message.id;
            }
        }
        if (data.roomStatus === "ENDED") {
            onChatEnded(data.endedBy);
        }
    } catch (err) {
        // temporary problem - try again on the next tick
    } finally {
        pollBusy = false;
    }
}

function addMessageToView(message) {
    if (renderedIds.has(message.id)) {
        return;
    }
    renderedIds.add(message.id);

    const mine = message.senderId === currentUser.id;

    const line = document.createElement("div");
    line.className = "line";

    const who = document.createElement("b");
    who.className = "who " + (mine ? "me" : "them");
    who.textContent = (mine ? currentUser.username : message.sender) + ":";

    const text = document.createElement("span");
    text.textContent = " " + message.content + " ";      // textContent => safe from HTML injection

    const time = document.createElement("span");
    time.className = "time";
    time.textContent = "(" + formatTime(message.sentAt) + ")";

    line.appendChild(who);
    line.appendChild(text);
    line.appendChild(time);
    appendToMessages(line, mine);

    const count = renderedIds.size;
    $("msg-count").textContent = count + (count === 1 ? " message" : " messages");
}

function addSystemMessage(text) {
    const note = document.createElement("div");
    note.className = "sys";
    note.textContent = "*** " + text + " ***";
    appendToMessages(note, false);
}

function appendToMessages(element, forceScroll) {
    const box = $("messages");
    const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 80;
    box.appendChild(element);
    if (nearBottom || forceScroll) {
        box.scrollTop = box.scrollHeight;
    }
}

async function sendMessage(event) {
    event.preventDefault();
    if (!currentRoom || chatEnded) {
        return;
    }

    const input = $("message-input");
    const content = input.value.trim();
    if (content === "") {
        return;
    }
    if (content.length > 500) {
        addSystemMessage("Message is too long (max 500 characters).");
        return;
    }

    input.value = "";
    try {
        const result = await api("/messages", "POST", { roomId: currentRoom.id, content: content });
        addMessageToView(result.message);
    } catch (err) {
        input.value = content;                    // give the text back so nothing is lost
        addSystemMessage(err.message);
        if (/ended/i.test(err.message)) {
            loadMessages();
        }
    }
}

function onChatEnded(endedBy) {
    if (chatEnded) {
        return;
    }
    chatEnded = true;
    clearInterval(pollTimer);
    pollTimer = null;

    $("message-input").disabled = true;
    $("send-btn").disabled = true;
    $("chat-status").textContent = "Chat ended";
    document.querySelector(".chat-title .dot").classList.add("off");

    if (currentUser && endedBy === currentUser.id) {
        addSystemMessage("You ended the chat.");
    } else {
        addSystemMessage(currentRoom.partner + " has left the chat.");
    }
    addSystemMessage("Press Next person to find someone new.");
    $("next-btn").classList.add("pulse");
}

async function endCurrentRoom() {
    if (currentRoom && !chatEnded) {
        try {
            await api("/rooms/end", "POST", { roomId: currentRoom.id });
        } catch (err) {
            // already ended or not reachable - nothing more to do
        }
    }
}

async function nextStranger() {
    stopAllTimers();
    await endCurrentRoom();
    startSearch();
}

async function stopChatting() {
    stopAllTimers();
    await endCurrentRoom();
    showIdle();
}

// ---------- timers & stats ----------
function stopAllTimers() {
    if (searchTimer !== null) {
        clearInterval(searchTimer);
        searchTimer = null;
    }
    if (pollTimer !== null) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
}

function renderCounter(total) {
    const digits = String(Math.max(0, total)).padStart(COUNTER_DIGITS, "0").slice(-COUNTER_DIGITS);
    const box = $("counter");
    box.innerHTML = "";
    for (let i = 0; i < digits.length; i++) {
        const cell = document.createElement("span");
        cell.textContent = digits.charAt(i);
        box.appendChild(cell);
    }
}

async function loadStats() {
    try {
        const stats = await api("/stats");
        setAll(".stat-active", stats.activeChats + " chatting now");
        setAll(".stat-waiting", stats.usersWaiting + " waiting");
        setAll(".stat-total", stats.totalUsers + " registered");
        renderCounter(stats.totalUsers);
    } catch (err) {
        // stats are optional - ignore errors
    }
}

// ---------- wiring events & start-up ----------
function init() {
    const tabs = document.querySelectorAll(".tab");
    for (let i = 0; i < tabs.length; i++) {
        tabs[i].addEventListener("click", function () {
            switchTab(tabs[i].dataset.tab);
        });
    }

    $("to-register").addEventListener("click", function (event) {
        event.preventDefault();
        switchTab("register");
    });
    $("to-login").addEventListener("click", function (event) {
        event.preventDefault();
        switchTab("login");
    });

    const logoutLinks = document.querySelectorAll(".logout-link");
    for (let i = 0; i < logoutLinks.length; i++) {
        logoutLinks[i].addEventListener("click", function (event) {
            event.preventDefault();
            logout();
        });
    }

    $("login-form").addEventListener("submit", handleLogin);
    $("register-form").addEventListener("submit", handleRegister);
    $("find-btn").addEventListener("click", startSearch);
    $("cancel-btn").addEventListener("click", cancelSearch);
    $("next-btn").addEventListener("click", nextStranger);
    $("stop-btn").addEventListener("click", stopChatting);
    $("message-form").addEventListener("submit", sendMessage);

    renderCounter(0);
    setInterval(loadStats, STATS_INTERVAL_MS);

    if (token) {
        api("/me").then(function (data) {
            currentUser = data.user;
            setUserName(currentUser.username);
            restoreState();
        }).catch(function () {
            resetLocalSession();
        });
    } else {
        showScreen("auth");
        loadStats();
    }
}

init();
