# NoPoDial Build Roadmap

**Product:** Android-first calling app with a memory assistant on top.
**Positioning:** Not just a dialer. NoPoDial remembers *who you talked to, what
happened, what you said you'd do, and when to follow up.*
**Team assumption:** solo dev / tiny indie team. Every feature below is scoped
to the smallest version that proves user value.
**Platform:** Android only. iOS is not deferred — it is out of scope by
platform design (no call state, call log, or in-call access for third parties).

---

## 0. The one constraint that shapes everything

**Android will not give you call audio.** Since Android 10, call recording is
blocked for non-system apps. Becoming the default dialer (`InCallService`)
gets you the in-call UI and call control — **not** an audio stream. No
permission, no Play policy exception, no reliable OEM workaround changes this.

So "transcribe what the user said near the end of the call" cannot be built by
passively listening to the call. The premium promise survives by changing the
mechanism, not the value:

> **The Exit Recap:** the moment a call ends, NoPoDial pops a one-tap
> "say it before you forget" prompt. The user speaks a 10–30 second recap
> ("told Dave I'd send the invoice Friday"), it's transcribed **on-device**,
> promises are detected, and reminders are one tap away.

This is honest, buildable, private (audio never leaves the phone), and — used
within seconds of hang-up — captures exactly the end-of-call commitments the
product is about. Everything in the premium tier builds on this flow.

A second consequence: `READ_CALL_LOG` / call-state access is Play-restricted.
The legitimate path to Play distribution is to **become the default Phone app**
(default dialer handler). That's why the dialer surface, which looks cosmetic,
is actually a Phase 1 strategic requirement, not vanity.

---

## 1. Product phases

### Phase 0 — Foundation / MVP: "The call journal" (companion app)

**What gets built**

- Call event detection (`PHONE_STATE` receiver — already proven in this repo)
- Post-call prompt: notification after every call ends → "Add a note?"
- Manual note attached to a call-log entry (who/when prefilled automatically)
- Recents screen: system call log merged with your notes
- Contact view: read system contacts; per-contact interaction timeline
- Simple callback reminder: "remind me to call back" → scheduled notification
- Dial-out via dialpad screen that places calls through an intent
  (`ACTION_CALL`) — the system handles the actual in-call UI

**Why this phase:** It proves the core loop — *call ends → capture → recall
later* — with zero hard tech. No transcription, no billing, no default-dialer
plumbing. If users don't attach notes to calls, nothing downstream matters.

**Prerequisites:** none beyond standard permissions. Reuses the existing
call-state receiver pattern.

**Explicitly waits:** default-dialer/in-call UI, tags, voice anything,
payments, sync, search beyond a basic filter. MVP distribution is sideload /
closed testing track, so the call-log permission fight is deferred too.

### Phase 1 — Post-MVP usability: "A dialer you'd actually switch to"

**What gets built**

- Become an eligible **default Phone app**: `InCallService` with a minimal,
  reliable in-call screen (answer, hang up, mute, speaker, dialpad/DTMF)
