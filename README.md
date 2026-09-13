# 👋🧩 Morphe Patches template

Template repository for Morphe Patches.

## ❓ About

Patches for apps I like.

<!-- TODO: Update this about section with a brief introduction/summary about this repo and what it offers. -->

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=ram-prices/sync-patches

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.3.0](https://github.com/ram-prices/sync-patches/releases/tag/v1.3.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;9 patches total
<details open>
<summary>📦 XYZ app&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.0.0 | 1.0.2 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Example Patch](#example-patch) | Example patch to start with. |  |

</details>

<details open>
<summary>📦 com.laurencedawson.reddit_sync&nbsp;&nbsp;•&nbsp;&nbsp;8 patches</summary>
<br>

**🎯 Supported versions:**

| 23.06.30-13:39 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Remove Gestures settings](#remove-gestures-settings) | Removes the "Gestures" section (Swipe to return, Dim behind activity, Swipe to return sensitivity) from Sync for Reddit's General settings screen, including the code that would otherwise crash the settings screen once those preferences no longer exist. |  |
| [Remove Gestures settings (resources)](#remove-gestures-settings-resources) | Removes the "Gestures" section (Swipe to return, Dim behind activity, Swipe to return sensitivity) from Sync for Reddit's General settings screen. |  |
| [Remove Privacy section](#remove-privacy-section) | Removes the "Privacy" entry (privacy policy link, Crashlytics toggle, "Delete Firebase installation ID", "Revoke GDPR consent for ads") from Sync for Reddit's settings, now that telemetry and ads have already been removed. |  |
| [Remove Restore purchases](#remove-restore-purchases) | Removes the "Restore purchases" entry from Sync for Reddit's settings, plus the Sync Ultra screen's "Restore subscription" and dev-only "Reset subscription locally" buttons, now that Ultra and ad removal are unlocked unconditionally and don't depend on this state. |  |
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

UserXYZ Patches are licensed under the [GNU General Public License v3.0](LICENSE)
