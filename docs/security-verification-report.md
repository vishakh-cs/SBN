# Authentication and authorization security verification

**Assessment date:** 2026-10-26  
**Scope:** Existing session authentication, RBAC, ownership checks, frontend session handling, and the API endpoints present in this repository.  
**Status:** Role and ownership rules remain in place. MySQL-backed integration tests are configured but could not run in this environment because no MySQL server is available. Proxy, TLS, role provisioning, and production topology remain environment-dependent.

## Executive summary

The API uses Spring Security sessions, BCrypt, CSRF tokens, explicit controller role checks, and service-level ownership checks. Public registration rejects `ADMIN` and `HOSPITAL`. The frontend restores identity from `/api/auth/me`, sends credentials and CSRF tokens, and does not use `localStorage` as proof of authentication. MySQL integration tests cover role boundaries, ownership/IDOR cases, CORS/CSRF, multiple sessions, an idle-session timeout, and persistence across a fresh application context. The base configuration defaults the session cookie to `Secure`; the local `dev` profile sets it false for HTTP.

The main deployment blockers are the process-local login limiter and process-local servlet sessions in a multi-instance deployment, plus the lack of an explicit trusted-proxy configuration. Production must use HTTPS, choose `SameSite` based on frontend/API site boundaries, set explicit CORS origins, avoid the `dev` profile, and securely provision initial admin/hospital accounts.

## Findings

| ID / severity | Finding and affected files/endpoints | Reproduction / evidence | Fix or required action |
|---|---|---|---|
| F-01 / **High, deployment blocker** | `LoginRateLimiter` stores failures in a local in-memory map; `AuthController#login` keys it using `HttpServletRequest.getRemoteAddr()`. It resets on process restart and does not share state between replicas. | Send failed logins to different replicas or restart the service; the counter is not shared/persistent. A single proxy address is seen as the client address unless the container is configured otherwise. | Use a shared, atomic rate-limit store (for example Redis) with both account and trusted client-IP dimensions before horizontally scaling. Do not trust arbitrary `X-Forwarded-For` values. |
| F-02 / **High, deployment blocker** | No reverse-proxy trust/deployment configuration is present; rate limiting uses the servlet peer address. | Deploy behind a reverse proxy: the limiter may group all users under the proxy IP. Enabling forwarded-header processing while directly exposing the app can let clients spoof the address used for limits. | Configure the proxy to strip incoming forwarding headers and inject its own; restrict direct app access; configure the app/container to trust only known proxy hops. Verify this in the actual deployment. The repository alone cannot verify that topology. |
| F-03 / **High if misconfigured** | `StartupDataSeeder` (`@Profile("dev")`) creates seeded ADMIN and HOSPITAL accounts with the known password `password123`. | Start with the `dev` profile and use `admin@test.local` or `hospital@test.local` with `password123`. | Keep `dev` disabled in production and never expose its seeded credentials. Provision production privileged accounts through an audited operator process. No production provisioning mechanism is included here. |
| F-04 / **Medium, deployment blocker for replicas** | Servlet sessions are container-local by default; `SecurityConfig` uses `HttpSessionSecurityContextRepository`. | Authenticate on one app instance and route the next protected request to another without sticky sessions or a shared session store; that instance will not have the session. | For multiple instances, use sticky routing or a supported shared Spring Session store, and verify logout/session expiry across replicas. The present tests use one instance. |
| F-05 / **Medium, environment-dependent** | Cookie security is configurable in `application.properties`, `SecurityConfig`, and `application-dev.properties`. Base default is now `Secure=true`; local `dev` overrides to false. `SameSite` defaults to `Lax`. | Inspect the `Set-Cookie` response when deployed. Cross-site frontend/API sites with `Lax` will not send the session cookie on cross-site fetches. | HTTPS is required for `Secure`. For cross-site deployments, configure `APP_SESSION_COOKIE_SAME_SITE=None` (and keep Secure on), with exact credentialed CORS origins. Runtime verification used HTTP test settings and did not exercise the production TLS edge. |
| F-06 / **Low, policy decision** | Multiple sessions per account are allowed; no maximum concurrent-session policy is configured. | Log in twice, then log out the first session: the first is rejected and the second remains valid. The integration test confirms this behavior. | Decide whether accounts need session limits or “log out all sessions” capability. The current behavior is explicit and does not bypass endpoint authorization. |
| F-07 / **Low, test coverage limit** | Production TLS termination, trusted proxy parsing, distributed throttling, and distributed session invalidation cannot be tested from this repository without deployment configuration. | Not reproducible in the local single-node test setup. | Treat these as release-gate checks in the target environment; do not infer them from the local Maven build. |
| F-08 / **Medium, fixed** | Spring MVC DEBUG request-body logging can serialize the login DTO, including its password, if debug logging is enabled. | This was observed in the test console before the fix as `LoginRequest[email=…, password=…]`. | Set `logging.level.org.springframework.web=INFO` in the base `application.properties`. After the change, the full test run passed and its captured output no longer contains `LoginRequest` body traces. Do not enable request-body logging in deployment. |

