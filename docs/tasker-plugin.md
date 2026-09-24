# Automation (Tasker & MacroDroid)

Shevery supports automation through two methods:
1. **Built-in Tasker / Locale Plugin** (`:tasker` module) — works inside Tasker and MacroDroid via plugin interfaces.
2. **Direct Broadcast Intents** — works in MacroDroid, Tasker, Termux, and ADB shell via standard `am broadcast` / "Send Intent" actions.

---

## 1. Supported Commands

| Action | Intent Action | Description |
|---|---|---|
| **Start server** | `com.hamondev.shevery.action.START_SERVER` | Starts the Shizuku server (clears user-stop flag, triggers ADB/Watchdog startup). |
| **Stop server** | `com.hamondev.shevery.action.STOP_SERVER` | Stops the Shizuku server (marks user-initiated stop). |
| **Restart server** | `com.hamondev.shevery.action.RESTART_SERVER` | Stops the server, awaits binder shutdown, then starts it again. |
| **Toggle server** | `com.hamondev.shevery.action.TOGGLE_SERVER` | Checks if running: stops if running, starts if stopped. |

---

## 2. MacroDroid Setup

### Method A: Direct "Send Intent" (Recommended)
1. Add Action → **Applications** → **Send Intent**.
2. Configure:
   - **Target**: `Broadcast Receiver`
   - **Action**: `com.hamondev.shevery.action.START_SERVER` *(or `STOP_SERVER`, `RESTART_SERVER`, `TOGGLE_SERVER`)*
   - **Package**: `com.hamondev.shevery`
   - Leave extras empty.
3. Save the action.

### Method B: Locale / Tasker Plugin
1. Add Action → **Plugins** → **Tasker/Locale** → **Shevery Tasker**.
2. Select your desired action (*Start*, *Stop*, *Restart*, *Toggle*).
3. Tap **Save**.

---

## 3. Tasker Setup

### Task Action (Task → Plugin → Shevery Tasker)
1. In your Task, add an action: **Plugin** → **Shevery Tasker**.
2. Tap the pencil icon to configure, choose the command, and press the back/save button.

### State Condition (Profiles → + → State → Plugin → Shevery Tasker)
* Condition: **Server is running**
* Returns `RESULT_CONDITION_SATISFIED` when Shizuku binder is active, `RESULT_CONDITION_UNSATISFIED` otherwise.

### Direct Broadcast (Task → System → Send Intent)
* **Action**: `com.hamondev.shevery.action.START_SERVER`
* **Package**: `com.hamondev.shevery`
* **Target**: `Broadcast Receiver`

---

## 4. ADB Shell & Termux

You can toggle the server directly from ADB shell or local terminal:

```sh
# Start
adb shell am broadcast -a com.hamondev.shevery.action.START_SERVER -p com.hamondev.shevery

# Stop
adb shell am broadcast -a com.hamondev.shevery.action.STOP_SERVER -p com.hamondev.shevery

# Restart
adb shell am broadcast -a com.hamondev.shevery.action.RESTART_SERVER -p com.hamondev.shevery

# Toggle
adb shell am broadcast -a com.hamondev.shevery.action.TOGGLE_SERVER -p com.hamondev.shevery
```
