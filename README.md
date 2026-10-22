# Nezumi Patches

Community patches for Zalo, compatible with Morphe.

## ❓ About

This repository contains Zalo patches for chat heads, chat reactions and optional privacy controls for delivery receipts, read receipts and typing indicators. Compatibility is limited to the app versions declared by each patch.

### How to use these patches

Add this source in Morphe: https://morphe.software/add-source?github=Nezumi-2711/morphe-patches

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.2.0](https://github.com/Nezumi-2711/morphe-patches/releases/tag/v1.2.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;9 patches total
<details open>
<summary>📦 Zalo&nbsp;&nbsp;•&nbsp;&nbsp;9 patches</summary>
<br>

**🎯 Supported versions:**

| 26.08.01 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Chat heads instead of Android bubbles](#chat-heads-instead-of-android-bubbles) | Use Zalo's overlay chat heads rather than native Android bubbles on Android 11 and newer. |  |
| [Chat reaction picker](#chat-reaction-picker) | Allow the enabled chat reaction UI without a matching subscription package. |  |
| [Custom font](#custom-font) | Choose a font in Nezumi's Settings for Zalo's text, chat messages, info card, options, and contacts. |  |
| [Drive backup via microG](#drive-backup-via-microg) | Enable Google Drive photo backup and use microG for Drive accounts and authentication. |  |
| [Hide Media Box in Messages](#hide-media-box-in-messages) | Hide the Media Box row in Messages when enabled in Nezumi Settings. |  |
| [Hide delivery receipts](#hide-delivery-receipts) | Suppress outgoing message delivery ACKs when enabled in Nezumi Settings. |  |
| [Hide read receipts](#hide-read-receipts) | Keep outgoing read receipts private while retaining delivery ACKs when enabled in Nezumi Settings. |  |
| [Hide text typing](#hide-text-typing) | Hide outgoing text typing in direct and group chats when enabled in Nezumi Settings. |  |
| [Native signature fix](#native-signature-fix) | Allow Zalo 26.08.01 ARM64 to start after patching and re-signing. |  |

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
