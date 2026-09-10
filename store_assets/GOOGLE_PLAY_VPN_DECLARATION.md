# Google Play VpnService declaration — HushTunnel

Use these answers for the Play Console declaration for every active artifact of
`com.hushtunnel.app`. Re-submit the declaration whenever the implementation or
data handling changes.

## Answers

1. **Is providing a VPN the core functionality?** Yes.
2. **Permitted functionality category:** VPN is the app's core functionality.
   Do not select an exception category.
3. **How VpnService is used:** HushTunnel uses Android `VpnService` to create a
   secure device-level tunnel from the user's device to a remote HushTunnel
   endpoint selected by the user. The service owns the virtual interface and
   routes device traffic into the encrypted tunnel. The app does not redirect
   or manipulate traffic for advertising, attribution, affiliate activity, or
   any other monetization purpose.
4. **Encryption:** Yes. VLESS with TLS/REALITY encrypts traffic between the
   Android device and the selected HushTunnel tunnel endpoint. Explain that
   traffic after the endpoint has the encryption supplied by its destination
   protocol, such as HTTPS.
5. **Data collected or shared using VpnService:** No VPN activity data is
   collected into a HushTunnel log or shared. Packets, destination addresses
   and ports, DNS requests, and payload content are processed transiently in
   memory only as needed to route the connection. HushTunnel retains only
   server-side aggregate byte totals for plan quota enforcement; those totals
   cannot reconstruct browsing activity. Account, purchase, and short-lived
   security diagnostics are collected outside VpnService and must be declared
   accurately in the separate Data safety form.
6. **Traffic monetization:** No.
7. **Prominent disclosure:** The versioned, localized disclosure is shown after
   sign-in and before the user can view plans, purchase service, or start the
   VPN. **Agree and continue** records affirmative consent. **Not now** starts
   no tunnel, records no consent, and signs out; signing in again shows the
   disclosure again.

## Required review video

Upload an unlisted video no longer than 90 seconds and paste its direct YouTube
or cloud-storage URL into the declaration. Record a fresh install or a new test
account and show, without cuts:

1. App launch and sign-in.
2. The entire prominent disclosure, including the privacy and terms links.
3. Tap **Not now** and show that the app returns to sign-in without starting a
   tunnel.
4. Sign in again, show the disclosure again, and tap **Agree and continue**.
5. Tap Connect, accept Android's system VPN permission, and show the connected
   state plus persistent foreground notification.

The existing `foreground_service_demo.mp4` is about 147 seconds and predates
the disclosure, so it is not suitable for this declaration.

## Console metadata checklist

- Full description must be replaced with
  `fastlane/metadata/android/en-US/full_description.txt`.
- Privacy policy URL: `https://www.hushtunnel.com/privacy`
- Terms URL (where the console offers one): `https://www.hushtunnel.com/terms`
- Data safety must match the web Privacy Policy. Do not declare browsing
  history, DNS queries, traffic destinations, or payload content as collected.
  Declare account email, user identifiers, purchase history, aggregate service
  usage, and short-lived security diagnostics where the form's definitions
  require them.
