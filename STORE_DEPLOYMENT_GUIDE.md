# HushTunnel — Google Play Store Deployment Guide

This repository includes fully automated GitHub Actions CI/CD workflows (`.github/workflows/release.yml`) for building APKs, Play Store App Bundles (`.aab`), and uploading directly to Google Play Console.

---

## 1. Authentication model

The release workflow uses Google Cloud Workload Identity Federation (WIF), not
a downloaded service-account JSON key. GitHub receives a short-lived Google
credential only for the duration of a release job. Do not create or store a
`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` secret.

The Google Play service account needs access only to the HushTunnel app and the
**Release apps to testing tracks** permission. Do not grant financial, order,
production, or account-admin permissions.

## 2. Required GitHub Actions configuration

Go to **GitHub repository -> Settings -> Secrets and variables -> Actions**.

### Repository secrets

| Secret Name | Description | Where to Obtain |
|---|---|---|
| `APP_KEYSTORE_BASE64` | Base64-encoded Release Keystore (`.jks` file) | `base64 -i V2rayNG/app/keystore/release.jks \| pbcopy` |
| `APP_KEYSTORE_PASSWORD` | Keystore password | The password used when the upload keystore was created |
| `APP_KEYSTORE_ALIAS` | Key alias | Inspect locally with `keytool -list -keystore V2rayNG/app/keystore/release.jks` |
| `APP_KEY_PASSWORD` | Key password | The password used when the upload key was created |

Never replace the keystore after the first Play upload. Google Play requires all
future updates to use the registered upload certificate.

### Repository variables

| Variable Name | Value |
|---|---|
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | Full provider resource name, e.g. `projects/123456789/locations/global/workloadIdentityPools/github/providers/vpn-android-client` |
| `GCP_PLAY_SERVICE_ACCOUNT` | Service-account email, e.g. `github-play-publisher@PROJECT_ID.iam.gserviceaccount.com` |

## 3. One-time Google configuration

1. Create or select a Google Cloud project.
2. Enable **Google Play Android Developer API** (`androidpublisher.googleapis.com`).
3. Create a service account named `github-play-publisher`. Do not create a JSON
   key for it.
4. In Play Console -> **Users and permissions**, invite the service-account
   email. Grant access only to `com.hushtunnel.app` and only **Release apps to
   testing tracks**.
5. In Google Cloud IAM -> **Workload Identity Federation**, create an OIDC pool
   and provider for GitHub Actions:
   - Issuer: `https://token.actions.githubusercontent.com`
   - Map `google.subject` to `assertion.sub`
   - Map `attribute.repository` to `assertion.repository`
   - Restrict the provider to
     `assertion.repository == 'atasmohammadi/vpn-android-client'`
6. Allow that repository identity to impersonate the service account with the
   **Workload Identity User** role.
7. Save the full provider resource name and service-account email as the two
   GitHub repository variables above.

Google Play's publishing API requires the package to exist first. If no AAB has
ever been uploaded for `com.hushtunnel.app`, build and upload the first signed
AAB manually in Play Console. Keep the same local upload keystore for CI.

---

## 4. Triggering builds and releases

### A. Automatic Play Store Release via Tag:
```bash
git tag v1.0.0
git push origin v1.0.0
```
Compiles universal/ARM APKs and the Play Store `.aab`, publishes the AAB to the
Google Play internal-testing track, and creates a GitHub Release with all
download assets attached.

### B. Manual Dispatch:
1. Go to **Actions** tab on GitHub.
2. Select **`Android Build & Play Store Release`**.
3. Click **Run workflow** and set `upload_to_play_store` to `true`.
4. Use `completed` to make the build available to internal testers immediately,
   or `draft` for a first diagnostic upload that you will review in Play
   Console before rollout.
