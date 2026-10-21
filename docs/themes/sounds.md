# The sounds of all the themes

Designed 2026-10-18. Each theme's sound register, coded or to come, laid side by side so that two themes do not sound alike, and to know which sources to add before listening. The flow of a choice (listening board, `theme_sounds.py`) is in `docs/THEMES.md`, "The sounds"; line 12 of a theme's spec, "The sound register", takes its own again from here. A theme being built starts from its line and makes it precise in its spec, the card following what it settles; a register that is not on the card is added there first, beside the others, before its spec.

## What distinguishes two registers

- **The material**: paper, wood, metal, plastic, glass, electronic, or a real degraded sound.
- **The note**: a sound that has a pitch (a chime, a beep) or a noise without a note (a click, a rustle).
- **The attack and the length**: dry and short, or round and resonant.

Two themes can share a material if they differ on another of these three points.

## What each signal asks, whatever the theme

- The step (`step`) comes back in a burst (a slider one glides): the shortest and most discreet of the theme.
- The end of a list (`scroll_end`) arrives without one having wanted it: soft, never a reproach.
- The refuse (`refuse`) is the only negative sound: it is recognised without looking at the screen.
- Enter and back, open and close go in pairs: the same sound, or two related sounds, that go up then down.

## What holds a theme together

Two sounds ring of the same family by their take: the room and its reverberation, the microphone's distance, the background noise, the colour, the length and the way of going out, the volume. Kenney and BigSoundBank each hold together because a single author took and treated everything; it is by mixing sources that a theme comes apart.

