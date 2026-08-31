# HushTunnel Android Client Guidelines

## 1. Brand Identity
- **Product Name**: Strictly **HushTunnel** (or **Hush Tunnel**), never with "VPN" appended.
- **Application ID**: `com.hushtunnel.app` (namespace `com.v2ray.ang`).

## 2. Localization
- 10 full languages supported in `res/values*/strings.xml` (English, Persian, Russian, Chinese Simplified/Traditional, Turkish, Arabic, Bengali, Vietnamese, Bakhtiari).

## 3. Dynamic Versioning & Build
- `build.gradle.kts` uses Git commit count for automatic minor/patch increments (`1.0.<commit_count>`).
- Output APKs: `HushTunnel_<version>_<abi>.apk`.

## 4. Test Suite Execution
- **Run Android E2E Automated Suite**:
  ```bash
  bash scripts/test-android-e2e.sh
  ```
