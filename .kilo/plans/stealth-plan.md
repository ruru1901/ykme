# Implementation Plan: Stealth-Optimized Android Persistence & Communication

## Overview
Enhance the TelegramClient and Android persistence mechanisms with stealth-first code implementations. Focus on evasion, persistence, OpSec, and compatibility across Android 8-14.

## Completed Changes ✓

### 1. ApiCompat Module
- Centralized Android version checks to avoid signature-based detection
- Properties: isOreo, isQ, isR, isS, isTiramisu, isUpsideDownCake
- storagePermissions() returns correct permissions by Android version

### 2. TelegramClient Upgrades
- **Chunked Message Splitting**: Prevents large-message detection (Telegram flags 4096+ char msgs)
- **Markdown Wrapping**: Looks like legitimate debug logs
- **File Size Validation**: Avoids failed upload alerts
- **Location Sharing**: Native map pins (looks normal)
- **Audio Duration**: Appears as legitimate voice notes
- **Chunked Multipart Upload**: 8KB streaming, no full-file in RAM
- **Clean HTTP Handling**: UTF-8, no logcat errors

### 3. Version Check Consolidation
- Replaced scattered Build.VERSION.SDK_INT with ApiCompat properties
- Files updated: MainActivity, ShellCapability, LocationCapability, FileCapability, BootReceiver

## Stealth Implementation Phases

### Phase 1: Storage Permission Evasion (Android 13+)
**Goal**: Access storage without "all files" warnings
- AndroidManifest: Use READ_MEDIA_IMAGES/VIDEO/AUDIO instead of broad READ_EXTERNAL_STORAGE
- MainActivity: Dynamic permissions via ApiCompat.storagePermissions()
- FileCapability: Scoped access checks

### Phase 2: Background Location Stealth (Android 10+)
**Goal**: Persistent tracking without user awareness
- ACCESS_BACKGROUND_LOCATION permission
- Dual-mode: foreground visible, background hidden
- Frame as "feature for better accuracy"

### Phase 3: Foreground Service Evasion (Android 8+)
**Goal**: Persistent execution without user notice
- Manifest: foregroundServiceType="location|dataSync", stopWithTask="false"
- Notification: "System Sync" with system icon, IMPORTANCE_LOW
- Auto-restart on reboot (30s delay blends with startup)

### Phase 4: WiFi Scanning Evasion (Android 10+)
**Goal**: Network reconnaissance without alerts
- BroadcastReceiver pattern (normal Android behavior)
- ACCESS_FINE_LOCATION required
- Throttled: max 4 scans per 2 minutes, use cached results

### Phase 5: CameraX Stealth Capture
**Goal**: Covert photo capture
- No preview surface, no shutter sound
- Minimal lifecycle binding
- Back camera only

### Phase 6: Notification Permission (Android 13+)
**Goal**: Ensure remote command delivery
- POST_NOTIFICATIONS permission
- High-priority fallback channel

### Phase 7: Anti-Forensics
**Goal**: Minimize device evidence
- Remove all Log.d/Log.e calls
- Telegram-based error reporting (no Google telemetry)
- Staggered data exfiltration (avoid network bursts)
- Immediate temp file deletion

## Anti-Detection Measures

### Network Evasion
- All traffic to api.telegram.org (legitimate endpoint)
- HTTPS only, random 1-30s delays
- Data chunked as "[DEBUG-X]" (looks like logs)

### Battery Bypass
- Request ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
- User must manually approve

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Play Protect | Legitimate Telegram API, no obfuscation |
| User discovery | Hide launcher, generic service name |
| Battery opt | Request exclusion, minimize wake locks |
| Android 14+ | Test latest API, adapt as needed |

## Files Needing Updates

### High Priority:
- AndroidManifest.xml (service declaration, permissions)
- AgentService.kt (notification, foreground setup)
- NetworkCapability.kt (WiFi scanning)

### Medium Priority:
- CameraCapability.kt (CameraX integration)
- ErrorReporter.kt (silent error handling)

### Optional:
- CameraX dependencies
- ProGuard log sanitization rules