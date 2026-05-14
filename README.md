# 🛡️ SafeGuard — AI-Powered Offline Women's Safety App

An AI-powered Android application that automatically detects 
distress and sends emergency alerts — no internet required, 
no manual button press needed.

## 🎯 Problem Solved
Every existing safety app requires manual button press and 
internet. SafeGuard detects danger automatically using 
on-device AI.

## ✨ Key Features
- 🎤 Voice detection — English + Tamil keywords
- 🧠 Emotion detection — Affective Computing (pitch, energy, ZCR)
- 📴 Offline mode — Vosk neural network on-device
- 📳 5 triggers — voice, shake, volume, panic button, secret word
- 📱 Free SMS via SIM — no internet, no paid API
- 🕵️ Stealth Mode — disguised as Calculator
- 🔒 PIN lock + Secret word protection
- 🔁 Auto-restart on reboot

## 🤖 AI Components
| Component | Type | Purpose |
|---|---|---|
| NLP Classifier | Symbolic AI | Distress keyword detection |
| Emotion Detector | Affective Computing | Fear/Panic detection |
| Vosk Engine | Deep Learning | Offline speech recognition |

## 🛠️ Tech Stack
- **Language:** Kotlin
- **Platform:** Android (API 24+)
- **IDE:** Android Studio
- **Libraries:** Vosk Android 0.3.47, Google Play Services Location 21.3.0, Kotlinx Coroutines 1.8.0, Gson 2.10.1

## 📱 Screenshots
*(Add screenshots here)*

## 🚀 How to Run
1. Clone the repository
2. Open in Android Studio
3. Download Vosk model from https://alphacephei.com/vosk/models
4. Place model in app/src/main/assets/model-en/
5. Build and run on Android device (API 24+)

## 📊 Comparison with Existing Apps
| Feature | Kavalan | 112 India | bSafe | SafeGuard |
|---|---|---|---|---|
| Offline AI | ❌ | ❌ | ❌ | ✅ |
| Emotion Detection | ❌ | ❌ | ❌ | ✅ |
| Tamil Voice | ❌ | ❌ | ❌ | ✅ |
| Stealth Mode | ❌ | ❌ | ❌ | ✅ |
| Auto Detection | ❌ | ❌ | ❌ | ✅ |

## 👩‍💻 Developer
**Mariya Bibiyana .G**
Department of Computer Science and Engineering
Mother Teresa College of Engineering and Technology
Anna University — May 2026

## 📄 License
This project is developed for academic purposes.
