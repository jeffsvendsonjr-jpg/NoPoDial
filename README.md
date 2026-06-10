# NoPoDial

Pocket-dial damage control for Android, with **zero idle battery cost**.

When your phone pocket-dials someone, the awkward part isn't the call — it's
the callback. They see a missed call (or 15 seconds of muffled fabric audio)
and ring you back. NoPoDial closes the loop: the moment a pocket dial ends,
it texts the person you accidentally called:

> *"Sorry — that was an accidental pocket dial. Nothing's wrong, no need to call back!"*

No voicemail tag, no callback, no explaining.

It also covers the more dangerous failure mode: **self-snitching**. Pocket-dial
your boss mid-rant about work, or your partner while you're out doing the thing
you said you weren't doing, and your pocket becomes an open mic happily
incriminating you. NoPoDial can't unsay what your pocket already said, but the
instant apology text stamps the whole thing as an accident *before* they call
back to ask what exactly they just overheard — turning damning muffled audio
into an obvious butt-dial nobody thinks twice about.

## Why it doesn't eat your battery

Most "pocket mode" apps run a foreground service and listen to the proximity
sensor all day. NoPoDial deliberately does the opposite:

1. **It sleeps until a call happens.** The only runtime component is a
   `BroadcastReceiver` for `PHONE_STATE`. Android wakes the app on call state
   transitions; between calls the app isn't even running.
2. **One-shot sensing at dial time.** The instant an outgoing call starts,
   it takes a single proximity reading (then immediately unregisters), and
   checks whether the screen was off and the device was locked.
3. **Decision at hang-up.** When the call ends, if it was short (default
   ≤ 20s, configurable) **and** at least two of the three pocket signals
   agree (screen off / locked / proximity covered), it grabs the dialed
   number from the call log and sends the apology SMS. A notification tells
   you it happened.

The 2-of-3 evidence rule means a deliberate short call ("on my way!") made
with the screen on and unlocked is never flagged, and a single flaky sensor
can't trigger a false apology on its own.

### Safety rails

- **Never texts emergency numbers** (`TelephonyManager.isEmergencyNumber`).
- Deduplicates per call-log entry, so one pocket dial = at most one SMS.
- Master switch is off by default and can't be enabled until permissions
  are granted.

## Permissions (and why)

| Permission | Why |
|---|---|
| `READ_PHONE_STATE` | Wake on call start/end — this is the trigger |
| `READ_CALL_LOG` | Find the number that was just dialed, after hang-up |
| `SEND_SMS` | Send the apology text |
| `POST_NOTIFICATIONS` | Tell you an apology went out (optional) |

## Building

Open the project in Android Studio (Hedgehog or newer) and run, or:

```bash
./gradlew assembleDebug
```

Requires JDK 17 and Android SDK 34. `minSdk` is 26 (Android 8.0).

## Distribution reality check

- **iOS:** not possible. Apple gives third-party apps no access to call
  state, the call log, or programmatic SMS sending. This is Android-only
  by platform design, not by choice.
- **Google Play:** `SEND_SMS` and `READ_CALL_LOG` are restricted
  permissions on the Play Store, normally reserved for default
  dialer/SMS-handler apps. Expect a policy fight or rejection if you submit
  this as-is. It works fine **sideloaded** (and would be at home on F-Droid),
  which is the realistic distribution path for a personal-use tool like this.

## Testing it

1. Install, open, grant permissions, flip the switch on.
2. Lock the phone, screen off.
3. From the lock screen's emergency-free path or via a Bluetooth/assistant
   misfire — or simply dial a friend, immediately turn the screen off and
   cover the proximity sensor — hang up within the threshold.
4. The friend gets the apology text and you get a notification.

For bench testing without burning a friend's patience, dial your own second
SIM / Google Voice number.
