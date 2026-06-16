# Veto 🐾
<img width="1622" height="324" alt="image" src="https://github.com/user-attachments/assets/644e0836-fa96-4746-88ca-fec9bae4fcaa" />

I have a terrible habit of opening Instagram when I know I have hundreds of Anki flashcards piling up. Standard app blockers didn't work for me—they were either too easy to turn off, locked behind premium subscriptions, or relied on arbitrary daily timers that didn't actually motivate me to study.

So, I built Veto. It's a completely free, unapologetically strict app blocker that physically intercepts your doomscrolling and holds your screen time hostage until your flashcards are done. No subscriptions, no cloud syncing, and no loopholes.

Just you, your flashcards, and a very cute (but highly judgmental) cat.

## The Rules of the House

Veto doesn't care how much time you *want* to spend on YouTube. It only cares about how many cards you've reviewed.

* **Anki is the Currency:** Screen time isn't given; it's earned. Every card you review deposits minutes into your "Time Bank."
* **Zero-Friction Local Sync:** Veto hooks directly into your local AnkiDroid database. There are no accounts to create and no APIs to ping. It just works instantly.
* **Deck-Specific Rewards:** You can set custom time payouts for different decks. Make those brutal Kanji cards worth 2 minutes each, while easy vocab gets 30 seconds.
* **Cost Multipliers:** Really want to punish yourself for opening TikTok? Set a 2x cost multiplier so it drains your earned time twice as fast as other apps.
* **Strict Mode:** For when you have absolutely zero self-control. When enabled, your blocked apps are completely inaccessible until your due card count hits exactly **0**. No exceptions.
* **The Bouncer:** Try to open a blocked app when you're out of time, and you'll be greeted by a gorgeous, non-dismissible overlay of Veto the cat politely telling you to get back to work.

### 🛠 Tech Stack & Architecture
* **UI:** 100% Jetpack Compose with a minty-fresh minimalist dashboard.
* **Navigation:** Built with the bleeding-edge **AndroidX Navigation 3** declarative routing (`NavDisplay` and `entryProvider`), utilizing a type-safe `Screen` sealed class backstack.
* **Architecture:** Robust Unidirectional Data Flow pattern. Every screen is backed by a ViewModel exposing a single, consolidated `StateFlow<UiState>` created via `combine()`.
* **Local Data:** Room Database (for app rules and deck profiles) & Jetpack DataStore (for time banking).
* **The Interceptor:** Android AccessibilityService.

<h1></h1>

### 🚀 Getting Started
*(Instructions coming soon once the cat actually lets me finish writing the code.)*

<h1></h1>
~ Made with ♥️ by Harsh
