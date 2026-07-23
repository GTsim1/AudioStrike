using System;
using System.IO;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Windows.Media.Control;
using Windows.Storage.Streams;
using System.Drawing;
using System.Drawing.Imaging;
using System.Diagnostics;
using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Runtime.InteropServices;

namespace MediaHelper
{
    
    [ComImport]
    [Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    class MMDeviceEnumerator { }

    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator
    {
        int NotImpl1();
        [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice ppEndpoint);
    }

    [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice
    {
        [PreserveSig] int Activate([MarshalAs(UnmanagedType.LPStruct)] Guid iid, int dwClsCtx, IntPtr pActivationParams, [MarshalAs(UnmanagedType.IUnknown)] out object ppInterface);
    }

    [Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioSessionManager2
    {
        int NotImpl1();
        int NotImpl2();
        [PreserveSig] int GetSessionEnumerator(out IAudioSessionEnumerator SessionEnum);
    }

    [Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioSessionEnumerator
    {
        [PreserveSig] int GetCount(out int SessionCount);
        [PreserveSig] int GetSession(int SessionCount, out IAudioSessionControl2 Session);
    }

    [Guid("bfb7ff88-7239-4fc9-8fa2-07c950be9c6d"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioSessionControl2
    {
        [PreserveSig] int NotImpl0();
        [PreserveSig] int GetDisplayName([MarshalAs(UnmanagedType.LPWStr)] out string pRetVal);
        [PreserveSig] int NotImpl1();
        [PreserveSig] int NotImpl2();
        [PreserveSig] int NotImpl3();
        [PreserveSig] int NotImpl4();
        [PreserveSig] int NotImpl5();
        [PreserveSig] int NotImpl6();
        [PreserveSig] int NotImpl7();
        [PreserveSig] int NotImpl8();
        [PreserveSig] int NotImpl9();
        [PreserveSig] int GetProcessId(out int pRetVal);
    }

    [Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface ISimpleAudioVolume
    {
        [PreserveSig] int SetMasterVolume(float fLevel, ref Guid EventContext);
        [PreserveSig] int GetMasterVolume(out float pfLevel);
        [PreserveSig] int SetMute(bool bMute, ref Guid EventContext);
        [PreserveSig] int GetMute(out bool pbMute);
    }

    class Program
    {
        private static GlobalSystemMediaTransportControlsSessionManager? _manager;
        private static GlobalSystemMediaTransportControlsSession? _currentSession;
        private static string _artworkPath = "";
        private static string? _overrideSessionId = null;
        private static readonly SemaphoreSlim _semaphore = new SemaphoreSlim(1, 1);

        
        private static string _configPath = "";
        private static string _spotifyClientId = "";
        private static string _spotifyClientSecret = "";
        private static string _selectedKillSongUri = "";
        private static string _selectedKillSongTitle = "";
        private static string _accessToken = "";
        private static DateTime _tokenExpiry = DateTime.MinValue;

        static async Task Main(string[] args)
        {
            var standardOutputWriter = new StreamWriter(Console.OpenStandardOutput(), System.Text.Encoding.UTF8) { AutoFlush = true };
            Console.SetOut(standardOutputWriter);
            _artworkPath = Path.Combine(Path.GetTempPath(), "spotify_mod_artwork.png");

            
            string gameDir = args.Length > 0 ? args[0] : AppDomain.CurrentDomain.BaseDirectory;
            string configDir = Path.Combine(gameDir, "config");
            _configPath = Path.Combine(configDir, "spotify_mod.properties");

            InitializeConfig(configDir, _configPath);

            try
            {
                _manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
                
                _manager.CurrentSessionChanged += OnCurrentSessionChanged;
                _manager.SessionsChanged += OnSessionsChanged;

                UpdateCurrentSession(GetActiveSession());

                _ = Task.Run(ReadCommandsLoop);

                while (true)
                {
                    await Task.Delay(1000);
                    _ = ReportStateAsync();
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine(JsonSerializer.Serialize(new { Error = ex.Message }));
            }
        }

        private static void InitializeConfig(string dir, string path)
        {
            try
            {
                if (!Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }
                if (!File.Exists(path))
                {
                    File.WriteAllText(path,
                        "# Spotify Mod Configuration\n" +
                        "# To search Spotify for kill-sounds, register a free application at developer.spotify.com and paste details below:\n" +
                        "spotify_client_id=\n" +
                        "spotify_client_secret=\n" +
                        "selected_kill_song_uri=\n" +
                        "selected_kill_song_title=\n");
                }

                var props = ReadProperties(path);
                props.TryGetValue("spotify_client_id", out _spotifyClientId);
                props.TryGetValue("spotify_client_secret", out _spotifyClientSecret);
                props.TryGetValue("selected_kill_song_uri", out _selectedKillSongUri);
                props.TryGetValue("selected_kill_song_title", out _selectedKillSongTitle);

                if (string.IsNullOrEmpty(_spotifyClientId) || string.IsNullOrEmpty(_spotifyClientSecret))
                {
                    Console.WriteLine(JsonSerializer.Serialize(new { Error = "Spotify API credentials missing or invalid in config!" }));
                }
            }
            catch {}
        }

        private static Dictionary<string, string> ReadProperties(string path)
        {
            var dict = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            if (File.Exists(path))
            {
                foreach (var line in File.ReadAllLines(path))
                {
                    var trimmed = line.Trim();
                    if (trimmed.StartsWith("#") || trimmed.StartsWith(";")) continue;
                    var idx = trimmed.IndexOf('=');
                    if (idx > 0)
                    {
                        var key = trimmed.Substring(0, idx).Trim();
                        var val = trimmed.Substring(idx + 1).Trim();
                        dict[key] = val;
                    }
                }
            }
            return dict;
        }

        private static void WriteProperty(string path, string key, string value)
        {
            try
            {
                var lines = File.Exists(path) ? new List<string>(File.ReadAllLines(path)) : new List<string>();
                bool updated = false;
                for (int i = 0; i < lines.Count; i++)
                {
                    var trimmed = lines[i].Trim();
                    if (trimmed.StartsWith("#") || trimmed.StartsWith(";")) continue;
                    var idx = trimmed.IndexOf('=');
                    if (idx > 0 && trimmed.Substring(0, idx).Trim().Equals(key, StringComparison.OrdinalIgnoreCase))
                    {
                        lines[i] = $"{key}={value}";
                        updated = true;
                        break;
                    }
                }
                if (!updated)
                {
                    lines.Add($"{key}={value}");
                }
                File.WriteAllLines(path, lines);
            }
            catch {}
        }

        private static void OnCurrentSessionChanged(GlobalSystemMediaTransportControlsSessionManager sender, CurrentSessionChangedEventArgs args)
        {
            UpdateCurrentSession(GetActiveSession());
        }

        private static void OnSessionsChanged(GlobalSystemMediaTransportControlsSessionManager sender, SessionsChangedEventArgs args)
        {
            UpdateCurrentSession(GetActiveSession());
        }

        private static GlobalSystemMediaTransportControlsSession? GetActiveSession()
        {
            if (_manager == null) return null;
            
            var sessions = _manager.GetSessions();
            if (sessions == null || sessions.Count == 0) return null;

            if (_overrideSessionId != null)
            {
                foreach (var session in sessions)
                {
                    if (session.SourceAppUserModelId.Equals(_overrideSessionId, StringComparison.OrdinalIgnoreCase))
                    {
                        return session;
                    }
                }
            }
            
            return _manager.GetCurrentSession();
        }

        private static void UpdateCurrentSession(GlobalSystemMediaTransportControlsSession? session)
        {
            if (_currentSession != null)
            {
                _currentSession.MediaPropertiesChanged -= OnMediaPropertiesChanged;
                _currentSession.PlaybackInfoChanged -= OnPlaybackInfoChanged;
                _currentSession.TimelinePropertiesChanged -= OnTimelinePropertiesChanged;
            }

            _currentSession = session;

            if (_currentSession != null)
            {
                _currentSession.MediaPropertiesChanged += OnMediaPropertiesChanged;
                _currentSession.PlaybackInfoChanged += OnPlaybackInfoChanged;
                _currentSession.TimelinePropertiesChanged += OnTimelinePropertiesChanged;
            }

            _ = ReportStateAsync();
        }

        private static void OnMediaPropertiesChanged(GlobalSystemMediaTransportControlsSession sender, MediaPropertiesChangedEventArgs args)
        {
            _ = ReportStateAsync();
        }

        private static void OnPlaybackInfoChanged(GlobalSystemMediaTransportControlsSession sender, PlaybackInfoChangedEventArgs args)
        {
            _ = ReportStateAsync();
        }

        private static void OnTimelinePropertiesChanged(GlobalSystemMediaTransportControlsSession sender, TimelinePropertiesChangedEventArgs args)
        {
            _ = ReportStateAsync();
        }

        private static string GetProcessPath(string aumid)
        {
            try
            {
                string procName = aumid;
                if (procName.Equals("308046B0AF4A39CB", StringComparison.OrdinalIgnoreCase))
                {
                    procName = "firefox";
                }
                
                if (procName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                {
                    procName = procName.Substring(0, procName.Length - 4);
                }
                
                
                var processes = Process.GetProcessesByName(procName);
                if (processes.Length > 0)
                {
                    return processes[0].MainModule?.FileName ?? "";
                }

                
                string lowAumid = aumid.ToLower();
                var allProcs = Process.GetProcesses();
                foreach (var proc in allProcs)
                {
                    try
                      {
                        string name = proc.ProcessName.ToLower();
                        if (name.Length >= 4 && lowAumid.Contains(name))
                        {
                            return proc.MainModule?.FileName ?? "";
                        }
                    }
                    catch {}
                }
            }
            catch {}
            return "";
        }

        private static string ExtractAppIcon(string aumid)
        {
            try
            {
                string path = GetProcessPath(aumid);
                if (string.IsNullOrEmpty(path)) return "";

                string safeName = aumid.Replace(":", "_").Replace("/", "_").Replace("\\", "_");
                string outputPath = Path.Combine(Path.GetTempPath(), $"spotify_mod_icon_{safeName}.png");
                
                if (File.Exists(outputPath)) return outputPath.Replace("\\", "/");

                using (Icon? icon = Icon.ExtractAssociatedIcon(path))
                {
                    if (icon != null)
                    {
                        using (Bitmap bitmap = icon.ToBitmap())
                        {
                            bitmap.Save(outputPath, ImageFormat.Png);
                            return outputPath.Replace("\\", "/");
                        }
                    }
                }
            }
            catch {}
            return "";
        }

        private static async Task<string> GetAccessTokenAsync()
        {
            if (string.IsNullOrEmpty(_spotifyClientId) || string.IsNullOrEmpty(_spotifyClientSecret))
            {
                return "";
            }

            if (!string.IsNullOrEmpty(_accessToken) && DateTime.UtcNow < _tokenExpiry)
            {
                return _accessToken;
            }

            try
            {
                using (var client = new HttpClient())
                {
                    var authHeader = Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes($"{_spotifyClientId}:{_spotifyClientSecret}"));
                    client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Basic", authHeader);

                    var content = new FormUrlEncodedContent(new[]
                    {
                        new KeyValuePair<string, string>("grant_type", "client_credentials")
                    });

                    var response = await client.PostAsync("https://accounts.spotify.com/api/token", content);
                    if (response.IsSuccessStatusCode)
                    {
                        var json = await response.Content.ReadAsStringAsync();
                        using (var doc = JsonDocument.Parse(json))
                        {
                            var root = doc.RootElement;
                            _accessToken = root.GetProperty("access_token").GetString() ?? "";
                            int expiresIn = root.GetProperty("expires_in").GetInt32();
                            _tokenExpiry = DateTime.UtcNow.AddSeconds(expiresIn - 60);
                            return _accessToken;
                        }
                    }
                }
            }
            catch {}
            return "";
        }

        private static async Task PerformSearchAsync(string query)
        {
            string token = await GetAccessTokenAsync();
            if (string.IsNullOrEmpty(token))
            {
                Console.WriteLine(JsonSerializer.Serialize(new { Error = "Spotify API credentials missing or invalid in config!" }));
                return;
            }

            try
            {
                using (var client = new HttpClient())
                {
                    client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
                    var url = $"https://api.spotify.com/v1/search?q={Uri.EscapeDataString(query)}&type=track&limit=5";

                    var response = await client.GetAsync(url);
                    if (response.IsSuccessStatusCode)
                    {
                        var json = await response.Content.ReadAsStringAsync();
                        var results = new List<object>();

                        using (var doc = JsonDocument.Parse(json))
                        {
                            var tracks = doc.RootElement.GetProperty("tracks").GetProperty("items");
                            foreach (var track in tracks.EnumerateArray())
                            {
                                string name = track.GetProperty("name").GetString() ?? "";
                                string uri = track.GetProperty("uri").GetString() ?? "";
                                
                                var artistsList = new List<string>();
                                foreach (var artist in track.GetProperty("artists").EnumerateArray())
                                {
                                    artistsList.Add(artist.GetProperty("name").GetString() ?? "");
                                }
                                string artistNames = string.Join(", ", artistsList);

                                results.Add(new
                                {
                                    Title = name,
                                    Artist = artistNames,
                                    Uri = uri
                                });
                            }
                        }

                        Console.WriteLine(JsonSerializer.Serialize(new { SearchResult = results }));
                    }
                    else
                    {
                        Console.WriteLine(JsonSerializer.Serialize(new { Error = "Search failed: " + response.ReasonPhrase }));
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine(JsonSerializer.Serialize(new { Error = "Search error: " + ex.Message }));
            }
        }

        private static async Task ReportStateAsync()
        {
            await _semaphore.WaitAsync();
            try
            {
                var sessionsList = new List<object>();
                var activeSessions = _manager?.GetSessions();
                if (activeSessions != null)
                {
                    foreach (var s in activeSessions)
                    {
                        string aumid = s.SourceAppUserModelId ?? "";
                        string name = aumid;
                        if (aumid.Equals("308046B0AF4A39CB", StringComparison.OrdinalIgnoreCase))
                        {
                            name = "Firefox";
                        }
                        else
                        {
                            if (name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                            {
                                name = name.Substring(0, name.Length - 4);
                            }
                            if (name.Length > 0)
                            {
                                name = char.ToUpper(name[0]) + name.Substring(1);
                            }
                        }
                        
                        string iconPath = ExtractAppIcon(aumid);
                        
                        sessionsList.Add(new
                        {
                            Id = aumid,
                            Name = name,
                            IconPath = iconPath,
                            IsActive = (aumid.Equals(_currentSession?.SourceAppUserModelId, StringComparison.OrdinalIgnoreCase))
                        });
                    }
                }

                if (_currentSession == null)
                {
                    Console.WriteLine(JsonSerializer.Serialize(new { 
                        HasSession = false, 
                        Sessions = sessionsList,
                        SelectedKillSongUri = _selectedKillSongUri,
                        SelectedKillSongTitle = _selectedKillSongTitle
                    }));
                    return;
                }

                var props = await _currentSession.TryGetMediaPropertiesAsync();
                var playbackInfo = _currentSession.GetPlaybackInfo();
                var timeline = _currentSession.GetTimelineProperties();

                string currentArtwork = "";
                if (props?.Thumbnail != null)
                {
                    try
                    {
                        using (var stream = await props.Thumbnail.OpenReadAsync())
                        {
                            using (var reader = new DataReader(stream.GetInputStreamAt(0)))
                            {
                                await reader.LoadAsync((uint)stream.Size);
                                byte[] bytes = new byte[stream.Size];
                                reader.ReadBytes(bytes);
                                await File.WriteAllBytesAsync(_artworkPath, bytes);
                                currentArtwork = _artworkPath.Replace("\\", "/");
                            }
                        }
                    }
                    catch
                    {
                        
                    }
                }

                var state = new
                {
                    HasSession = true,
                    Title = props?.Title ?? "",
                    Artist = props?.Artist ?? "",
                    Source = _currentSession.SourceAppUserModelId ?? "",
                    IsPlaying = playbackInfo?.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing,
                    Position = timeline?.Position.TotalSeconds ?? 0,
                    Duration = timeline?.EndTime.TotalSeconds ?? 0,
                    PlaybackSpeed = playbackInfo?.PlaybackRate ?? 1.0,
                    TimelineUpdateTime = timeline != null ? timeline.LastUpdatedTime.ToUnixTimeMilliseconds() : DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                    ArtworkPath = currentArtwork,
                    Sessions = sessionsList,
                    SelectedKillSongUri = _selectedKillSongUri,
                    SelectedKillSongTitle = _selectedKillSongTitle
                };

                Console.WriteLine(JsonSerializer.Serialize(state));
            }
            catch (Exception ex)
            {
                Console.WriteLine(JsonSerializer.Serialize(new { Error = ex.Message }));
            }
            finally
            {
                _semaphore.Release();
            }
        }
        private static void SetProcessVolume(string processName, float volume)
        {
            try
            {
                volume = Math.Clamp(volume, 0f, 1f);
                var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumerator();
                if (enumerator == null) return;
                
                enumerator.GetDefaultAudioEndpoint(0, 1, out IMMDevice device);
                if (device == null) return;
                
                Guid IID_IAudioSessionManager2 = new Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F");
                device.Activate(IID_IAudioSessionManager2, 1, IntPtr.Zero, out object managerObj);
                if (managerObj == null) return;
                
                var manager = (IAudioSessionManager2)managerObj;
                if (manager == null) return;

                manager.GetSessionEnumerator(out IAudioSessionEnumerator sessionEnumerator);
                if (sessionEnumerator == null) return;

                sessionEnumerator.GetCount(out int count);

                var targetPids = new List<int>();
                foreach (var p in Process.GetProcessesByName(processName))
                {
                    targetPids.Add(p.Id);
                }

                for (int i = 0; i < count; i++)
                {
                    sessionEnumerator.GetSession(i, out IAudioSessionControl2 session);
                    if (session == null) continue;
                    session.GetProcessId(out int pid);
                    
                    if (targetPids.Contains(pid))
                    {
                        var simpleVolume = session as ISimpleAudioVolume;
                        if (simpleVolume != null)
                        {
                            Guid ctx = Guid.Empty;
                            int res = simpleVolume.SetMasterVolume(volume, ref ctx);
                            Console.WriteLine(JsonSerializer.Serialize(new { Log = $"Set volume for pid {pid} to {volume}, result: {res}" }));
                        }
                        else
                        {
                            Console.WriteLine(JsonSerializer.Serialize(new { Log = $"Failed to get ISimpleAudioVolume for pid {pid}" }));
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine(JsonSerializer.Serialize(new { Log = "Volume error: " + ex.Message }));
            }
        }

        private static async Task ReadCommandsLoop()
        {
            while (true)
            {
                var line = await Console.In.ReadLineAsync();
                if (line == null) break;

                var originalLine = line.Trim();
                line = originalLine.ToLower();

                if (line == "exit")
                {
                    Environment.Exit(0);
                }

                try
                {
                    if (line.StartsWith("switch "))
                    {
                        var parts = originalLine.Split(' ', 2);
                        if (parts.Length > 1)
                        {
                            _overrideSessionId = parts[1];
                            UpdateCurrentSession(GetActiveSession());
                        }
                        continue;
                    }

                    if (line.StartsWith("save_credentials "))
                    {
                        var parts = originalLine.Split(' ', 3);
                        if (parts.Length > 2)
                        {
                            _spotifyClientId = parts[1];
                            _spotifyClientSecret = parts[2];
                            WriteProperty(_configPath, "spotify_client_id", _spotifyClientId);
                            WriteProperty(_configPath, "spotify_client_secret", _spotifyClientSecret);
                            
                            _accessToken = "";
                            _tokenExpiry = DateTime.MinValue;
                            
                            string token = await GetAccessTokenAsync();
                            if (string.IsNullOrEmpty(token))
                            {
                                Console.WriteLine(JsonSerializer.Serialize(new { Error = "Spotify API credentials missing or invalid in config!" }));
                            }
                            else
                            {
                                Console.WriteLine(JsonSerializer.Serialize(new { SearchResult = new List<object>() }));
                            }
                        }
                        continue;
                    }

                    if (line.StartsWith("search "))
                    {
                        var parts = originalLine.Split(' ', 2);
                        if (parts.Length > 1)
                        {
                            _ = PerformSearchAsync(parts[1]);
                        }
                        continue;
                    }

                    if (line.StartsWith("select_kill_song "))
                    {
                        var parts = originalLine.Split(' ', 3);
                        if (parts.Length > 2)
                        {
                            _selectedKillSongUri = parts[1];
                            _selectedKillSongTitle = parts[2];
                            WriteProperty(_configPath, "selected_kill_song_uri", _selectedKillSongUri);
                            WriteProperty(_configPath, "selected_kill_song_title", _selectedKillSongTitle);
                            _ = ReportStateAsync();
                        }
                        continue;
                    }

                    if (line.StartsWith("play_uri "))
                    {
                        var parts = originalLine.Split(' ', 2);
                        if (parts.Length > 1)
                        {
                            string uri = parts[1];
                            Process.Start(new ProcessStartInfo(uri) { UseShellExecute = true });
                        }
                        continue;
                    }

                    if (line.StartsWith("volume "))
                    {
                        var parts = line.Split(' ');
                        if (parts.Length > 1 && float.TryParse(parts[1], System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out float vol))
                        {
                            string procName = "Spotify";
                            if (_currentSession != null && _currentSession.SourceAppUserModelId != null)
                            {
                                string aumid = _currentSession.SourceAppUserModelId;
                                if (aumid.Equals("308046B0AF4A39CB", StringComparison.OrdinalIgnoreCase)) procName = "firefox";
                                else if (aumid.Contains("Spotify", StringComparison.OrdinalIgnoreCase)) procName = "Spotify";
                                else
                                {
                                    string p = aumid;
                                    if (p.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) p = p.Substring(0, p.Length - 4);
                                    procName = p;
                                }
                            }
                            SetProcessVolume(procName, vol);
                        }
                        continue;
                    }

                    if (_currentSession == null) continue;

                    if (line == "play")
                    {
                        await _currentSession.TryPlayAsync();
                    }
                    else if (line == "pause")
                    {
                        await _currentSession.TryPauseAsync();
                    }
                    else if (line == "toggle")
                    {
                        await _currentSession.TryTogglePlayPauseAsync();
                    }
                    else if (line == "repeat")
                    {
                        var info = _currentSession.GetPlaybackInfo();
                        if (info != null && info.AutoRepeatMode.HasValue)
                        {
                            var mode = info.AutoRepeatMode.Value;
                            var newMode = mode == Windows.Media.MediaPlaybackAutoRepeatMode.None ? Windows.Media.MediaPlaybackAutoRepeatMode.Track : Windows.Media.MediaPlaybackAutoRepeatMode.None;
                            await _currentSession.TryChangeAutoRepeatModeAsync(newMode);
                        }
                    }
                    else if (line == "next")
                    {
                        await _currentSession.TrySkipNextAsync();
                    }
                    else if (line == "prev")
                    {
                        await _currentSession.TrySkipPreviousAsync();
                    }
                    else if (line.StartsWith("seek "))
                    {
                        var parts = line.Split(' ');
                        if (parts.Length > 1 && double.TryParse(parts[1], out double seconds))
                        {
                            await _currentSession.TryChangePlaybackPositionAsync(TimeSpan.FromSeconds(seconds).Ticks);
                        }
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine(JsonSerializer.Serialize(new { Log = "Command failed: " + ex.Message }));
                }
            }
        }
    }
}

