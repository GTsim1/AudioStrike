from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from typing import Dict, List
import json

app = FastAPI()

# Store active websocket connections by server IP
# server_ip -> dict of {websocket: username}
active_connections: Dict[str, Dict[WebSocket, str]] = {}
# Store the current song of each user
# username -> song
user_songs: Dict[str, str] = {}

class ConnectionManager:
    def __init__(self):
        pass

    async def connect(self, websocket: WebSocket, server_ip: str, username: str):
        await websocket.accept()
        if server_ip not in active_connections:
            active_connections[server_ip] = {}
        active_connections[server_ip][websocket] = username
        # Send current state of the server immediately upon connection
        await self.broadcast_server_state(server_ip)

    def disconnect(self, websocket: WebSocket, server_ip: str):
        if server_ip in active_connections:
            username = active_connections[server_ip].pop(websocket, None)
            if username and username in user_songs:
                # Optional: remove their song when they disconnect
                del user_songs[username]

    async def broadcast_server_state(self, server_ip: str):
        if server_ip not in active_connections:
            return
            
        # Collect all active players and their songs on this server
        players = []
        for ws, uname in active_connections[server_ip].items():
            song = user_songs.get(uname, "")
            players.append({"username": uname, "song": song})
            
        message = json.dumps({"players": players})
        
        # Broadcast to everyone on the server
        for ws in list(active_connections[server_ip].keys()):
            try:
                await ws.send_text(message)
            except Exception:
                # Remove stale connection
                pass

manager = ConnectionManager()

import re

def is_valid_song(song: str) -> bool:
    if not song:
        return True # Empty string is fine (clearing song)
    if len(song) > 100:
        return False # Too long to be a real song name
    
    # Check for URLs, discord invites, or self-promotion
    bad_patterns = [
        r"discord\.gg",
        r"http://",
        r"https://",
        r"\.com",
        r"\.net",
        r"\.org",
        r"join my",
        r"subscribe to",
        r"youtube\.com"
    ]
    song_lower = song.lower()
    for pattern in bad_patterns:
        if re.search(pattern, song_lower):
            return False
    return True

@app.websocket("/ws/{server_ip}/{username}")
async def websocket_endpoint(websocket: WebSocket, server_ip: str, username: str):
    await manager.connect(websocket, server_ip, username)
    try:
        while True:
            data = await websocket.receive_text()
            # Expecting a JSON payload: {"song": "New Song Name"}
            try:
                payload = json.loads(data)
                if "song" in payload:
                    new_song = payload["song"]
                    if is_valid_song(new_song):
                        user_songs[username] = new_song
                        # Broadcast the new state to everyone on the server
                        await manager.broadcast_server_state(server_ip)
                    else:
                        # If they send an invalid/ad song, secretly drop it to prevent spam
                        pass
            except json.JSONDecodeError:
                pass
    except WebSocketDisconnect:
        manager.disconnect(websocket, server_ip)
        await manager.broadcast_server_state(server_ip)