- Labels/tags on notes and contacts ("client", "landlord", "follow up")
- Search across notes, names, numbers, and tags (local, SQLite FTS)
- Better post-call prompt: full-screen sheet (not just a notification) when
  the user is the default dialer, with quick-chips ("promised…", "they'll
  send…", "call back ___")
- Reminder management screen (snooze, mark done, see upcoming follow-ups)

**Why this phase:** Default-dialer status unlocks Play-compliant call-log
access, a guaranteed-visible call-end moment (you *own* that screen), and
daily-use retention. Tags and search make the journal useful once it has a few
weeks of data in it.

**Prerequisites:** Phase 0 data model stable; enough real usage to know the
post-call prompt timing/UX works.

**Explicitly waits:** transcription, promise detection, billing. Do not gate
anything yet — you need free users generating habit data.

### Phase 2 — Premium rollout: "The memory assistant"

**What gets built (in this order)**

1. **Exit Recap (voice):** call ends → "hold to recap" → record ≤60s →
   on-device speech-to-text (`SpeechRecognizer` first; whisper.cpp later if
   quality demands) → transcript saved as the call note
2. **Promise detection:** flag likely commitments in recap text ("I'll…",
   "I said I'd…", "by Friday", "next week") — start with rules + date parsing,
   not ML
3. **One-tap reminder creation:** each detected promise renders as a chip →
   tap → scheduled reminder with contact + note context attached
4. **Premium gating + Play Billing:** free = N recaps/month and 90-day
   searchable history; paid = unlimited recaps, unlimited history, promise
   detection, smart follow-ups
5. **Smart follow-up suggestions:** "You said you'd send X to Dave 3 days ago
   — done?" Driven by detected promises with no completed reminder

**Why this phase:** This is the paid value proposition, and it's only worth
building once the free loop proves people capture notes at call-end. Gating
ships *after* the first premium feature works, so you're selling something real.

**Prerequisites:** Phase 1 call-end surface (the recap prompt lives there);
note/reminder data model from Phase 0.

**Explicitly waits:** cloud LLM polish of recaps, multi-device sync, any
server-side anything. v1 of premium is 100% on-device — that's also the
privacy marketing story.

### Phase 3 — Polish / retention

**What gets built**

- Morning digest notification: today's follow-ups + overdue promises
- "Before you call" card: when dialing a known contact, show last note and
  open promises (this is the magic retention moment — cheap to build, only
  needs data you already have)
- Follow-through stats: promises kept vs. pending (lightweight, per-user only)
- Backup/export (local file or user's own Drive — not your server)
- Widget: today's follow-ups
- Quality pass: OEM battery-killer handling (Xiaomi/Samsung reminder
  reliability), accessibility, tablet/foldable layout sanity

**Why this phase:** All of it multiplies value of existing data; none of it
proves anything. Building any of this earlier steals time from the core loop.

---

## 2. Free tier must-haves

| Feature | When | Notes |
|---|---|---|
| Basic dialing (dialpad → place call) | **MVP** | Intent-based; system in-call UI. Own in-call screen is Phase 1. |
| Recent call history | **MVP** | Read system `CallLog`; it's the spine every note hangs on. |
| Manual notes after a call | **MVP** | The product's core loop. Post-call prompt + free-text note. |
| Simple callback reminders | **MVP** | One button on the post-call prompt + `AlarmManager` notification. |
| Saved contacts | **MVP (read-only)** | Read system contacts; do **not** build your own contact store or editor — fast-follow at most. |
| Basic labels/tags | Fast-follow (Phase 1) | Needs a few weeks of real notes to know which presets matter. |
| Search through interactions | Fast-follow (Phase 1) | MVP gets a simple name/number filter; real FTS search comes with tags. |

Rationale: MVP = the five things needed for "call ends → capture → recall."
Tags and search organize volume the user doesn't have on day one.

## 3. Paid tier must-haves

Launch order within premium:

1. **Exit Recap + on-device transcription** — first, because it *is* the value
   prop and everything else feeds off its output. (Per the constraint above:
   this is user-dictated recap at hang-up, not passive call capture. Market it
   as "lock in what you said, the second you hang up.")
2. **Promise/plan detection** — second; turns transcripts into actionable
   items. Rules + date parsing first; upgrade extraction quality later.
3. **One-tap reminder from detected items** — ships with #2; it's the payoff
   tap that makes detection feel magical.
4. **Unlimited searchable notes/history** — the gating lever, not a feature
   build: free tier gets a generous cap (e.g., 90 days / N recaps a month),
   premium removes it. Cheap to implement, classic upgrade trigger.
5. **Smart follow-up suggestions** — last. It needs accumulated promise data
   to be smart, and shipped too early it nags wrongly and erodes trust.

Don't launch the paywall until #1 works well in daily use. Selling a flaky
recap kills the "memory assistant you can trust" positioning permanently.

## 4. Technical sequencing

Build order (each step is shippable before the next starts):

1. **App shell / navigation** — single-activity, 4 destinations: Recents,
   Contacts, Dialpad, Follow-ups. Boring and done in days.
2. **Contacts/history data model** — the load-bearing decision. Local Room/
   SQLite: `Interaction` (keyed to call-log entry + contact), `Note`,
   `Reminder`, later `Promise`. Key by normalized phone number + timestamp so
   notes survive contact edits. Get this right; everything references it.
3. **Call-end detection + post-call note flow** — receiver → prompt → note →
   appears in Recents/contact timeline. This is the MVP heartbeat.
4. **Reminders/follow-up system** — `AlarmManager` exact alarms + notification
   actions (done/snooze). Test on aggressive OEM battery managers early.
5. **Call-end recap UX (no voice yet)** — design the hang-up sheet with text
   quick-chips first. Nail timing and friction *before* audio exists; voice
   slots into a proven surface.
6. **Default dialer / `InCallService`** — the biggest pure-Android lift
   (telecom edge cases: second incoming call, Bluetooth, hold). Do it after
   the journal loop works, before premium, because the recap surface and Play
   compliance both depend on it.
7. **Speech/transcript pipeline** — `SpeechRecognizer` (on-device) for v1;
   evaluate whisper.cpp/Vosk only if accuracy complaints are real. Then
   rule-based promise extraction + date parsing.
8. **Premium gating + Play Billing** — entitlement flag checked at feature
   call-sites and history-cap queries. Add once there's something to sell.
9. **Search/history scaling** — SQLite FTS5 over notes/transcripts. Migrate
   the simple filter to FTS when volume demands; not before.

The ordering principle: data model before features, surfaces before pipelines
(5 before 7), platform plumbing (6) only after the loop is proven, money (8)
only after value exists.

## 5. Dealbreakers / do-not-build-yet

- **Passive call recording/transcription** — not "later," **never** as
  third-party Android. Don't burn weeks on accessibility-service or rooted
  workarounds; they're fragile, policy-violating, and legally fraught
  (two-party consent). The Exit Recap is the product.
- **iPhone support** — out of scope by platform design. Zero thought spent.
- **Cosmetic premium (themes, icons, ringtones)** — dilutes "memory assistant"
  positioning into "skin pack." Premium must equal memory, full stop.
- **Overbuilt analytics** — no funnels/dashboards/A-B infra. One question
  matters early: *do users add a note after a call?* Count locally, move on.
- **Team/business/admin tools, CRM integrations** — different product,
  different buyer, implies servers, sync, auth, and compliance. Revisit only
  if individual users prove out and businesses ask.
- **Cloud sync / multi-device / accounts** — forces backend, auth, and privacy
  policy overhead before product-market fit. On-device + export is enough
  through Phase 3.
- **Cloud LLM summarization of recaps** — adds cost, latency, and a privacy
  asterisk to undercut the on-device story. Rules + on-device STT first;
  revisit when extraction quality is the proven bottleneck.
- **Spam blocking / caller ID lookup** — table-stakes-shaped distraction
  requiring data sources you don't have. Not your differentiator.

## 6. Milestone definitions

### Phase 0 done means
- After any call, a prompt appears within ~2s of hang-up; note save ≤ 2 taps
- Notes show in Recents and per-contact timeline; callback reminders fire
  reliably on a stock Pixel **and** one aggressive OEM (e.g., Samsung)
- **Can fake:** dialer UI (plain dialpad + intent), contact editing (punt to
  system app), search (simple filter), visual polish
- **Must work well:** call-end detection reliability and prompt timing. If
  the prompt is late or missing, the product premise is dead.

### Phase 1 done means
- NoPoDial is settable as default Phone app; in-call screen survives a week of
  daily driving (incoming, outgoing, speaker, Bluetooth, call waiting at
  minimum "don't crash, don't drop")
- Tags + FTS search return any note in <1s; reminder list manageable
- **Can fake:** call-waiting *UI elegance* (correct > pretty), tag management
  (preset chips + freeform, no taxonomy editor)
- **Must work well:** in-call basics. A dialer that mishandles a real call
  loses the user permanently — this gate is absolute.

### Phase 2 done means
- Exit Recap: hang-up → hold-to-talk → transcript saved, fully on-device,
  usable accuracy in a quiet room; obvious promises ("I'll send it Friday")
  become a reminder chip; one tap schedules it with context
- Billing live; free caps enforced; restore-purchase works
- **Can fake:** detection breadth (high-precision rules only — better to miss
  promises than hallucinate them), smart suggestions (start with dumb "open
  promises older than 3 days")
- **Must work well:** the recap capture moment (zero crashes, never lose a
  recording) and billing/entitlement correctness. Losing a user's spoken
  memo is the single worst failure this product can have.

### Phase 3 done means
- Daily digest and "before you call" card live; reminders proven reliable
  across the top 3 OEMs you can test; export produces a re-importable file
- **Can fake:** stats visual richness, widget configurability
- **Must work well:** reminder delivery reliability — a memory assistant that
  forgets is worse than no assistant.

## 7. Priority cheat sheet

**Build now (Phase 0):** call-end detection → note prompt, manual notes on
call-log spine, recents + contact timeline, callback reminders, intent dialing.

**Build next (Phase 1):** default-dialer in-call UI, tags, FTS search,
upgraded hang-up sheet, reminder management.

**Build after (Phase 2–3):** Exit Recap voice capture + on-device STT,
promise detection + one-tap reminders, billing + history caps, smart
follow-ups, digest, "before you call" card, export, widget.

**Do not build yet:** cloud sync/accounts, cloud LLM processing, CRM/team
features, analytics infra, spam/caller-ID, cosmetic premium.

**Do not build ever (as third-party Android):** passive in-call recording or
live call transcription. The Exit Recap *is* the buildable version of that
promise.
