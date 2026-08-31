# HushTunnel — Google Play Store Deployment Guide

This repository includes fully automated GitHub Actions CI/CD workflows (`.github/workflows/release.yml`) for building APKs, Play Store App Bundles (`.aab`), and uploading directly to Google Play Console.

---

## 1. Required GitHub Repository Secrets

Go to **GitHub Repo $\rightarrow$ Settings $\rightarrow$ Secrets and variables $\rightarrow$ Actions** and configure the following secrets:

| Secret Name | Description | Where to Obtain |
|---|---|---|
| `APP_KEYSTORE_BASE64` | Base64-encoded Release Keystore (`.jks` file) | `base64 -i V2rayNG/app/keystore/release.jks \| pbcopy` |
| `APP_KEYSTORE_PASSWORD` | Keystore password | Password set for `release.jks` (`hushtunnelpass`) |
| `APP_KEYSTORE_ALIAS` | Key alias | `hushtunnel` |
| `APP_KEY_PASSWORD` | Key password | Password set for key alias (`hushtunnelpass`) |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Google Play Console API Service Account JSON key | Google Play Console $\rightarrow$ API Access $\rightarrow$ Link Google Cloud Project $\rightarrow$ Create Service Account $\rightarrow$ Generate JSON Key |

---

## 2. Triggering Builds and Releases

### A. Automatic Play Store Release via Tag:
```bash
git tag v1.0.0
git push origin v1.0.0
```
*Compiles universal/ARM APKs and Google Play `.aab`, uploads the `.aab` to Google Play Console Internal Track, and creates a GitHub Release with all download assets attached.*

### B. Manual Dispatch:
1. Go to **Actions** tab on GitHub.
2. Select **`Android Build & Play Store Release`**.
3. Click **Run workflow** and set `upload_to_play_store` to `true`.
