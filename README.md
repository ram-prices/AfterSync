# 🧩 AfterSync

Morphe patches for Sync for Reddit — an actively-updated patch set that keeps the
now-abandoned app working: crash fixes for parts of the app that broke after the
developer stopped maintaining it, removal of dead telemetry/ads/purchase-validation
code that depended on servers that may no longer respond, and a fix for
Reddit-hosted images no longer embedding properly in comments and posts.

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=ram-prices/AfterSync

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.21.8](https://github.com/ram-prices/AfterSync/releases/tag/v1.21.8)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;25 patches total
<details open>
<summary>📦 com.laurencedawson.reddit_sync&nbsp;&nbsp;•&nbsp;&nbsp;25 patches</summary>
<br>

**🎯 Supported versions:**

| v23.06.30-13:39 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Center media in posts](#center-media-in-posts) | Centers images, GIFs, and videos embedded in a post's own body horizontally. Media in comments stays left-aligned. |  |
| [Clean up root settings menu](#clean-up-root-settings-menu) | Removes the "New" category ("Developer options", "Legacy settings", and its promotional banner) and moves "Run setup" into the "Other" category in Sync for Reddit's settings. |  |
| [Clean up root settings menu (fix crash)](#clean-up-root-settings-menu-fix-crash) | Removes the dead "Developer options" visibility-check code left behind after removing it from Sync for Reddit's settings menu. |  |
| [Fix blank images in post bodies](#fix-blank-images-in-post-bodies) | Fixes images embedded directly in a post's own body (not a comment) showing as blank boxes in Sync for Reddit, by giving that render path the available-width value it was never being given. |  |
| [Fix comment image margins from missing dimensions](#fix-comment-image-margins-from-missing-dimensions) | Fixes comment-embedded preview.redd.it images being boxed as if they were square (causing large empty margins around non-square photos) by giving them the same real-dimension lookup from Reddit's own metadata that post bodies already get. |  |
| [Fix inline comment image sizing](#fix-inline-comment-image-sizing) | Stops inline images and GIFs in comments/posts from being stretched to a square, caps their size, and gives Giphy embeds a real animated fallback instead of a static link chip when Reddit's own size metadata for them is missing. |  |
| [Fix preview.redd.it comment/post images](#fix-preview-redd-it-comment-post-images) | Fixes comment and post images hosted on preview.redd.it (Reddit's current inline-image hosting) showing as a raw link instead of embedding properly in Sync for Reddit. |  |
| [Move Sync Ultra setting](#move-sync-ultra-setting) | Moves the "Sync Ultra" entry in Sync for Reddit's settings from the "New" category to the top of the "Content" category. |  |
| [Rebrand About screen](#rebrand-about-screen) | Renames "Everything else" to "About", removes "Help and support"/"Rate app!"/the original Credits entries, adds a row for the original app's version, and repurposes two Credits rows and their links for Morphe and this patch repo's GitHub page in Sync for Reddit. |  |
| [Rebrand About screen (resources)](#rebrand-about-screen-resources) | Renames "Everything else" to "About", removes "Help and support"/"Rate app!"/the original Credits entries, adds a row for the original app's version, and repurposes two Credits rows for Morphe and this patch repo's GitHub page in Sync for Reddit. |  |
| [Remove Gestures settings](#remove-gestures-settings) | Removes the "Gestures" section (Swipe to return, Dim behind activity, Swipe to return sensitivity) from Sync for Reddit's General settings screen, including the code that would otherwise crash the settings screen once those preferences no longer exist. |  |
| [Remove Gestures settings (resources)](#remove-gestures-settings-resources) | Removes the "Gestures" section (Swipe to return, Dim behind activity, Swipe to return sensitivity) from Sync for Reddit's General settings screen. |  |
| [Remove Privacy section](#remove-privacy-section) | Removes the "Privacy" entry (privacy policy link, Crashlytics toggle, "Delete Firebase installation ID", "Revoke GDPR consent for ads") from Sync for Reddit's settings, now that telemetry and ads have already been removed. |  |
| [Remove Restore purchases](#remove-restore-purchases) | Removes the "Restore purchases" entry from Sync for Reddit's settings, plus the Sync Ultra screen's "Restore subscription" and dev-only "Reset subscription locally" buttons, now that Ultra and ad removal are unlocked unconditionally and don't depend on this state. |  |
| [Remove Restore purchases (resources)](#remove-restore-purchases-resources) | Removes the "Restore purchases" entry from Sync for Reddit's settings, plus the Sync Ultra screen's "Restore subscription" and dev-only "Reset subscription locally" buttons, now that Ultra and ad removal are unlocked unconditionally and don't depend on this state. |  |
| [Remove Sync Ultra screen](#remove-sync-ultra-screen) | Relocates "Translate text"/"Restore removed comments"/"Paint users"/"Tag users" click behavior onto the Comments settings screen, and strips the now-pointless preference-wiring setup code from the Sync Ultra screen. |  |
| [Remove Sync Ultra screen (resources)](#remove-sync-ultra-screen-resources) | Relocates "Translate text"/"Restore removed comments" into the existing "View tweaks" category and "Paint users"/"Tag users" into the existing "Highlighting" category on the Comments settings screen, then removes everything else on the Sync Ultra screen, including its entry point in the root settings menu. |  |
| [Remove Ultra cloud backup](#remove-ultra-cloud-backup) | Removes the redundant, Firebase-backend-dependent "Cloud backup and restore" section, and the now-misleading "Settings cloud backup" shortcut to it, from Sync for Reddit's settings. |  |
| [Remove Ultra cloud backup (resources)](#remove-ultra-cloud-backup-resources) | Removes the redundant, Firebase-backend-dependent "Cloud backup and restore" section, and the now-misleading "Settings cloud backup" shortcut to it, from Sync for Reddit's settings. |  |
| [Remove Website previews](#remove-website-previews) | Removes the "Website previews" toggle from Sync for Reddit's Sync Ultra screen and disables the underlying feature. |  |
| [Remove Website previews (resources)](#remove-website-previews-resources) | Removes the "Website previews" toggle from Sync for Reddit's Sync Ultra screen and disables the underlying feature. |  |
| [Remove ads](#remove-ads) | Permanently disables ads in Sync for Reddit. |  |
| [Remove telemetry](#remove-telemetry) | Disables Crashlytics crash reporting, Firebase Analytics collection, and a device/account registration call to Google's servers in Sync for Reddit. |  |
| [Remove telemetry (resources)](#remove-telemetry-resources) | Disables Firebase Analytics data collection in Sync for Reddit. |  |
| [Unlock Sync Ultra](#unlock-sync-ultra) | Permanently unlocks Sync Ultra locally, without depending on the abandoned app's validation servers. Also stops the paint/tag cloud-sync jobs, since they'd otherwise keep making network calls to a backend that may no longer respond. |  |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

AfterSync patches are licensed under the [GNU General Public License v3.0](LICENSE)
