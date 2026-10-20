# Nezumi Patches

Community patches for Zalo, compatible with Morphe.

## ❓ About

This repository contains Zalo patches for chat heads, chat reactions and optional privacy controls for delivery receipts, read receipts and typing indicators. Compatibility is limited to the app versions declared by each patch.

### How to use these patches

Add this source in Morphe: https://morphe.software/add-source?github=Nezumi-2711/morphe-patches

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.1.0-dev.1](https://github.com/Nezumi-2711/morphe-patches/releases/tag/v1.1.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;8 patches total
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
<summary>📦 Zalo&nbsp;&nbsp;•&nbsp;&nbsp;7 patches</summary>
<br>

**🎯 Supported versions:**

| 26.08.01 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Zalo Drive backup via microG](#zalo-drive-backup-via-microg) | Enable Google Drive photo backup and use microG for Drive accounts and authentication. |  |
| [Zalo chat heads instead of Android bubbles](#zalo-chat-heads-instead-of-android-bubbles) | Use Zalo's overlay chat heads rather than native Android bubbles on Android 11 and newer. |  |
| [Zalo chat reaction picker](#zalo-chat-reaction-picker) | Allow the enabled chat reaction UI without a matching subscription package. |  |
| [Zalo hide delivery receipts](#zalo-hide-delivery-receipts) | Suppress outgoing message delivery ACKs when enabled in Nezumi Settings. |  |
| [Zalo hide read receipts](#zalo-hide-read-receipts) | Keep outgoing read receipts private while retaining delivery ACKs when enabled in Nezumi Settings. |  |
| [Zalo hide text typing](#zalo-hide-text-typing) | Hide outgoing text typing in direct and group chats when enabled in Nezumi Settings. |  |
| [Zalo native signature fix](#zalo-native-signature-fix) | Allow Zalo 26.08.01 ARM64 to start after patching and re-signing. |  |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

Nezumi Patches are licensed under the [GNU General Public License v3.0](LICENSE)
