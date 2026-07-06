# AudioStrike for Minecraft

<div align="center">
  <img src="assets/audiostrike_logo.png" alt="AudioStrike Logo" width="200"/>

  [![Discord](https://img.shields.io/discord/1324785461129322588?color=5865F2&logo=discord&logoColor=white&label=Join%20our%20Discord)](https://discord.gg/9eJhKqJKct)
</div>

> ⚠️ **Note:** AudioStrike is currently in **Alpha**! Features are being actively developed and more updates are coming soon. 
> 
> 🧪 **We need testers!** It would be incredibly helpful if you could download the mod, test out the features, and report any bugs or ideas in our Discord!
> 
> 🎮 **Compatible with version 26.1.2**

## ✨ Features

- **Global Media Controls:** An elegant, glassmorphic in-game UI to control Spotify, Chrome, Discord, and other media players active on your PC.
- **Now Playing Nametags:** Seamlessly broadcasts your currently playing song to the server so it floats above your nametag in-game! Includes scrolling animations for long titles.
- **Interactive Spotify Search:** Look at any player and press the **`V`** keybind to instantly open a web search for the exact song they are listening to!
- **Privacy & Security:** Built-in "Privacy Filters" that automatically block YouTube/Twitch streams from being broadcasted, plus a server-side firewall to block in-game spam/advertisements.
- **Custom Kill Sounds:** Automatically plays custom `.wav` sound clips locally whenever you get a kill in-game.
- **Integrated Downloader:** Built-in integration with `spotdl` to download audio from Spotify links directly into your `killsounds` folder.
- **Voice Chat Integration:** Connects with the **Simple Voice Chat** mod (with high-quality, crisp FFmpeg audio processing) to transmit your downloaded sounds directly over your microphone to other players.
- **Audio Cropping Tool:** A built-in UI tool utilizing FFmpeg to precisely crop your downloaded audio clips to get the perfect drop.
- **Customizable Settings:** A dedicated settings menu to toggle animations, adjust nametag lengths, and hide your song if you want to go incognito.
- **Media Companion:** Includes an invisible C# companion app to securely bridge Windows Media APIs with Minecraft in real-time.

## 📸 Screenshots

<p align="center">
  <img src="assets/screenshot1.png" width="45%" />
  <img src="assets/screenshot2.png" width="45%" />
</p>

## 🚀 Installation

1. Download the latest `.jar` from the [Releases](https://github.com/GTsim1/AudioStrike/releases) tab.
2. Drop it into your Minecraft `mods` folder.
3. Ensure you have the [Fabric API](https://modrinth.com/mod/fabric-api) installed.
4. *(Optional)* Install the [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) mod for the microphone transmission features.

## 🛠️ Usage

- Press **`R`** (default) in-game to open the Media Control Screen.
- Use the hamburger menu in the top right to access the **Setup Menu**.
- Paste a Spotify link to download it, and manage your downloaded sounds via the gallery.

> 🛡️ **Antivirus Notice:** Because this mod uses an invisible C# background process (`MediaHelper.exe`) to read your Windows Media APIs, some aggressive antiviruses (like Windows Defender) might flag it as a "false positive". This is completely normal for new, unsigned executables. The code is 100% open-source and viewable in this repository!

## 📄 License
This project is licensed under the MIT License.
