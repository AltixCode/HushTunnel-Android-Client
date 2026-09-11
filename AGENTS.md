# HushTunnel Android Client Guidelines

## 1. Brand Identity & Master Guide
- **Product Name**: Strictly **HushTunnel** (or **Hush Tunnel**), never with "VPN" appended.
- **Application ID**: `com.hushtunnel.app` (namespace `com.v2ray.ang`).
- **Master Ecosystem Guide**: See `../AGENTS.md` for overall multi-repo architecture, RevenueCat mappings, and store procedures.
- **Play Store CLI**: `gplay` CLI (`/opt/homebrew/bin/gplay`, based on [play-console-cli](https://github.com/tamtom/play-console-cli)). Authenticated via service account key `/Users/ata/Certificates/play-store-service-account.json` with configuration stored in `/Users/ata/.gplay/config.json`.
- **In-App Subscriptions**: Powered by RevenueCat SDK 10.15.1 (Play flavor). Subscriptions use Google Play base plans: `hushtunnel_1_month:monthly-autorenewing`, `hushtunnel_3_months:three-months-autorenewing`, `hushtunnel_12_months:annual-autorenewing`, and consumable wallet products (`hushtunnel_funds_5` through `hushtunnel_funds_100`).

## 2. Localization
- 10 full languages supported in `res/values*/strings.xml` (English, Persian, Russian, Chinese Simplified/Traditional, Turkish, Arabic, Bengali, Vietnamese, Bakhtiari).

## 3. Dynamic Versioning & Build
- `build.gradle.kts` uses Git commit count for automatic minor/patch increments (`1.0.<commit_count>`).
- Output APKs: `HushTunnel_<version>_<abi>.apk`.

## 4. Test Suite Execution
- **Run Android E2E Automated Suite** (requires a real device/emulator, Android
  Studio's JDK, and the Android command-line tools — not available in every
  environment):
  ```bash
  TEST_EMAIL=qa_e2e_android@hushtunnel.com TEST_PASSWORD=<disposable-test-password> bash scripts/test-android-e2e.sh
  ```
  **Never hardcode a real email/password into this script.** It's committed
  to git — a hardcoded credential leaks into repo history permanently, not
  just the working tree. `TEST_EMAIL`/`TEST_PASSWORD` must always come from
  the environment. (A real personal email + what looked like a real phone
  number were hardcoded here before 2026-08-31 and are already in git
  history — rotate that account's password if it's real.)

## 5. Standing Development Requirements

- **Every user-facing feature needs E2E coverage**, extending
  `scripts/test-android-e2e.sh` where a real device/emulator can verify it
  (UI taps need real screen coordinates — don't guess them without a device
  to check against). If you don't have emulator access, document the gap
  explicitly (see the TODO in that script for the reseller self-service
  flow) rather than skipping it silently.
- **Every user-facing string must exist in all locale directories** under
  `res/values-*/` — currently `fa` (RTL), `ru`, `tr`, `zh-rCN`, `zh-rTW`,
  `ar`, `bn`, `vi`, `bqi-rIR`, plus the English baseline in `res/values/`.
  When adding a new `brand_*` string, add it to every one of these files in
  the same change — a string missing from one locale falls back to English
  for that locale's users, which is an inconsistent, half-finished feature.
- **Role gating and personal-subscription self-service already exist** —
  `AuthStore.getRole()`/`saveSession(token, email, role)`, and
  `ResellerHomeViewModel.buyPersonalSubscription`/`renewPersonalSubscription`
  calling `ApiClient.createSelfSubscription` (mirrors
  `app/actions/reseller.ts`'s `createResellerSelfSubscriptionAction`/
  `renewResellerSelfSubscriptionAction` in the web repo). Don't reintroduce
  a version of this that only lets a reseller manage customers — a reseller
  is also a customer of their own service and needs the same
  buy/renew/connect/disconnect capability regular users get.
- **`ApiClient.request()` must support every HTTP method actually used** —
  `PUT` and `DELETE` were called (`updateCustomerPassword`, `deleteCustomer`)
  before the shared request builder handled anything but `GET`/`POST`,
  which meant those two calls threw at runtime on every invocation. Fixed
  2026-08-31. If you add a call with a new method, extend `request()`'s
  `when` block in the same change — don't assume it's already handled.