- **One main source per theme** (the card's column). A second only comes for a hole, named on the theme's line; Freesound is never the main source. The terminal has two by nature: the beep, synthetic, and the keyboard, recorded.
- **One finish per theme**, written in its `sounds-choice.json` and applied by `theme_sounds.py make` to each of its sounds, whatever its source: a common channel and sampling rate, the bass cut under a same threshold (the room, the handling), the leading silence cut, a maximal length and a same end fade, a same colour (bass and treble), a same small reverberation or none (the swiss stays dry), and the level brought to a same volume measured before the signal's shift, where today's gain applies to the raw sound. Each step is named in the file; the retro and the cosy have only their today's gain, and their files do not change.
- **The theme listened to in one stretch** on the board, its signals chained: a sound that comes from elsewhere is heard at once. `make` writes beside, for each sound, its background noise, its length, its colour and its volume: a measure that shows the gap, the ear deciding.

## The card

| Theme | Material | Note | Attack | Its signature | Main source |
|---|---|---|---|---|---|
| Default | none | | | silent | — |
| Retro (done) | plastic, switches | no | dry | Kenney's clicks | Kenney |
| Cosy (done) | hollow wood, drops | yes | round | round sounds that bounce | Kenney |
| Swiss (done) | office metal | no | very dry | the stamp, the metal drawer, the retractable pen | BigSoundBank, Freesound |
| Ink and wash (done) | stone, wood, water | little | muffled, slow | the brush, a drop, the glass | BigSoundBank, Freesound, Kenney |
| Metro signage (done) | relays, metal | no | crisp | the relay, the thermostat, the key | BigSoundBank, Freesound |
| Cut paper (done) | thick paper | no | crisp | the laid sheet, the pencil sharpener, the stapler | BigSoundBank |
| Architect's blueprint (done) | drawing tools | no | dry, fine | the pencil, the pencil sharpener, the ruler | BigSoundBank, Freesound |
| Notebook | paper, pen | no | soft | the ticking pen, the turned page | BigSoundBank |
| Monochrome / e-ink | none | | | silent | — |
| Terminal | PC speaker, keyboard | yes (a single one) | dry | the square beep at one pitch, the keys of a mechanical keyboard | synthesis and BigSoundBank |
| Instruments | heavy metal, relays | no, except the alarm | heavy, mechanical | the lever switch, the clacking relay, the buzzer | BigSoundBank |
| JRPG menu | electronic | yes, melodic | short | the beep-beep cursor, the rising confirmation, the falling cancel | synthesis |
| 1-bit Mac | real degraded sounds | no | dry | object sounds reduced to 8 bits and 11 kHz, grainy | BigSoundBank, degraded |
| Science-fiction HUD | fine electronics | yes, high | light | fine beeps, sweeps, a scan | Kenney (Sci-fi Sounds) |
| Risograph | printing machine | no | mechanical | the turning drum, the sheet coming out | BigSoundBank |
| Neon arcade | big shiny synth | yes | loud, bright | the coin slid in, an arpeggio, a laser | Kenney (Digital Audio) |
| Playing cards | cardboard, chips | no | crisp | the card laid, slid, the stacked chip | Kenney (Casino Audio) |

## Theme by theme

- **Default**: silent (decided 2026-10-18).
- **Retro**: done (`retro/sounds-choice.json`). Its clicks are those of a plastic object, not a console's: the 8-bit beeps go to the JRPG, the arcade and the Mac, and the retro keeps them. To listen again beside the swiss.
- **Cosy**: done (`cosy/sounds-choice.json`), nothing to change.
- **Swiss**: done (`swiss/sounds-choice.json`).
- **Ink and wash**: done (`ink/sounds-choice.json`).
- **Metro signage**: done (`metro/sounds-choice.json`).
- **Cut paper**: done (`paper/sounds-choice.json`).
- **Architect's blueprint**: done (`blueprint/sounds-choice.json`).
- **Notebook**: a notebook and a pen; the pen that ticks (confirm, tick), the page one turns (enter, back), the spiral, the eraser (refuse). BigSoundBank: the turned page (n° 164, 362, 363, 493), the pencil, the cap, the marker.
- **Monochrome / e-ink**: silent, an e-reader makes no noise (decided 2026-10-21).
- **Terminal**: the beep of a PC's speaker, one pitch, different lengths (refuse = two beeps), and the keys of a mechanical keyboard for the step and the tick. A `synth` recipe in a square wave; BigSoundBank: the keyboards (n° 229, 1724 to 1728…) and the typewriters.
- **Instruments**: a heavy control panel; the lever switch (tick), the relay (confirm, step), the alarm buzzer (refuse). BigSoundBank: the switches, the buzzers (n° 35, 89, 173), the metal binder; few relays (a thermostat, a blinker), a hole for Freesound.
- **JRPG menu**: the convention of role-playing game menus, melodic and short: the cursor (step), a note that rises (confirm, enter), that falls (back, close), a low buzzer (refuse). A `synth` recipe.
- **1-bit Mac**: not synth beeps but real object sounds (a click, a button, a drawer) passed to 8 bits and 11 kHz, grainy; never an imitation of Apple's sounds. A recipe that degrades a BigSoundBank sound.
- **Science-fiction HUD**: fine, high electronics, airy; brief beeps, a sweep that rises (open), that falls (close), a scan (enter). Where to look: Kenney's Sci-fi Sounds and Digital Audio.
- **Risograph**: the machine that prints; the drum turning (enter), the sheet coming out (confirm), the tray one pushes (close). BigSoundBank: the inkjet and laser printers (n° 169, 394, 395), the Dymo (n° 1620, 1621), an old calculator's receipt (n° 3264…).
- **Neon arcade**: the loudest and brightest of all; the coin slid (confirm), an arpeggio that rises (enter), a laser (open). Where to look: Digital Audio (`laser`, `powerUp`), Music Jingles for a bit of arpeggio.
- **Playing cards**: the card laid (confirm, tick), slid (enter, back), the shuffled deck (open), the stacked chip (step). Kenney's Casino Audio; the chips also have BigSoundBank (n° 942 to 945), which stays outside the theme.

## The sources

Added 2026-10-18 (`sound-sources.json`): Kenney's Casino Audio and Sci-fi Sounds; 84 BigSoundBank recordings, chosen by keywords in Saylune's catalogue, outside the voices; 26 Freesound sounds for the holes (brush, seal, ruler, relay, scissors), taken by their public Ogg excerpt, to replace by the original, downloaded by hand, if one of them sounds damaged on the phone. A sound missing from a theme is added to its source's list.

## What stays open