## Authorization map and integration-test coverage

All non-public API routes are covered by `.authenticated()` in `SecurityConfig`. `@PreAuthorize` supplies role gates, and service methods scope records to the authenticated email except where ADMIN access is explicitly allowed. This map is source-reviewed. The listed integration assertions require MySQL and were not executed in the current environment.

| Endpoint | Public / allowed roles | Ownership and sensitive-data rule | Test evidence |
|---|---|---|---|
| `GET /api/auth/csrf` | Public | Returns a CSRF token, no identity data. | Credentialed CORS allow/deny test. |
| `POST /api/auth/register` | Public; only `DONOR` or `PATIENT` accepted; missing role defaults to `DONOR`. | Role is checked server-side; BCrypt hash is stored; response DTO omits hash. | Attempts to register `ADMIN` and `HOSPITAL` return 400 and create no account. |
| `POST /api/auth/login` | Public | Generic 401 on bad credentials; session saved only on successful auth; threshold produces 429. | Successful and failed login, no session after failure, session fixation ID rotation, rate limit. |
| `GET /api/auth/me` | Authenticated | Returns the current account's safe `UserResponse`; there is no arbitrary-user profile route. | MySQL integration assertions cover all roles, anonymous 401, logout rejection, and idle timeout; run pending a local MySQL instance. |
| `POST /api/auth/logout` | CSRF-protected logout filter | Invalidates only the current session. | Session invalidated; second concurrent session remains valid. |
| `GET /api/admin/dashboard` | `ADMIN` | Aggregate administrative data. | Anonymous 401; patient/donor/hospital 403; admin 200. |
| `GET /api/users` | `ADMIN` | Sensitive user list; DTO excludes password hashes. | Patient/donor/hospital 403; admin 200; response checked for hash fields/hash strings. |
| `POST /api/blood-requests` | `PATIENT`, `HOSPITAL`, `ADMIN` | Requester name/email come from the authenticated account, not forged payload identity. | Patient create succeeds and forged contact email is overwritten; donor denied. |
| `GET /api/blood-requests` | `PATIENT`, `HOSPITAL`, `ADMIN` | Non-admin list filters by authenticated contact email; admin sees all. | Other patient's list is empty; donor denied. |
| `POST /api/blood-requests/{id}/match` | `PATIENT`, `HOSPITAL`, `ADMIN` | Owner or admin only; match results may contain donor contact details. | Another patient's request returns 403. Role gate for donor checked; hospital/admin permission is source-reviewed. |
| `POST /api/blood-requests/{id}/fulfill` | `HOSPITAL`, `ADMIN` | Owner or admin only. | Patient and foreign-owner hospital denied; admin succeeds. Hospital fulfilling its own request is source-reviewed but not separately exercised by this test. |
| `POST /api/donations` | `DONOR`, `ADMIN` | Donor ID must belong to current account; linked request must also belong to it unless admin. | Forged donor ID, patient role, and foreign request ID are denied. |
| `GET /api/donations/donor/{donorId}` | `DONOR`, `ADMIN` | Donor ID must belong to current account unless admin. DTO avoids nested `UserAccount`. | Donor cannot read another user's history. |
| `POST /api/organs/preferences` | `DONOR`, `ADMIN` | Donor ID must belong to current account unless admin. | Donor own preference succeeds; foreign donor and patient are denied; DTO checked for password data. |
| `GET /api/organs/preferences` | `ADMIN` | Sensitive preference/consent list. | Non-admin roles denied; admin succeeds. |
| `POST /api/organs/preferences/{id}/verify` | `ADMIN` | Verification is administrative only. | Donor denied; admin succeeds. |

