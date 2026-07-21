# Tablet Production Readiness

This document is a production deployment checklist and runbook for the DeliverMore tablet workflow.

It is based on the current server-side implementation in this repository, including:

- tablet provisioning via QR or asset tag
- tablet pull APIs for pending orders, payload, history, and decisions
- Firebase Cloud Messaging push for new orders and status changes
- dispatch tracking and heartbeat/token registration

Use this before the next production rollout.

## 1. What Is In Scope

The current tablet implementation has these moving parts:

- Provisioning endpoint:
  - `/api/tablet/provision`
  - `/api/provision`
- Tablet order API base:
  - `/api/tablet/orders`
- Tablet auth headers:
  - `X-Tablet-Asset-Tag`
  - `X-Tablet-Api-Key`
- Push types currently sent by the server:
  - `dm_new_order`
  - `dm_order_status_changed`

The tablet flow is hybrid:

- Push wakes or notifies the tablet.
- Tablet then pulls order data from the API.
- Tablet also sends heartbeat and token registration updates.

This means production readiness is not only about Firebase. It also depends on:

- working HTTPS
- correct API key configuration
- correct tablet base URL
- correct proxy routing for `/api/tablet/**`
- working database persistence for tablet assets and dispatch tracking

## 2. Production Environment Variables

These variables are used by the current code and should be explicitly set in production.

### Required tablet and Firebase variables

- `DM_TABLET_API_KEY`
  - Shared API key required by tablet API endpoints.
  - Every tablet request to `/api/tablet/orders/**` must send this in `X-Tablet-Api-Key`.

- `DM_TABLET_BASE_URL`
  - Base URL used in provisioning responses and QR flows.
  - Must be the production public URL the tablet can reach.
  - Example: `https://app.delivermore.ca`

- `DM_TABLET_PUSH_ENABLED`
  - Enables or disables Firebase push sending from the server.
  - Recommended values:
    - `true` for live rollout
    - `false` for emergency kill-switch behavior

- `DM_FIREBASE_PROJECT_ID`
  - Firebase project used by the production server for FCM.

- `DM_FIREBASE_CREDENTIALS_PATH`
  - Absolute filesystem path to the Firebase service account JSON on the production server.
  - The application process user must be able to read this file.

- `DM_TABLET_PUSH_MAX_FAILURES`
  - Controls failure escalation threshold.
  - Start with current default unless you have a reason to change it.

- `DM_TABLET_PUSH_ATTEMPTS_PER_ORDER`
  - Number of push attempts per order.
  - Start with current default unless you have a reason to change it.

- `DM_TABLET_PROVISIONING_EMAIL`
  - Email address used when the admin UI sends provisioning QR packages.

- `DM_TABLET_PROVISIONING_EXPIRY_MINUTES`
  - Expiry duration for QR provisioning nonces.

### Strongly recommended related variables

- `DM_ORDER_SUPPORT_EMAIL`
  - Used for support and fallback communication.

- `DM_SSL_CERT`
- `DM_SSL_CERT_PRIV`
  - Must be correct and valid for production because provisioning and tablet API traffic depend on HTTPS.

## 3. Firebase Production Setup

If Firebase is new to production for this app, treat this as a separate production service rollout.

### Project layout decision

Current decision for this environment:

- reuse the existing single Firebase project: `delivermore-tablet-push`
- do not maintain separate Firebase projects for dev and production at this time

This is a reasonable tradeoff for a small environment, but it changes the operational risks.

### Risks of reusing one Firebase project across dev and production

Using one Firebase project is simpler, but the main risks are:

- accidental cross-environment push delivery
  - a dev server could send a push to a production tablet if it has a valid token for that device
  - a production server could send a push to a dev tablet if that token was registered against the same project and backend data path

- harder incident diagnosis
  - when everything uses one Firebase project, it is less obvious whether a bad token, wrong backend URL, or wrong device registration came from dev or prod usage

- shared token namespace
  - all device registration tokens live under the same Firebase project, so environment separation must be enforced by your backend data and operational process rather than Firebase isolation

