# Handoff: ShadowLink Android client

You're continuing a white-label Android VPN client forked from
[2dust/v2rayNG](https://github.com/2dust/v2rayNG). Read `README.md` first (build
steps, what's already changed vs. upstream, git remotes). This file is the
**product/feature spec** — everything the app still needs to reach parity with
the web dashboard it's a front-end for.

## The business

ShadowLink sells VLESS-REALITY VPN subscriptions. Backend is a Next.js app,
repo `vpn-billing-dashboard`, deployed at `https://vpn-billing-dashboard.vercel.app`
(no custom domain yet — that's `BrandConfig.API_BASE_URL`, update it when one
exists). It has three kinds of accounts:

- **USER** — buys a subscription, gets a VLESS config, renews it. This is who
  the app currently serves.
- **RESELLER** — deposits cash into a wallet, gets an automatic volume
  discount (5/10/15/20% at $50/$300/$1000/$3000+ balance), creates and manages
  their own customers (who are plain USER accounts with `resellerId` set),
  pays for those customers' subscriptions out of their wallet at their
  discounted rate. **The app currently does nothing for this role** — see
  "Reseller support" below, the single biggest gap.
- **ADMIN** — runs the whole platform from `/admin` on the web. Out of scope
  for this app entirely; if an admin account logs in here it should be
  rejected client-side with a message pointing them at the web dashboard (not
  currently done — see Known bugs).

The web app (source at `/Users/atamohammadi/Dev/vpn` on this machine, same
Next.js repo the mobile API lives in) is the reference for every screen this
app needs. When in doubt about copy, behavior, or edge cases, read the
corresponding web page there before guessing:

| Web page | What it does |
|---|---|
| `app/login/page.tsx`, `components/marketing/login-form.tsx` | Login |
| `app/register/page.tsx`, `components/marketing/register-form.tsx` | Register (`?type=reseller` toggles reseller signup) |
| `app/dashboard/page.tsx` | User overview — all subscriptions as cards + plan picker |
| `components/dashboard/subscription-card.tsx` | Per-subscription card: days left, active/inactive badge, traffic progress bar, QR + copy VLESS/sub-URL, renew button, setup guide |
| `app/dashboard/checkout/[planId]/page.tsx`, `components/dashboard/checkout-form.tsx` | Payment gateway picker |
| `app/dashboard/orders/page.tsx`, `app/dashboard/orders/[id]/page.tsx` | Order history + detail |
| `app/dashboard/renew/[subId]/page.tsx` | Renew: pick a new plan for an *existing* subscription |
| `app/reseller/**` | Reseller portal — overview, customers, orders, subscriptions, deposits |
| `lib/reseller.ts` | Discount tier math — port this logic if you compute prices client-side, or just trust what the API returns |
| `lib/i18n/dictionaries/{en,fa,ru,zh,tr}.ts` | **Already-translated strings** for every user-facing concept (auth, subscription, checkout, orders, renew). Reuse the translations for matching concepts instead of re-translating from scratch — tone/terminology should match the web app since users may use both. |

## Current state of this app (be precise about this — don't assume more works than does)

Package `V2rayNG/app/src/main/java/com/v2ray/ang/ui/brand/`:

- `SplashActivity` → `LoginActivity`/`RegisterActivity` → `HomeActivity`. That's
  the entire nav graph. One screen after login.
- `HomeActivity`/`HomeViewModel`: shows email, a big Connect/Disconnect
  circle, **the first active subscription only** (plan name + expiry date,
  nothing else — no traffic bar, no QR, no VLESS link, no copy button), a
  "Buy a plan" dialog (flat list, no traffic/description shown), and a
  logout button. All strings are hardcoded English inline in the Composable.
- `ApiClient`: wraps `register`, `login`, `me`, `plans`, `checkout`,
  `orderStatus`. No reseller endpoints. No gateway-list endpoint.
- `AuthStore`: persists `token` + `email` only. **Does not persist `role`** —
  login/`me` both return it, it's just discarded.
- `ProvisionHelper`: after login and after `refresh()`, points v2rayNG's one
  hidden subscription at whichever subscription is "first active" and
  re-fetches. This is the mechanism that makes Connect work without the user
  ever seeing a config — keep it, it's correct.
- No order history screen (the `orderStatus` API call exists in `ApiClient`
  but nothing in the UI calls it).
- No localization — every string is an inline English literal.
- No reseller screens at all.

## Known bugs — fix these before adding features on top of them

1. **Renew is broken.** `HomeViewModel.buyPlan(planId)` calls
   `ApiClient.checkout(token, planId, gateway = "MANUAL")` and **never passes
   `subscriptionId`**. Look at `lib/fulfillment.ts` (`fulfillOrder`) in the web
   repo: an order with `subscriptionId` set gets the *existing* subscription's
   expiry extended (same UUID, same config); an order without it **provisions
   a brand-new subscription** (new UUID, new 3x-ui client, new sub-token).
   Today, every purchase through this app is treated as brand-new, so a user
   who already has an active subscription and buys another plan ends up with
   two live subscriptions in 3x-ui and only ever sees the newest one (`refresh()`
   picks `firstOrNull { it.isActive }`) — the old one silently leaks and never
   expires cleanly from the user's perspective. Fix: the "buy" and "renew" UI
   actions must be distinct. Renewing an existing subscription passes its
   `id` as `subscriptionId` to `checkout()`; buying a fresh one omits it.
2. **No role gating.** `ApiClient.login`/`me` both return `role`, and nothing
   checks it. An ADMIN account can log into this consumer app and just sees a
   broken "no subscription" screen. Persist `role` in `AuthStore` and branch
   on it right after login/splash: `USER` → normal home, `RESELLER` → reseller
   home (see below), `ADMIN` → reject with a message ("Use the web dashboard
   at vpn-billing-dashboard.vercel.app to manage the platform") and don't
   store the session.
3. **Multi-subscription is silently dropped.** A user can have several
   subscriptions (e.g. bought two plans, or renewed into a second one during
   the bug above). The web dashboard shows all of them as cards. This app
   shows only `firstOrNull { it.isActive }`. Decide product-wise whether a
   consumer VPN app should even allow multiple simultaneous subscriptions
   (probably not, going forward — but existing data may have some), and at
   minimum let the user see and pick between them if more than one is active,
   rather than one being invisible.

## Feature checklist — parity with the web user/reseller experience

### Auth
- [ ] Persist and act on `role` (see bug #2).
- [ ] Register screen: keep as-is for plain USER signup. Reseller signup is a
  **separate concern** — see "Reseller support."
- [ ] Password reset / forgot-password: **the web app has none either** (only
  admin/reseller can reset a customer's password server-side). Don't build
  this unless the web app gets it first — stay in parity, not ahead of it.

### User home / subscriptions
- [ ] Show **all** subscriptions, not just the first active one (cards, like
  the web dashboard) — plan name, expiry/days-remaining, active/inactive
  badge, traffic used/total with a progress bar (`usedBytes`/`totalBytes`
  already come back from `/api/mobile/me`, just unused in the UI today).
- [ ] Each subscription needs its own Connect target — currently
  `ProvisionHelper` only ever points at one, chosen implicitly. If keeping
  multi-subscription support, the user needs to pick which one is "active" in
  the VPN client; if you decide to restrict this app to one subscription at a
  time going forward, make that a real product decision (flag it to the
  user), not a silent bug.
- [ ] "No active subscription" empty state → plan list, matching
  `app/dashboard/page.tsx`'s `dashboard.overview.noSubTitle`/`noSubSubtitle`.

### Buying / checkout
- [ ] Payment gateway picker. The web checkout page
  (`components/dashboard/checkout-form.tsx`) shows only the gateways that are
  actually configured server-side (Cryptomus / NOWPayments / Revolut Pay /
  Manual-admin-confirmed) — it knows this because the server component calls
  `isCryptomusConfigured()` etc. before rendering. **The mobile API has no
  equivalent endpoint today.** You'll need to add one in the web repo, e.g.:

  ```
  GET /api/mobile/gateways
  → { cryptomus: boolean, nowpayments: boolean, revolut: boolean }
  ```

  (Trivial to add — see `app/dashboard/checkout/[planId]/page.tsx` lines
  27-31 for the exact three functions to call and expose.) Until that
  exists, hardcode to MANUAL only, same as today, but make it an explicit
  "Place order — admin confirms it" choice, not a silent default.
- [ ] After placing an order with a real gateway, the app already opens
  `checkoutUrl` in the browser (correct — that's the payment provider's own
  hosted page). After the user comes back, **poll `/api/mobile/orders/:id`**
  (already wired in `ApiClient.orderStatus`, unused) until `status` flips to
  `PAID`, then call `refresh()`. Right now the app just shows a static
  "awaiting confirmation" toast and relies on manual pull-to-refresh forever.

### Renew
- [ ] Fix bug #1 first. Then: a distinct "Renew" action per subscription card
  (not reusing the generic "buy a plan" dialog) that opens the same plan
  picker but calls `checkout(planId, gateway, subscriptionId = thatSub.id)`.

### Order history
- [ ] A real orders list screen: id/date/plan/amount/gateway/status, tapping
  through to detail. `GET /api/mobile/orders/:id` exists; you'll need to add
  a **list** endpoint too (`GET /api/mobile/orders` — doesn't exist yet,
  trivial mirror of `app/dashboard/orders/page.tsx`'s Prisma query, scoped to
  `session.userId`).

### Config / setup guide
- Intentionally **do not** surface the raw VLESS link or subscription URL to
  the user the way the web dashboard's QR/copy buttons do — the whole point
  of this app is that config is invisible (`ProvisionHelper` handles it).
  Don't regress this by "completing" the subscription card port and
  accidentally adding a copy-VLESS button; that's correct on web (where
  other VPN clients need the raw link) and wrong here (this app *is* the VPN
  client).

### Localization
The web app supports English, Persian (RTL), Russian, Chinese, Turkish via a
cookie-based switcher (`lib/i18n/` in the web repo). This app has zero
localization today. Match the same 5 languages:

- **Mechanism**: use Android's standard per-app language API
  (`AppCompatDelegate.setApplicationLocales()`, androidx
  `core-splashscreen`/`appcompat` backport — works down to API 21 with the
  right dependency), with `res/values/strings.xml` (English baseline) +
  `res/values-fa/`, `values-ru/`, `values-zh/`, `values-tr/`. This is more
  idiomatic Android than a custom cookie/DataStore scheme and gets you system
  share-sheet / voice-assistant locale correctness for free. Store the user's
  explicit in-app choice (don't just rely on system locale — the web app
  lets you pick independent of OS/browser language, match that with an
  in-app language switcher, e.g. in a settings sheet off the home screen).
- **RTL**: Compose mirrors automatically via `LocalLayoutDirection` for
  `fa` — just make sure padding/alignment uses `start`/`end`, never
  `left`/`right`, anywhere you touch existing Composables (the current
  `HomeActivity.kt` already does, since Compose defaults to logical
  properties — don't introduce absolute positioning when adding new screens).
- **Strings**: the app's screens don't map 1:1 to the web's dictionary keys
  (no landing page, no navbar/footer in-app), but the *concepts* do — auth,
  subscription status, traffic usage, checkout gateway labels, order status,
  renew. Read `lib/i18n/dictionaries/en.ts` in the web repo for the full
  English baseline and `fa.ts`/`ru.ts`/`zh.ts`/`tr.ts` for the already-done
  translations of each concept — reuse that phrasing/terminology directly for
  matching strings (e.g. `subscription.daysRemaining`, `subscription.active`,
  `checkout.gateway.cryptomus.label`, `orders.colStatus`) instead of
  translating from scratch, so a bilingual user sees consistent wording
  across web and app.

## Reseller support (biggest gap — not started at all)

Nothing exists for this today: no mobile API endpoints, no app screens.
Building it means work in **both repos**.

### What a reseller needs to do (mirrors `app/reseller/**` + `app/actions/reseller.ts` on web)
1. **Overview**: wallet balance, current discount tier, progress toward next
   tier (`lib/reseller.ts` has the exact math: tiers at $0/$50/$300/$1000/$3000
   balance → 0/5/10/15/20% off).
2. **Customers**: list their own customers (`User` rows with
   `resellerId = me`), create a new customer by email (server generates a
   random password and returns it once — the reseller has to relay it to
   their customer themselves, there's no email delivery), reset a customer's
   password.
3. **Orders**: buy/renew a plan *for a customer*, paid instantly from the
   reseller's wallet at their discounted price (fails with an "insufficient
   balance" error if the wallet's too low) — this calls the same
   provisioning pipeline as a normal order, just funded differently and with
   `paidFromBalance = true`.
4. **Subscriptions**: same extend/toggle/reset-UUID/reset-traffic/revoke
   actions the admin has, but scoped to only the reseller's own customers'
   subscriptions (`assertOwnsSubscription` pattern in `app/actions/reseller.ts`
   — port that ownership check server-side, never trust the client).
5. **Deposits**: add funds to their own wallet via a payment gateway (same
   three gateways as regular checkout) or wait for an admin manual credit.

### New mobile API endpoints to add (in the `vpn-billing-dashboard` repo, mirroring the Server Actions in `app/actions/reseller.ts` — same auth pattern as the existing `/api/mobile/*` routes: `requireMobileUser(request)` then check `session.role === "RESELLER"`)

```
GET  /api/mobile/reseller/overview
  → { balanceUsd, discountPct, nextTier: { minBalance, discountPct } | null }

GET  /api/mobile/reseller/customers
  → { customers: [{ id, email, createdAt }] }
POST /api/mobile/reseller/customers            { email }
  → { id, email, generatedPassword }

GET  /api/mobile/reseller/orders
  → { orders: [{ id, customerEmail, planName, amountUsd, status, createdAt }] }
POST /api/mobile/reseller/orders                { customerEmail, planId }
  → { orderId } | { error }   (mirrors createResellerOrderAction — creates the
                                customer if the email doesn't exist yet)

GET  /api/mobile/reseller/subscriptions
  → { subscriptions: [{ id, customerEmail, planName, expiryDate, isActive, usedBytes, totalBytes }] }
POST /api/mobile/reseller/subscriptions/:id/extend    { days }
POST /api/mobile/reseller/subscriptions/:id/toggle    { enable }
POST /api/mobile/reseller/subscriptions/:id/reset-uuid
POST /api/mobile/reseller/subscriptions/:id/reset-traffic
POST /api/mobile/reseller/subscriptions/:id/revoke

GET  /api/mobile/reseller/deposits
  → { deposits: [{ id, amountUsd, gateway, status, createdAt }] }
POST /api/mobile/reseller/deposits              { amountUsd, gateway }
  → { checkoutUrl } | { error }
```

All of these are thin wrappers — the actual logic (discount math, ownership
checks, wallet debit transaction, provisioning) already exists in
`lib/reseller.ts`, `lib/subscription-management.ts`, and
`app/actions/reseller.ts`. Don't reimplement it; extract/call the same
functions from the new route handlers the way `app/api/mobile/checkout/route.ts`
already calls the shared `lib/order-checkout.ts` instead of duplicating the
web Server Action's logic.

### App-side
- A distinct reseller home (`ResellerHomeActivity`/`ResellerHomeViewModel` or
  similar), reached by branching on `role` right after login/splash (bug #2).
- **Open product question, not yet decided by the product owner**: can a
  reseller also be a customer of their own service (i.e. run the VPN
  themselves through this app)? Today's data model says no — a RESELLER
  account has no `Subscription` rows of its own; only accounts with
  `resellerId` set (their customers) get subscriptions. If the answer ends
  up "yes," that's a backend data-model change (letting a RESELLER buy a
  personal subscription same as a USER), not just an app change — confirm
  with the user before building UI that assumes either answer.

## Constraints (don't relearn these the hard way)

- **No compiler/emulator/Android SDK is available in this environment.**
  Nothing added so far has been build-verified. The first thing to do with
  any new work session is a Gradle sync + build in Android Studio (or CI) and
  fix whatever doesn't match — don't assume prior code compiles cleanly.
- `androidx.compose.material.icons.*` is **not** a dependency in this
  project (checked against `libs.versions.toml`) — use `TextButton`/plain
  `Text` labels instead of `Icon`, or add the dependency deliberately if you
  want icons and verify the version resolves.
- Experimental Compose Material3 APIs (e.g. `PullToRefreshBox`) were avoided
  on purpose given no way to verify they compile against this project's
  Compose BOM version — same caution applies to anything else marked
  `@ExperimentalMaterial3Api` you're tempted to reach for.
- `namespace` in `app/build.gradle.kts` is deliberately still `com.v2ray.ang`
  (not `com.shadowlink.vpn`, which is only the `applicationId`) — every
  unqualified `R.xxx` reference in the huge existing v2rayNG source depends
  on this. Don't "fix" it without repackaging the entire existing codebase.
- MMKV for local persistence (already initialized app-wide in
  `AngApplication.onCreate()`), OkHttp + `org.json` for networking (both
  already dependencies — no Retrofit/Moshi/Gson added, stay consistent or
  have a real reason to introduce a new dependency).
- Two git remotes: `origin` (this fork, where you push) and `upstream`
  (real `2dust/v2rayNG`, for pulling VPN-engine fixes later — expect merge
  conflicts only in `AndroidManifest.xml`, `strings.xml`,
  `app/build.gradle.kts`, the three files this fork touches outside
  `ui/brand/`).

## Suggested order of work

1. Fix bugs #1–#3 (renew, role gating/persistence, multi-subscription
   visibility) — these are correctness bugs in what already exists, not new
   features.
2. Localization infrastructure (locale switcher + resource files) — do this
   before adding more screens so new screens are written against string
   resources from the start, not hardcoded English you'll have to retrofit.
3. Round out the user-facing screens (order history, gateway picker, traffic
   bar, distinct renew flow).
4. Reseller support — backend endpoints first, then the app screens that
   consume them.
