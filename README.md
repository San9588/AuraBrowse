# AuraBrowse

A small, native Android WebView browser. The first milestone includes a Compose omnibox, multiple in-memory tabs, history persistence, Android DownloadManager integration, and a small URL blocklist.

## Build
Builds are intentionally manual: run the **Android build** GitHub Actions workflow on an ARM64 runner. No local build is required. The app uses the system WebView; it does not bundle Chromium.

Profiles and CDP are planned follow-up milestones. WebView data-directory suffixes must be selected before the first WebView is created, so profile isolation will be implemented with a process-safe profile lifecycle rather than pretending cookies are isolated in one shared WebView process.
