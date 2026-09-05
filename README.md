# IQRA Audio Player

An Android audio player that plays a directory tree in alphabetical order.

You point it at a folder; it walks that folder and every sub-folder, collects the
audio files, and queues them up as one continuous alphabetical playlist.

## What it does

- **Pick any folder** via the system folder picker. The grant is persisted, so it
  survives reboots and app updates — no `MANAGE_EXTERNAL_STORAGE` needed.
- **Recursive scan.** Every sub-directory is walked; each file's position in the
  queue is decided first by its directory, then by its file name.
- **Natural alphabetical order.** Runs of digits compare as numbers, so
  `track2` comes before `track10`, and `Disc 2` before `Disc 10`. Case is ignored.
- **Now-playing bar** with the track title, its folder, a scrubbable seek bar with
  elapsed/total time, previous / −10s / play-pause / +10s / next.
- **Track list** grouped by folder, with the playing track highlighted; the list
  follows along as the queue advances. Tap any track to jump to it.
- **Background playback** with a system media notification, lock-screen and
  Bluetooth/headset controls, audio focus, and pause-on-unplug.
- **Resumes where you stopped** — the track and position are bookmarked.
- **Rescan** button for when you add files; the current track keeps playing and
  keeps its place in the rebuilt list.

## Build

Requires JDK 17 and an Android SDK with platform 36. `local.properties` points at
the SDK; edit `sdk.dir` if yours lives elsewhere.

```bash
./gradlew :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For the shrunk, signed build (~2 MB instead of ~21 MB):

```bash
./gradlew :app:assembleRelease
```

That is signed with the `mic-tech-upload` key. The keystore and its password live
outside the project (`~/.keystores/`) and are read at build time, so no secret is
ever committed. On another machine, point the build at them with:

```bash
./gradlew :app:assembleRelease -Piqra.keystore=/path/to.keystore -Piqra.keystorePasswordFile=/path/to/pwd.txt -Piqra.keyAlias=mic-tech-upload
```

If the keystore is not found the release APK is left **unsigned** rather than
falling back to the debug key — a debug-signed "release" is exactly the kind of
thing that ships by accident.

Unit tests cover the ordering rules:

```bash
./gradlew :app:testDebugUnitTest
```

## How it fits together

| File | Role |
| --- | --- |
| `MediaScanner.kt` | Walks the picked tree via `DocumentsContract` and returns every audio file. |
| `NaturalOrder.kt` | The digit-aware comparator and the directory-then-name track ordering. |
| `Library.kt` | Owns the root folder, the scanned list (cached to disk), and the resume bookmark. |
| `PlayerService.kt` | `MediaSessionService` hosting ExoPlayer — background playback and notification. |
| `PlayerViewModel.kt` | Connects the UI to the session and keeps the queue in sync with the library. |
| `ui/PlayerScreen.kt` | Compose UI: track list and the now-playing bar. |

## Notes

- MP3 is the target format; `m4a`, `m4b`, `aac`, `flac`, `ogg`, `opus`, `wav`,
  `wma` and `mka` are accepted too, since ExoPlayer handles them anyway.
- Titles come from the file name with its extension stripped, not from ID3 tags.
  For folder-organised libraries the file name is what determines play order, so
  showing anything else would disagree with the list you are looking at.
- Scanning a large tree over the Storage Access Framework is slow, so results are
  cached; use **Rescan** after adding files.
- `minSdk 26` (Android 8.0), `targetSdk 36`.
- Application ID `com.maryumcenter.iqraaudioplayer`.
- The launcher icon is generated from `iqraaudioplayer-logo.svg`, converted to
  VectorDrawables in `res/drawable/`. It is scaled to 60dp inside the 108dp
  adaptive-icon canvas, which keeps every opaque pixel within the 33dp-radius
  safe circle that round launcher masks crop to (measured max radius 32.6dp).
  The themed/monochrome layer drops the gold paths, since Android tints the
  whole drawable one colour and the gold would otherwise flatten the shape into
  a solid blob.