- credential blast radius
  - if the single Firebase service account is compromised or misused, both dev and production push traffic are affected

- harder cleanup during testing
  - dev devices that were provisioned or tested carelessly may continue receiving notifications unless their registration tokens are replaced or cleared

- greater reliance on backend correctness
  - because Firebase will not separate environments for you, the correctness of `DM_TABLET_BASE_URL`, tablet registration, asset assignment, and token storage matters more

### Controls required when using one shared Firebase project

If you keep one Firebase project, the important controls are:

- separate backend URLs strictly
  - dev tablets must point to the dev backend
  - production tablets must point to the production backend
  - this matters more than the shared Firebase project itself

- separate server secrets and env values by environment
  - production and dev can both target the same Firebase project id, but each server still needs its own correct environment configuration

- keep device registration environment-specific by backend
  - a tablet only becomes operational where it registers its token and heartbeat
  - do not use the same physical device interchangeably between dev and prod without explicitly reprovisioning and re-registering it

- use a distinct tablet API key for production
  - `DM_TABLET_API_KEY` should be production-only and should not match the dev value

- keep production tablet assets distinct from test assets
  - use clear asset tags and avoid reusing the same tablet asset records for dev experiments

- periodically remove stale or test device registrations
  - especially if the same tablet app build is used in both environments during rollout

- verify push by restaurant and asset, not only by Firebase success
  - Firebase send success only means the message was accepted for a token
  - it does not prove the correct environment or correct tablet received the intended order

### Server-side Firebase setup steps

1. Confirm the shared Firebase project is the intended project for both environments:
  - `delivermore-tablet-push`

2. Enable Firebase Cloud Messaging for that project.

3. Create or confirm a service account that the production server will use.
  Use least privilege where practical, but it must be able to send FCM messages.

4. Decide whether dev and production will share one service account JSON or use separate service account JSON files for the same Firebase project.

Recommended even with one Firebase project:

- one credentials file for dev
- one credentials file for production

This does not create separate Firebase projects. It just limits operational blast radius and makes credential rotation safer.

5. Download the service account JSON used by production.

6. Place the JSON on the production server in a secure path, for example under a protected secrets directory.

7. Set `DM_FIREBASE_CREDENTIALS_PATH` to that exact file path.

8. Set `DM_FIREBASE_PROJECT_ID=delivermore-tablet-push`.

9. Confirm the application process user can read the JSON file.

10. Confirm startup logs show Firebase initialization succeeded.
   The current server logs these states on startup:
   - whether push is enabled
   - whether project id is configured
   - whether credentials path is configured
   - whether Firebase messaging initialized successfully

### Tablet app / device-side Firebase requirements

The server being configured is not enough by itself.

The production tablet app must also:

- use the shared Firebase project `delivermore-tablet-push`
- register a valid FCM token against the production backend
- send that token to `/api/tablet/orders/register-token`
- be able to receive both push types:
  - `dm_new_order`
  - `dm_order_status_changed`

If the tablet app still points at the dev backend or still has stale dev registration state, production will partially work or silently fail even though the Firebase project is shared.

Because one Firebase project is being reused, the backend URL and provisioning/registration flow are what separate environments.

### What the server actually sends

The current server sends these data payloads through FCM:

#### New order push

- `type=dm_new_order`
- `stagedOrderId`
- `restaurantId`
- `restaurantName`
- `assetTag`

#### Status changed push

- `type=dm_order_status_changed`
- `stagedOrderId`
- `restaurantId`
- `restaurantName`
- `assetTag`
- `approvalStatus`
- optional `statusReason`
- optional `statusUpdatedAt`

Before rollout, confirm the tablet build handles both payload types correctly and that the same build can safely register to either dev or prod only through configuration and provisioning.

## 4. Provisioning Flow In Production

The current provisioning flow supports QR or direct asset-tag claim patterns.

### Provisioning endpoints

