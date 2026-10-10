## 🛡️ Immortality

Take control over death itself.

**Immortality** is a lightweight server-side Fabric mod that lets you decide who lives… and who doesn’t. Grant players the power to survive fatal damage, manage immortality with simple commands, and keep everything persistent across server restarts.

---

## ✨ Features

- **Per-player immortality**
  - Toggle immortality for yourself or any player

- **Mass control**
  - Use selectors like `@a`, `@p`, etc. to affect multiple players at once

- **Global immortality mode**
  - Instantly make *everyone* immortal

- **Totem compatibility**
  - Totems work normally and are never overridden

- **Persistent data**
  - Player immortality is saved and restored automatically

- **Simple & lightweight**
  - No configs required, no client install needed

---

## ⚔️ Commands

- `/immortal`  
  Toggle immortality for yourself  

- `/immortal set <player> <true|false>`  
  Set immortality for one or more players  

- `/immortal get <player>`  
  Check if a player is immortal  

- `/immortal global`  
  Toggle global immortality  

---

## 🧠 How It Works

When a player would normally die, the mod intercepts the event and keeps them alive at **Half a Heart** unless they’re not marked as immortal.

If the player is holding a Totem of Undying, the game handles it normally.

---

## 🔧 Use Cases

- Scripted servers
- God mode for specific players  

---

## 💾 Data Storage

Immortality states are saved in:config/immortality.json

