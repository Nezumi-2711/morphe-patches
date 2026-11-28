# Patches

> Generated from `patches-list.json` — **v1.9.0-dev.1** (`dev`) · **28 patches** across **1 app** · back to [README](README.md)

---

## Zalo (com.zing.zalo)

**Supported versions:** `26.11.01`

| Patch | Details |
|---|---|
| **AMOLED dark theme** | Use true-black backgrounds in Zalo's dark mode, with slightly lifted popups, sheets and chat bubbles, and a dimmed call screen that can be made true black in Nezumi's Settings. |
| **Bug log** | Adds a Debugging section to Nezumi's Settings with a local log the user can export and share with the developer. Errors, crashes and ANRs are always recorded and named by patch; detailed logging and turning the log off completely are options. |
| **Bypass conversation protection** | Unblock screenshots, screen recording, forwarding, copying and saving media in protected conversations when enabled in Nezumi Settings. |
| **Call recording** | Adds a record button to the Zalo 1-1 in-call screen. Recordings are saved as m4a in Music/ZaloRecordings. |
| **Chat heads instead of Android bubbles** | Use Zalo's overlay chat heads rather than native Android bubbles on Android 11 and newer. |
| **Chat reaction picker** | Allow the enabled chat reaction UI without a matching subscription package. |
| **Clone package** | Install an ARM64 Zalo clone alongside the original with a custom package and app name. Separate accounts are required.<br><sub>Options: Clone package name, Clone app name</sub> |
| **Custom font** | Choose a font in Nezumi's Settings for Zalo's text, chat messages, info card, options, and contacts. |
| **Custom sounds** | Choose custom notification and incoming-call sounds in Nezumi's Settings. |
| **Drive backup via microG** | Enable Google Drive photo backup and use microG for Drive accounts and authentication. |
| **Hide Business Box in Messages** | Hide the Business Box row in Messages when enabled in Nezumi Settings. |
| **Hide Discovery tab** | Remove the Discovery tab from Zalo's main navigation. |
| **Hide Media Box in Messages** | Hide the Media Box row in Messages when enabled in Nezumi Settings. |
| **Hide OA accounts in Messages** | Hide OA accounts in Messages and/or mute their notifications. Both options are configurable in Nezumi Settings. |
| **Hide ads in Messages** | Hide Z Instant Ads rows in Zalo's Messages page when enabled in Nezumi Settings. |
| **Hide delivery receipts** | Suppress outgoing message delivery ACKs when enabled in Nezumi Settings. |
| **Hide feed ads** | Remove sponsored and advertising items from Zalo's user feed when enabled in Nezumi Settings. |
| **Hide read receipts** | Keep outgoing read receipts private while retaining delivery ACKs when enabled in Nezumi Settings. Adds a Mark as read button to the chat toolbar for sending a read receipt on demand; the button can be hidden in Nezumi Settings. |
| **Hide text typing** | Hide outgoing text typing in direct and group chats when enabled in Nezumi Settings. |
| **Keep recalled messages** | Keep received messages and locally available photos after another user recalls them, when enabled in Nezumi Settings. |
| **Mark as read from notifications** | Adds Mark as read to chat, reaction, message error and missed Zalo call notifications, and Mark all as read when two or more conversations have notifications. Read receipts follow the Hide read receipts setting. |
| **Native signature fix** | Allow Zalo 26.11.01 ARM64 to start after patching and re-signing. ARMv7 is not supported. |
| **Open links in external browser** | Applies Zalo's "Mở bằng trình duyệt web" choice (Settings &gt; Privacy &gt; Chọn cách mở link) to all links, including link-preview video cards, feed and comment links, group board/My Cloud links and scanned QR links. Zalo's own links (zalo.me) still open in Zalo. You must select "Mở bằng trình duyệt web" in that setting; with the default "Mở bằng Zalo" nothing changes. |
| **Rich text formatting** | Adds a formatting toolbar (Aa) to the message composer and sends bold, italic, underline, strikethrough, size, color, lists and indent as rich text that the Zalo app and Zalo Web display. Copying a formatted message or composer text and pasting it into the composer keeps the formatting. Toggle in Nezumi's Settings. |
| **Save media** | Adds Save to Gallery for profile avatar, profile cover, Story, Zalo Video, feed videos, and post videos and photos. |
| **Send photos in original quality** | Unlocks Zalo's native original-photo quality option and selects it by default while preserving its normal quality picker. |
| **Unlimited quick messages** | Remove the client-side limit on the number of quick messages, ignoring the server-provided cap. |
| **View online status privately** | Hide your activity and show confirmed current-online status in the individual chat header when available. |

---