- `POST /api/tablet/provision`
- `POST /api/tablet/provision/claim`
- `GET /api/tablet/provision`
- `GET /api/tablet/provision/claim`
- alias path also exists under `/api/provision`

### Provisioning inputs accepted

The provisioning controller currently accepts:

- `assetTag`
- or a nonce/code using one of:
  - `nonce`
  - `token`
  - `code`
  - `provisioningNonce`
  - `provisioningCode`

### Production provisioning checks

1. `DM_TABLET_BASE_URL` must be the real production public URL.

2. The reverse proxy must pass provisioning endpoints unchanged.

3. A tablet asset must be assigned to a restaurant before QR provisioning is issued.

4. The QR email path must be able to send mail from production if you plan to use emailed QR packages.

5. The tablet app must trust the production TLS certificate chain.

6. At least one real production tablet should complete the full QR claim flow before general rollout.

## 5. Tablet Order API Requirements

All current tablet order endpoints are under `/api/tablet/orders`.

### Required request headers

Every protected tablet order request must send:

- `X-Tablet-Asset-Tag`
- `X-Tablet-Api-Key`

The server will reject requests if:

- the API key is missing or wrong
- the asset tag is missing
- the asset is unknown
- the asset is archived
- the asset is not assigned to a restaurant

### Current endpoints used by tablets

- `POST /api/tablet/orders/register-token`
- `POST /api/tablet/orders/heartbeat`
- `GET /api/tablet/orders/pending`
- `GET /api/tablet/orders/history`
- `GET /api/tablet/orders/{stagedOrderId}/payload`
- `POST /api/tablet/orders/{stagedOrderId}/ack`
- `POST /api/tablet/orders/{stagedOrderId}/approve`
- `POST /api/tablet/orders/{stagedOrderId}/decline`
- `POST /api/tablet/orders/{stagedOrderId}/cancel`

### Production proxy/network checks

Before rollout, confirm your production proxy or CDN does not:

- cache these API responses
- strip the custom tablet headers
- rewrite these paths
- block POST requests to these routes
- apply aggressive WAF rules that break polling or token registration

## 6. Database and Operational Tracking

The current implementation tracks dispatch state in `tablet_order_dispatch`.

Useful fields for production support include:

- `requested_at`
- `last_attempted_at`
- `push_sent_at`
- `push_failed_at`
- `payload_pulled_at`
- `acknowledged_at`
- `failure_count`
- `last_failure_reason`
- `support_email_notified_at`

This table is one of the main places to inspect when a tablet says:

- no order appeared
- push arrived but payload did not load
- order was seen but not acknowledged

Also verify the `tablet_asset` records are healthy in production:

- assigned to the correct restaurant
- not archived
- provisioned
- have recent heartbeat values
- have an FCM token stored after registration

## 7. Minimum Production Readiness Checklist

Do not roll out until all items below are checked.

### Server and config

- Production jar is built from a clean frontend state.
- Production jar does not contain unwanted devtools/copilot frontend artifacts.
- `DM_TABLET_API_KEY` is set to a production-only secret.
- `DM_TABLET_BASE_URL` points to the production public URL.
- `DM_FIREBASE_PROJECT_ID` is correct.
- `DM_FIREBASE_CREDENTIALS_PATH` points to a readable service account JSON.
- `DM_TABLET_PUSH_ENABLED=true` only when you are ready.
- Mail settings are valid if QR email is used.

### Firebase

- Shared Firebase project `delivermore-tablet-push` is confirmed as the intended project for both environments.
- Production service account JSON is installed on the server.
- Server startup logs confirm Firebase initialized successfully.
- Tablet app uses the shared Firebase project and registers against the correct backend for the current environment.
- Tablet app supports both push payload types.
- Test tablets and production tablets are operationally separated by backend registration, asset assignment, and process.

### API and proxy

- `/api/tablet/provision/**` is reachable externally.
- `/api/tablet/orders/**` is reachable externally.
- Proxy preserves `X-Tablet-Asset-Tag`.
- Proxy preserves `X-Tablet-Api-Key`.
- No API caching is applied.
- TLS certificate is valid on the public hostname.

