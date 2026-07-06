from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from typing import Dict, List
import json
import os
import requests
import base64
import asyncio

app = FastAPI()

FIREBASE_URL = os.environ.get("FIREBASE_URL", "")
FIREBASE_SECRET = os.environ.get("FIREBASE_SECRET", "")

def get_firebase_url(path: str) -> str:
    url = f"{FIREBASE_URL.rstrip('/')}/{path}.json"
    if FIREBASE_SECRET:
        url += f"?auth={FIREBASE_SECRET}"
    return url

def safe_key(song: str) -> str:
    # Firebase keys cannot contain . # $ [ ]
    return base64.urlsafe_b64encode(song.encode('utf-8')).decode('utf-8').rstrip('=')

# In-memory cache for song likes to reduce DB reads
song_likes_cache: Dict[str, int] = {}

@app.get("/")
def read_root():
    return {"status": "AudioStrike Server is Running!"}

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
        
        total_players = len(active_connections[server_ip])
        print(f"[+] Player '{username}' connected to server '{server_ip}'. Total players on server: {total_players}")
        
        # Send current state of the server immediately upon connection
        await self.broadcast_server_state(server_ip)

    def disconnect(self, websocket: WebSocket, server_ip: str):
        if server_ip in active_connections:
            username = active_connections[server_ip].pop(websocket, None)
            if username and username in user_songs:
                # Optional: remove their song when they disconnect
                del user_songs[username]
                
            total_players = len(active_connections[server_ip])
            print(f"[-] Player '{username}' disconnected from server '{server_ip}'. Total players on server: {total_players}")
            
            # Clean up empty servers
            if total_players == 0:
                del active_connections[server_ip]

    async def broadcast_server_state(self, server_ip: str):
        if server_ip not in active_connections:
            return
            
        # Collect all active players and their songs on this server
        players = []
        for ws, uname in active_connections[server_ip].items():
            song = user_songs.get(uname, "")
            likes = 0
            if song:
                if song in song_likes_cache:
                    likes = song_likes_cache[song]
                elif FIREBASE_URL:
                    # Fetch likes from Firebase in the background (or block briefly)
                    try:
                        resp = requests.get(get_firebase_url(f"songs/{safe_key(song)}/likes"), timeout=2)
                        if resp.status_code == 200 and resp.json() is not None:
                            likes = int(resp.json())
                            song_likes_cache[song] = likes
                    except Exception:
                        pass
            players.append({"username": uname, "song": song, "likes": likes})
            
        message = json.dumps({"type": "state", "players": players})
        
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
            try:
                payload = json.loads(data)
                
                if "song" in payload and "action" not in payload:
                    new_song = payload["song"]
                    if is_valid_song(new_song):
                        user_songs[username] = new_song
                        # Broadcast the new state to everyone on the server
                        await manager.broadcast_server_state(server_ip)
                        
                elif "action" in payload:
                    action = payload["action"]
                    
                    if action == "like" and "song" in payload:
                        target_song = payload["song"]
                        print(f"Received like action for '{target_song}' from '{username}'", flush=True)
                        
                        if not FIREBASE_URL:
                            await websocket.send_text(json.dumps({"type": "like_error", "message": "Firebase URL is missing in server secrets!"}))
                            continue
                            
                        s_key = safe_key(target_song)
                        safe_user = safe_key(username)
                        
                        try:
                            # 1. Check if user already liked it
                            print("Step 1: Checking if liked", flush=True)
                            check_resp = requests.get(get_firebase_url(f"user_likes/{safe_user}/{s_key}"), timeout=2)
                            
                            if check_resp.status_code != 200:
                                print(f"Step 1 Failed: Status {check_resp.status_code}, {check_resp.text}", flush=True)
                                # Firebase Error (e.g. Permission Denied)
                                error_msg = str(check_resp.text)
                                await websocket.send_text(json.dumps({"type": "like_error", "message": f"Firebase Error: {error_msg}"}))
                            elif check_resp.json() is not True:
                                print("Step 2: User hasn't liked it yet. Putting new like.", flush=True)
                                # 2. Mark as liked
                                requests.put(get_firebase_url(f"user_likes/{safe_user}/{s_key}"), json=True)
                                
                                print("Step 3: Incrementing likes count.", flush=True)
                                # 3. Increment likes
                                current_likes = 0
                                likes_resp = requests.get(get_firebase_url(f"songs/{s_key}/likes"), timeout=2)
                                if likes_resp.status_code == 200 and likes_resp.json() is not None:
                                    try:
                                        current_likes = int(likes_resp.json())
                                    except ValueError:
                                        pass
                                    
                                new_likes = current_likes + 1
                                requests.patch(get_firebase_url(f"songs/{s_key}"), json={"name": target_song, "likes": new_likes})
                                song_likes_cache[target_song] = new_likes
                                
                                print("Step 4: Broadcasting server state.", flush=True)
                                # 4. Re-broadcast to show the new like instantly
                                await manager.broadcast_server_state(server_ip)
                                
                                print("Step 5: Sending like_success.", flush=True)
                                # 5. Tell the user it succeeded
                                await websocket.send_text(json.dumps({"type": "like_success", "song": target_song}))
                            else:
                                print("Step 2 Alternative: User already liked it.", flush=True)
                                await websocket.send_text(json.dumps({
                                    "type": "like_error", 
                                    "message": "You already liked this song!",
                                    "song": target_song
                                }))
                            print("Like flow completed successfully.", flush=True)
                        except Exception as inner_e:
                            print(f"Firebase Exception: {inner_e}", flush=True)
                            await websocket.send_text(json.dumps({"type": "like_error", "message": f"Python Error: {str(inner_e)}"}))

                    elif action == "get_top":
                        if not FIREBASE_URL:
                            await websocket.send_text(json.dumps({"type": "like_error", "message": "Firebase URL is missing!"}))
                            continue
                            
                        # Fetch all songs
                        resp = requests.get(get_firebase_url("songs"), timeout=3)
                        if resp.status_code == 200 and resp.json():
                            songs_data = resp.json()
                            songs_list = []
                            for k, v in songs_data.items():
                                if v and "name" in v and "likes" in v:
                                    songs_list.append({"name": v["name"], "likes": int(v["likes"])})
                            
                            # Sort by likes descending
                            songs_list.sort(key=lambda x: x["likes"], reverse=True)
                            top_10 = songs_list[:10]
                            await websocket.send_text(json.dumps({"type": "top_songs", "songs": top_10}))

            except json.JSONDecodeError:
                pass
            except Exception as e:
                print(f"WS Error: {e}")
    except WebSocketDisconnect:
        manager.disconnect(websocket, server_ip)
        await manager.broadcast_server_state(server_ip)
