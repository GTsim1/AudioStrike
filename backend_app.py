from fastapi import FastAPI ,WebSocket ,WebSocketDisconnect 
from typing import Dict ,List 
import json 
import os 
import requests 
import base64 
import asyncio 
import time 
from collections import deque 

app =FastAPI ()

FIREBASE_URL =os .environ .get ("FIREBASE_URL","")
FIREBASE_SECRET =os .environ .get ("FIREBASE_SECRET","")

def get_firebase_url (path :str )->str :
    url =f"{FIREBASE_URL .rstrip ('/')}/{path }.json"
    if FIREBASE_SECRET :
        url +=f"?auth={FIREBASE_SECRET }"
    return url 

def safe_key (song :str )->str :

    return base64 .urlsafe_b64encode (song .encode ('utf-8')).decode ('utf-8').rstrip ('=')

song_likes_cache :Dict [str ,int ]={}

user_cooldowns :Dict [str ,float ]={}
song_cooldowns :Dict [str ,float ]={}

ip_strikes :Dict [str ,int ]={}
banned_ips :Dict [str ,float ]={}


auth_requests_window :deque =deque ()

def can_make_auth_request ()->bool :
    now =time .time ()

    while auth_requests_window and auth_requests_window [0 ]<now -1.0 :
        auth_requests_window .popleft ()


    if len (auth_requests_window )>=10 :
        return False 

    auth_requests_window .append (now )
    return True 

@app .get ("/")
def read_root ():
    return {"status":"AudioStrike Server is Running!"}



active_connections :Dict [str ,Dict [WebSocket ,str ]]={}


user_songs :Dict [str ,str ]={}


dirty_servers :set =set ()

class ConnectionManager :
    def __init__ (self ):
        pass 

    def mark_dirty (self ,server_ip :str ):
        dirty_servers .add (server_ip )

    async def connect (self ,websocket :WebSocket ,server_ip :str ,username :str ):


        pass 

    def disconnect (self ,websocket :WebSocket ,server_ip :str ):
        if server_ip in active_connections :
            username =active_connections [server_ip ].pop (websocket ,None )
            if username and username in user_songs :

                del user_songs [username ]

            global_total =sum (len (clients )for clients in active_connections .values ())
            print (f"[-] Player '{username }' disconnected from server '{server_ip }'. Total players online globally: {global_total }",flush =True )


            if len (active_connections [server_ip ])==0 :
                del active_connections [server_ip ]

    async def broadcast_server_state (self ,server_ip :str ):
        if server_ip not in active_connections :
            return 


        players =[]
        for ws ,uname in active_connections [server_ip ].items ():
            song =user_songs .get (uname ,"")
            likes =0 
            if song :
                if song in song_likes_cache :
                    likes =song_likes_cache [song ]
                elif FIREBASE_URL :

                    try :
                        resp =await asyncio .to_thread (requests .get ,get_firebase_url (f"songs/{safe_key (song )}/likes"),timeout =2 )
                        if resp .status_code ==200 and resp .json ()is not None :
                            likes =int (resp .json ())
                            song_likes_cache [song ]=likes 
                    except Exception :
                        pass 
            players .append ({"username":uname ,"song":song ,"likes":likes })

        message =json .dumps ({"type":"state","players":players })


        for ws in list (active_connections [server_ip ].keys ()):
            try :
                await ws .send_text (message )
            except Exception :

                pass 

manager =ConnectionManager ()

@app .on_event ("startup")
async def startup_event ():
    asyncio .create_task (broadcast_loop ())

async def broadcast_loop ():
    while True :
        await asyncio .sleep (3.0 )
        if not dirty_servers :
            continue 


        servers_to_update =list (dirty_servers )
        dirty_servers .clear ()

        for server_ip in servers_to_update :
            try :
                await manager .broadcast_server_state (server_ip )
            except Exception as e :
                print (f"Broadcast error for {server_ip }: {e }",flush =True )

import re 

