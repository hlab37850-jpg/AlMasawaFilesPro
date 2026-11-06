# ملفات المساوى PRO — Al-Masawa Files PRO

Phase 1 (foundation): project structure, Material 3 theme (light/dark/system, RTL, AR/EN),
real storage dashboard, MediaStore categories, file browser with real operations
(create folder/file, rename, delete, multi-select), quick-access panels (Room), settings (DataStore),
unit tests, GitHub Actions.

## Build
Push to GitHub — the **Android CI** workflow runs unit tests, builds the debug APK and uploads it as an artifact.
Release signing (later phases): set env vars `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Notes
- All-files access (`MANAGE_EXTERNAL_STORAGE`) is requested at first launch (suitable for direct APK distribution).
- Delete is permanent until the Recycle Bin (phase 3).
- Dual Pane, search, archives, viewers: phase 2.
