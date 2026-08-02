# UI/UX Improvements Walkthrough

## Summary of Changes

### 1. Contextual Action Buttons in Empty States
- **[item_shelf_empty.xml](file:///c:/Projects/echo-nightly/app/src/main/res/layout/item_shelf_empty.xml)**: Redesigned the empty state shelf to feature a prominent icon, bold headline, clear descriptive subtitle, and primary (`MaterialButton.TonalButton`) + secondary (`MaterialButton.OutlinedButton`) contextual CTA buttons.
- **[EmptyAdapter.kt](file:///c:/Projects/echo-nightly/app/src/main/java/dev/brahmkshatriya/echo/ui/feed/EmptyAdapter.kt)**: Upgraded with a flexible `Config` and `ButtonConfig` data model allowing dynamic updating of title, subtitle, icon, and button actions with icons.
- **[FeedAdapter.kt](file:///c:/Projects/echo-nightly/app/src/main/java/dev/brahmkshatriya/echo/ui/feed/FeedAdapter.kt)**: Added an overloaded `withLoading` extension method supporting custom `EmptyAdapter` instances.
- **[LibraryFragment.kt](file:///c:/Projects/echo-nightly/app/src/main/java/dev/brahmkshatriya/echo/ui/library/LibraryFragment.kt)**: Added tailored empty states:
  - *Offline Source*: "Scan Device Folders" CTA (redirects directly to Settings to configure folders).
  - *Online Source*: "Add Extension Source" primary CTA + "Search" secondary CTA.
- **[MediaDetailsFragment.kt](file:///c:/Projects/echo-nightly/app/src/main/java/dev/brahmkshatriya/echo/ui/media/MediaDetailsFragment.kt)**: Contextual empty states for playlists and albums with "Explore Music" and "Search Songs" CTAs.
- **[DownloadFragment.kt](file:///c:/Projects/echo-nightly/app/src/main/java/dev/brahmkshatriya/echo/ui/download/DownloadFragment.kt)**: Contextual empty state with "Explore Music" button.

### 2. Dialog & Bottom Sheet Backdrop Scrim Opacity
- **[styles.xml](file:///c:/Projects/echo-nightly/app/src/main/res/values/styles.xml)**: Softened backdrop dim amount from opaque 95% down to 75% (`android:backgroundDimAmount = 0.75`) across `EchoBottomSheetDialog`, `EchoDialog`, and `EchoAlertDialog`, ensuring users can view and appreciate the ambient background while interacting with sheets and modal dialogs.

### 3. Mini-Player & Bottom Nav Touch Separation & Elevation
- **[dimens.xml](file:///c:/Projects/echo-nightly/app/src/main/res/values/dimens.xml)**: Increased `bottom_player_peek_height` from `136dp` to `144dp`, providing an expanded 16dp vertical clearance between the floating mini-player card and the top of the Bottom Navigation Bar.
- **[UiViewModel.kt](file:///c:/Projects/echo-nightly/app/src/main/java/dev/brahmkshatriya/echo/ui/common/UiViewModel.kt)**:
  - Updated `setPlayerInsets` to include the 16dp clearance buffer, ensuring scrollable views (Home feed, Library, Playlists) scroll completely clear of the floating mini-player dock.
  - Adjusted dynamic peek height calculations in `setupPlayerBehavior` when navigating between full bottom nav screens and detail child fragments.
- **[PlayerFragment.kt](file:///c:/Projects/echo-nightly/app/src/main/java/dev/brahmkshatriya/echo/ui/player/PlayerFragment.kt)**: Elevated the mini-player card outline shadow from `4dp` to `6dp` for a crisp, distinct floating dock appearance that guarantees separate, un-crowded touch targets from the bottom navigation items.

## Verification
- Clean compilation across all modules (`:common` and `:app`) using Java 21 / Kotlin toolchain.
- Debug APK successfully built and installed on the connected Android device.

### 4. TalkBack Accessibility — Dynamic Content Descriptions
- **[strings.xml](file:///c:/Projects/echo-nightly/app/src/main/res/values/strings.xml)**: Added `like`, `liked`, and all previously missing accessibility strings (`pause`, `repeat_off`, `repeat_all`, `repeat_one`, `shuffle_on`, `shuffle_off`, `insert_search`, `delete`, `reorder`, `retry`, `equalizer`, `more_options`).
- **[PlayerFragment.kt](file:///c:/Projects/echo-nightly/app/src/main/java/dev/brahmkshatriya/echo/ui/player/PlayerFragment.kt)**:
  - **Play/Pause**: `contentDescription` dynamically set to `"Pause"` when playing, `"Play"` when paused — applied to both expanded and collapsed player controls.
  - **Shuffle**: `contentDescription` dynamically set to `"Shuffle on"` / `"Shuffle off"` on state change.
  - **Repeat**: `contentDescription` dynamically set to `"Repeat off"` / `"Repeat all"` / `"Repeat one"` on each mode cycle, via `updateRepeatDescription()`.
  - **Like (Heart)**: `contentDescription` dynamically set to `"Like"` / `"Liked"` both on toggle and when loading a new track.
- **Layout files** (item_quick_search_query.xml, item_quick_search_media.xml, item_download.xml, item_playlist_track.xml): Static `contentDescription` attributes added to all icon-only action buttons.

### 5. Secondary Text Contrast Calibration (WCAG AA 4.5:1)
- **[colors.xml](file:///c:/Projects/echo-nightly/app/src/main/res/values/colors.xml)**: Added `@color/secondary_text` (`#556575`) — calibrated for day/light mode.
- **[values-night/colors.xml](file:///c:/Projects/echo-nightly/app/src/main/res/values-night/colors.xml)**: Added `@color/secondary_text` (`#94A4B0`) — calibrated for night/dark mode to meet WCAG AA 4.5:1 contrast ratio on dark backgrounds.
- **14 layout files updated** — `android:alpha` on all secondary subtitle/description/summary `TextView` elements raised from `0.66` → `0.80`:
  - `item_shelf_media.xml`, `item_shelf_video.xml`, `item_shelf_video_horizontal.xml`
  - `item_shelf_media_grid.xml`, `item_shelf_lists_media.xml`, `item_shelf_header.xml`, `item_shelf_category.xml`
  - `item_quick_search_media.xml`, `item_playlist_track.xml`
  - `item_download.xml`, `item_download_task.xml`
  - `preference_common.xml`, `preference_extension_info.xml`
  - `item_login_user.xml`, `item_ruler.xml`
  - `item_player_controls.xml` (total-duration timestamp)
  - `fragment_player_lyrics.xml` (no-lyrics empty state)
  - `fragment_audio_fx.xml` (3× description labels)
  - `dialog_player_sleep_timer.xml`, `dialog_player_audio_fx.xml`, `dialog_extension_installer.xml`