def is_valid_song (song :str )->bool :
    if not song :
        return True 
    if len (song )>100 :
        return False 


    bad_patterns =[
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
    song_lower =song .lower ()
    for pattern in bad_patterns :
        if re .search (pattern ,song_lower ):
            return False 
    return True 

@app .websocket ("/ws/{server_ip}/{username}")
async def websocket_endpoint (websocket :WebSocket ,server_ip :str ,username :str ):
    await websocket .accept ()

    forwarded_for =websocket .headers .get ("x-forwarded-for")
    if forwarded_for :
        client_ip =forwarded_for .split (",")[0 ].strip ()
    else :
        client_ip =websocket .client .host if websocket .client else "unknown"

    if client_ip in banned_ips :
        if time .time ()<banned_ips [client_ip ]:
            print (f"[!] Blocked connection from banned IP: {client_ip }",flush =True )
            await websocket .close (code =1008 ,reason ="IP Banned for 18 hours")
            return 
        else :
            del banned_ips [client_ip ]
            if client_ip in ip_strikes :
                del ip_strikes [client_ip ]

    async def fail_auth (code :int ,reason :str ,is_strike :bool =True ):
        print (f"[!] Auth Failed for {username } ({client_ip }): {reason }",flush =True )
        if is_strike and client_ip !="unknown":
            ip_strikes [client_ip ]=ip_strikes .get (client_ip ,0 )+1 
            if ip_strikes [client_ip ]>=3 :
                print (f"[!] IP {client_ip } has been BANNED for 18 hours due to 3 strikes!",flush =True )
                banned_ips [client_ip ]=time .time ()+64800 
        try :
            await websocket .close (code =code ,reason =reason )
        except Exception :
            pass 

    import secrets 
    import asyncio 

    REQUIRE_PREMIUM =os .environ .get ("REQUIRE_PREMIUM","true").lower ()=="true"

    initial_song =None 
    if REQUIRE_PREMIUM :
        await websocket .send_text (json .dumps ({"type":"auth_request"}))

        try :
            while True :
                auth_data_str =await asyncio .wait_for (websocket .receive_text (),timeout =10.0 )
                auth_payload =json .loads (auth_data_str )

                if "song"in auth_payload and "action"not in auth_payload :
                    initial_song =auth_payload ["song"]
                    continue 

                if auth_payload .get ("action")!="auth_complete"or "token"not in auth_payload :
                    await fail_auth (1008 ,"Expected auth_complete with token")
                    return 
                break 

            access_token =auth_payload ["token"]


            if len (access_token )<100 or len (access_token .split ('.'))!=3 :
                await fail_auth (1008 ,"Invalid Token Format")
                return 


            if not can_make_auth_request ():
                await websocket .close (code =1013 ,reason ="Server Busy: Auth Queue Full")
                return 

            profile_url ="https://api.minecraftservices.com/minecraft/profile"
            headers ={"Authorization":f"Bearer {access_token }"}

            print (f"Checking Microsoft Profile API for {username }...",flush =True )
            resp =await asyncio .to_thread (requests .get ,profile_url ,headers =headers )
            print (f"Profile API Response Status: {resp .status_code }",flush =True )

            if resp .status_code !=200 :
                print (f"Profile API Error Body: {resp .text }",flush =True )
                await websocket .send_text (json .dumps ({"type":"like_error","message":"Failed Microsoft Authentication! Premium account required."}))
                await fail_auth (1008 ,"Microsoft Auth Failed")
                return 

            profile_data =resp .json ()
            if profile_data .get ("name","").lower ()!=username .lower ():
                await websocket .send_text (json .dumps ({"type":"like_error","message":"Username spoofing detected!"}))
                await fail_auth (1008 ,"Username Mismatch")
                return 

        except asyncio .TimeoutError :
            await fail_auth (1008 ,"Auth Timeout",is_strike =False )
            return 
        except Exception as e :
            print (f"Auth error: {e }",flush =True )
            await fail_auth (1008 ,"Auth Error",is_strike =False )
            return 






    if server_ip not in active_connections :
        active_connections [server_ip ]={}
    active_connections [server_ip ][websocket ]=username 

    if initial_song is not None and is_valid_song (initial_song ):
        user_songs [username ]=initial_song 


    manager .mark_dirty (server_ip )

    global_total =sum (len (clients )for clients in active_connections .values ())
    print (f"[+] Player '{username }' securely authenticated & connected to server '{server_ip }'. Total players online globally: {global_total }",flush =True )
    try :
        while True :
            data =await websocket .receive_text ()
            try :
                payload =json .loads (data )

                if "song"in payload and "action"not in payload :
                    now =time .time ()
                    last_song_time =song_cooldowns .get (username ,0 )
                    if now -last_song_time <1.0 :
                        continue 
                    song_cooldowns [username ]=now 

                    new_song =payload ["song"]
                    if is_valid_song (new_song ):
                        print (f"[*] Received song update from {username }: {new_song }",flush =True )
                        user_songs [username ]=new_song 

                        manager .mark_dirty (server_ip )

                elif "action"in payload :
                    action =payload ["action"]

                    if action in ["like","unlike"]:
                        now =time .time ()
                        last_action =user_cooldowns .get (username ,0 )
                        if now -last_action <1.0 :
                            await websocket .send_text (json .dumps ({"type":"like_error","message":"Please wait a second before clicking again!"}))
                            continue 
                        user_cooldowns [username ]=now 

                    if action =="like"and "song"in payload :
                        target_song =payload ["song"]
                        print (f"Received like action for '{target_song }' from '{username }'",flush =True )

                        if not FIREBASE_URL :
                            await websocket .send_text (json .dumps ({"type":"like_error","message":"Firebase URL is missing in server secrets!"}))
                            continue 

                        s_key =safe_key (target_song )
                        safe_user =safe_key (username )

                        try :

                            print ("Step 1: Checking if liked",flush =True )
                            check_resp =requests .get (get_firebase_url (f"user_likes/{safe_user }/{s_key }"),timeout =2 )

                            if check_resp .status_code !=200 :
                                print (f"Step 1 Failed: Status {check_resp .status_code }, {check_resp .text }",flush =True )

                                error_msg =str (check_resp .text )
                                await websocket .send_text (json .dumps ({"type":"like_error","message":f"Firebase Error: {error_msg }"}))
                            elif check_resp .json ()is not True :
                                print ("Step 2: User hasn't liked it yet. Putting new like.",flush =True )

                                requests .put (get_firebase_url (f"user_likes/{safe_user }/{s_key }"),json =True )

                                print ("Step 3: Incrementing likes count.",flush =True )

                                current_likes =0 
                                likes_resp =requests .get (get_firebase_url (f"songs/{s_key }/likes"),timeout =2 )
                                if likes_resp .status_code ==200 and likes_resp .json ()is not None :
                                    try :
                                        current_likes =int (likes_resp .json ())
                                    except ValueError :
                                        pass 

                                new_likes =current_likes +1 
                                requests .patch (get_firebase_url (f"songs/{s_key }"),json ={"name":target_song ,"likes":new_likes })
                                song_likes_cache [target_song ]=new_likes 

                                print ("Step 4: Broadcasting server state.",flush =True )

                                await manager .broadcast_server_state (server_ip )

                                print ("Step 5: Sending like_success.",flush =True )

                                await websocket .send_text (json .dumps ({"type":"like_success","song":target_song }))
                            else :
                                print ("Step 2 Alternative: User already liked it.",flush =True )
                                await websocket .send_text (json .dumps ({
                                "type":"like_error",
                                "message":"You already liked this song!",
                                "song":target_song 
                                }))
                            print ("Like flow completed successfully.",flush =True )
                        except Exception as inner_e :
                            print (f"Firebase Exception: {inner_e }",flush =True )
                            await websocket .send_text (json .dumps ({"type":"like_error","message":f"Python Error: {str (inner_e )}"}))

                    elif action =="unlike"and "song"in payload :
                        target_song =payload ["song"]
                        print (f"Received unlike action for '{target_song }' from '{username }'",flush =True )

                        if not FIREBASE_URL :
                            await websocket .send_text (json .dumps ({"type":"like_error","message":"Firebase URL is missing in server secrets!"}))
                            continue 

                        s_key =safe_key (target_song )
                        safe_user =safe_key (username )

                        try :

                            check_resp =requests .get (get_firebase_url (f"user_likes/{safe_user }/{s_key }"),timeout =2 )
                            if check_resp .status_code !=200 :
                                await websocket .send_text (json .dumps ({"type":"like_error","message":"Firebase Error (Unlike)"}))
                                continue 

                            check_data =check_resp .json ()
                            if check_data is not None :

                                requests .delete (get_firebase_url (f"user_likes/{safe_user }/{s_key }"),timeout =2 )


                                likes_resp =requests .get (get_firebase_url (f"songs/{s_key }/likes"),timeout =2 )
                                current_likes =likes_resp .json ()
                                if current_likes and isinstance (current_likes ,int )and current_likes >0 :
                                    requests .put (get_firebase_url (f"songs/{s_key }/likes"),json =current_likes -1 ,timeout =2 )
                                    song_likes_cache [target_song ]=current_likes -1 


                                    if current_likes -1 ==0 :
                                        requests .delete (get_firebase_url (f"songs/{s_key }"),timeout =2 )
                                        song_likes_cache .pop (target_song ,None )

                                await manager .broadcast_server_state (server_ip )
                                await websocket .send_text (json .dumps ({"type":"unlike_success","song":target_song }))
                            else :
                                await websocket .send_text (json .dumps ({"type":"like_error","message":"You haven't liked this song yet!"}))
                        except Exception as inner_e :
                            print (f"Firebase Exception: {inner_e }",flush =True )
                            await websocket .send_text (json .dumps ({"type":"like_error","message":f"Python Error: {str (inner_e )}"}))

                    elif action =="get_top":
                        if not FIREBASE_URL :
                            await websocket .send_text (json .dumps ({"type":"like_error","message":"Firebase URL is missing!"}))
                            continue 


                        resp =requests .get (get_firebase_url ("songs"),timeout =3 )
                        if resp .status_code ==200 and resp .json ():
                            songs_data =resp .json ()
                            songs_list =[]
                            for k ,v in songs_data .items ():
                                if v and "name"in v and "likes"in v :
                                    songs_list .append ({"name":v ["name"],"likes":int (v ["likes"])})


                            songs_list .sort (key =lambda x :x ["likes"],reverse =True )
                            top_10 =songs_list [:10 ]
                            await websocket .send_text (json .dumps ({"type":"top_songs","songs":top_10 }))

            except json .JSONDecodeError :
                pass 
            except Exception as e :
                print (f"WS Error: {e }")
    except WebSocketDisconnect :
        manager .disconnect (websocket ,server_ip )
        manager .mark_dirty (server_ip )