### Data and assets

- Production tablet assets exist and are assigned to restaurants.
- Restaurants that should receive tablet orders have `sendToTablet=true`.
- Restaurants that should not receive tablet orders remain disabled.
- At least one tablet has been provisioned successfully in production.
- At least one tablet has a stored FCM token.

## 8. Recommended First Production Test

Roll out with one restaurant and one tablet first.

### Test sequence

1. Start the production server and verify startup logs.

2. Provision one tablet in production.

3. Confirm the tablet can:
   - claim provisioning
   - register token
   - send heartbeat

4. Submit one staged order for that restaurant.

5. Confirm:
   - a `dm_new_order` push is sent
   - the tablet can fetch `/pending`
   - the tablet can fetch `/{stagedOrderId}/payload`
   - `payload_pulled_at` is recorded
   - `acknowledged_at` is recorded if the tablet acks

6. Approve from tablet and verify admin state changes.

7. Approve, decline, or cancel from admin and verify the tablet receives `dm_order_status_changed` and refreshes correctly.

8. Disable networking temporarily on the tablet and verify fallback polling behavior once connectivity returns.

Only after that should you expand to more restaurants and tablets.

## 9. Emergency and Rollback Planning

Have these controls ready before go-live.

### Fast kill switches

- Set `DM_TABLET_PUSH_ENABLED=false` to stop push sending while keeping pull endpoints available.
- Disable `sendToTablet` per restaurant if rollout needs to be narrowed quickly.

### If a tablet fails in production

Check in this order:

1. Does the server startup log show Firebase initialized successfully?
2. Does the tablet asset have a stored FCM token?
3. Is the asset assigned to the correct restaurant?
4. Did `push_sent_at` update in `tablet_order_dispatch`?
5. Is `last_failure_reason` populated?
6. Did `payload_pulled_at` update?
7. Are the tablet headers reaching the server unchanged?
8. Is the production tablet app pointing at the production backend and Firebase project?

### Secrets rotation plan

Be prepared to rotate:

- `DM_TABLET_API_KEY`
- Firebase service account JSON

Document who updates:

- server environment variables
- production secret file path
- tablet app configuration if required

## 10. Practical Notes For This Repository

These values are currently wired from `application.properties`:

- `app.tablet.api-key=${DM_TABLET_API_KEY:}`
- `app.tablet.push.fcm.enabled=${DM_TABLET_PUSH_ENABLED:false}`
- `app.tablet.push.fcm.project-id=${DM_FIREBASE_PROJECT_ID:}`
- `app.tablet.push.fcm.credentials-path=${DM_FIREBASE_CREDENTIALS_PATH:}`
- `app.tablet.push.max-failures-before-email=${DM_TABLET_PUSH_MAX_FAILURES:3}`
- `app.tablet.push.max-attempts-per-order=${DM_TABLET_PUSH_ATTEMPTS_PER_ORDER:3}`
- `app.tablet.provisioning.base-url=${DM_TABLET_BASE_URL:}`
- `app.tablet.provisioning.email=${DM_TABLET_PROVISIONING_EMAIL:${DM_ORDER_SUPPORT_EMAIL:support@delivermore.ca}}`
- `app.tablet.provisioning.qr-expiry-minutes=${DM_TABLET_PROVISIONING_EXPIRY_MINUTES:720}`

Use those exact variable names in the production environment.

## 11. Suggested Pre-Go-Live Signoff Questions

Before enabling restaurants in production, make sure you can answer yes to all of these:

- Do we know the shared Firebase project for both environments is `delivermore-tablet-push`?
- Do we know which service account JSON file the production server is using?
- Do we know where to confirm token registration and heartbeat activity?
- Do we know how to identify failed push attempts?
- Do we know how to turn push off without redeploying code?
- Do we know which restaurants should be enabled first?
- Do we know the production tablets are registering to the production backend and not the dev backend?
- Do we know how to reprovision or re-register any tablet that was previously used in dev?

If any answer is no, pause rollout until that gap is closed.