**HTTP behavior:** the integration role matrix is configured to submit every protected controller route anonymously and with `DONOR`, `PATIENT`, `HOSPITAL`, and `ADMIN` sessions (14 routes × 5 identities). Assertions expect anonymous `401` and `403` for authenticated users outside the route's role/ownership permission. CSRF failures expect `403`; invalid login expects a generic `401`; public role escalation attempts expect `400`. These MySQL-backed integration assertions have not run in the current environment because MySQL is unavailable.

## Authentication/session and response checks

- Login uses Spring's DAO authentication provider and the configured BCrypt encoder; a session is created only after authentication succeeds.
- Login rotates an existing session ID. Logout invalidates the current server-side session and clears auth/cookies.
- CSRF uses a cookie token and requires the matching `X-XSRF-TOKEN` header on state-changing requests. A missing token on login was tested as 403.
- Session timeout is configured as 30 minutes by default. The integration test runs with a one-minute timeout, waits 65 seconds, and checks the old session receives 401.
- Session cookie is HttpOnly and SameSite=Lax by default. Base configuration now sets Secure by default; the dev profile and local HTTP integration test override Secure to false. Production `Secure` cookie emission was verified by configuration inspection, not by a TLS deployment test.
- Allowed CORS origins are explicitly configured, credentials are allowed, and an allowed origin was tested against a disallowed origin. Production origin values must be supplied and reviewed.
- Auth response DTOs (`UserResponse`, `DonationHistoryResponse`, `OrganPreferenceResponse`) do not expose hashes or session identifiers. The admin user response and login/preference responses are asserted not to contain password hashes. No arbitrary entity serialization was found for account or donation/organ preference endpoints; blood requests serialize only the blood-request entity, which has no account/password/session field.
- Base Spring Web logs are limited to INFO so MVC DEBUG body traces do not record login DTO credentials; test output was checked after this configuration change. Hibernate SQL logging remains off through `spring.jpa.show-sql=false` (SQL DEBUG may still appear if separately enabled by deployment logging configuration, without bound parameter values in the checked test output).
- Frontend `main.js` uses `credentials: "include"`, obtains CSRF tokens, restores state with `/api/auth/me`, and clears protected UI on 401. Repository search found no `localStorage` authentication use. Frontend role navigation is cosmetic; backend controls remain authoritative.

## Test/build evidence

Set `MYSQL_TEST_URL`, `MYSQL_TEST_USERNAME`, and `MYSQL_TEST_PASSWORD` to a disposable MySQL test database before running the Maven commands. Without these, MySQL-backed integration classes are skipped.

```powershell
..\.tools\apache-maven-3.9.9\bin\mvn.cmd test
..\.tools\apache-maven-3.9.9\bin\mvn.cmd package
node --check ..\frontend\assets\js\main.js
```

Verification attempted on 2026-10-26:

- `mvn clean package`: **BUILD SUCCESS**; 23 tests discovered, 4 passed, 19 MySQL-backed integration tests skipped because `127.0.0.1:3306` is unavailable. The skipped tests are `AuthenticationAuthorizationIntegrationTest` (17), `SessionExpirationIntegrationTest` (1), and `MySqlPersistenceIntegrationTest` (1).
- Database-backed role, session, and restart-persistence behavior must be rerun against the dedicated MySQL test database before treating those behaviors as runtime-verified for this revision.
- `node --check frontend/assets/js/main.js`: passed.
- No reverse proxy, TLS terminator, shared session store, or shared rate limiter was exercised.

## Release blockers / assumptions

1. Deploy only over HTTPS with `APP_SESSION_COOKIE_SECURE=true`; choose `APP_SESSION_COOKIE_SAME_SITE` to match the real frontend/API origin relationship.
2. Supply exact production `APP_CORS_ORIGINS` values. Do not use wildcard origins with credentials.
3. Keep the `dev` profile disabled; the seeder contains public demo credentials and creates privileged demo accounts.
4. Establish a secure, audited process to provision the first production ADMIN/HOSPITAL account.
5. For multi-instance deployments, deploy a shared rate limiter and shared/sticky session strategy. Ensure logout and expiration semantics are consistent across instances.
6. Configure trusted proxy handling only when the app is isolated behind a proxy that removes client-supplied forwarding headers. Validate the observed client IP and rate-limit behavior end to end.
7. Concurrent sessions are currently permitted. Set a session limit or revocation requirement only if the product's access policy requires it.
