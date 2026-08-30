# ShadowLink Android (v2rayNG fork)

White-label Android client for the ShadowLink VPN billing platform
(`vpn-billing-dashboard`). Forked from [2dust/v2rayNG](https://github.com/2dust/v2rayNG)
(original README preserved at `UPSTREAM_README.md`) — the real VPN engine
(Xray core, tun2socks, VPN service, subscription-import mechanism) is
untouched. What changed is the front door: instead of v2rayNG's own
server-list/settings UI, users only ever see **Login → (order) → Connect**.

## What's different from upstream

All new code lives in one package, `V2rayNG/app/src/main/java/com/v2ray/ang/ui/brand/`:

- `SplashActivity` — now the app's launcher (`AndroidManifest.xml`). Routes to
  `HomeActivity` if a session token is stored, else `LoginActivity`.
- `LoginActivity` / `RegisterActivity` — call the backend's
  `/api/mobile/{login,register}` and store the returned JWT in a private MMKV
  store (`AuthStore.kt`).
- `HomeActivity` — shows subscription status (from `/api/mobile/me`), a single
  Connect/Disconnect button (drives v2rayNG's real `LauncherManager` /
  `VpnService` exactly the way the stock `MainActivity` does), and a plan
  picker that calls `/api/mobile/checkout`. If a real payment gateway is
  configured server-side, the returned checkout URL is opened in the browser
  (that's the payment provider's own hosted page, not our site, so no session
  handoff is needed); otherwise the order just sits pending until an admin
  confirms it in `/admin/orders`.
- `ProvisionHelper` — the actual bridge to v2rayNG's engine. After login (or
  once an order is paid), it points the app's one hidden subscription at
  `https://<backend>/api/sub/<token>` (a standard v2ray subscription feed —
  base64 blob of `vless://` links) and calls v2rayNG's own
  `AngConfigManager.updateConfigViaSubAll()` to fetch, parse, and auto-select
  a server. The user never sees a subscription URL, a server list, or the
  settings/routing screens that ship with upstream v2rayNG — those screens
  still exist in the build (removing them was out of scope for this pass)
  but are no longer reachable: `MainActivity` lost its launcher intent-filter
  and nothing in the new flow links to it.

Nothing under `service/`, `core/`, `handler/` (besides adding the one new
subscription via its existing public API), or the native submodules was
touched.

## Before you build

**I could not compile or run this myself** — no JDK/Android SDK/Gradle/emulator
were available in the environment this was written in. Everything above was
written against the real cloned source (file:line references verified against
`2dust/v2rayNG` at clone time) and follows the project's own
`AGENTS.md` / `V2rayNG/app/src/main/java/com/v2ray/ang/ui/AGENTS.md` conventions,
but **the first thing to do is a Gradle sync + build in Android Studio** and
fix whatever small thing doesn't match (API surface can shift between
upstream versions).

```sh
git submodule update --init --recursive
# AndroidLibXrayLite ships as a prebuilt AAR upstream, not built from source:
# download a libv2ray.aar release matching the AndroidLibXrayLite submodule's
# tag from https://github.com/2dust/AndroidLibXrayLite/releases and place it
# at V2rayNG/app/libs/libv2ray.aar (see .github/workflows/build.yml for the
# exact logic if you want to automate it).
./compile-hevtun.sh   # builds the hev-socks5-tunnel native lib into V2rayNG/app/libs

cd V2rayNG
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assemblePlaystoreDebug
```

## Before you ship

- **`BrandConfig.API_BASE_URL`** (`ui/brand/BrandConfig.kt`) points at
  `https://vpn-billing-dashboard.vercel.app` — the Vercel preview alias, since
  there's no custom domain yet. Vercel deployments can be deleted/recreated;
  move to a real domain before a public release and update this one constant.
- **App icon / adaptive icon** (`@mipmap/ic_launcher`) is still v2rayNG's
  stock icon — swap it for your own before publishing.
- **`applicationId`** was changed to `com.shadowlink.vpn`
  (`app/build.gradle.kts`) so this installs as its own app, side by side with
  real v2rayNG. `namespace` was deliberately left as `com.v2ray.ang` so every
  existing unqualified `R.xxx` reference in the forked source keeps
  resolving — don't change `namespace` without also repackaging every file.
- Upstream `AGENTS.md` recommends tracking `upstream/master` (already set as
  the `upstream` git remote in this repo) to pull VPN-engine fixes later:
  `git fetch upstream && git merge upstream/master` (expect conflicts only in
  `AndroidManifest.xml`, `strings.xml`, and `app/build.gradle.kts` — the three
  files this fork touched outside the new `ui/brand/` package).
- No Play Store listing or signing keystore is set up — see
  `.github/workflows/build.yml` for how upstream signs release builds
  (`APP_KEYSTORE_*` secrets) if you want the same CI flow.

## Backend contract

The five things this app depends on, all in the `vpn-billing-dashboard` repo:

- `POST /api/mobile/register`, `POST /api/mobile/login` — `{email,password}` → `{token,email,role}`
- `GET /api/mobile/me` (Bearer token) → `{email,role,subscriptions:[{id,planName,expiryDate,isActive,usedBytes,totalBytes,subscriptionUrl}]}`
- `GET /api/mobile/plans` → `{plans:[{id,name,description,priceUsd,durationDays,trafficLimitGb}]}`
- `POST /api/mobile/checkout` (Bearer token) `{planId,gateway,subscriptionId?}` → `{orderId,checkoutUrl,gateway}`
- `GET /api/mobile/orders/:id` (Bearer token) → `{id,status,subscriptionId}`
- `GET /api/sub/:token` (no auth — this is the actual VPN subscription feed) → base64 `vless://` blob + `Subscription-Userinfo` header
