# Smart Blood & Organ Donation Network

Project structure for a Java full-stack web application that connects blood donors, organ donors, hospitals, and patients through a centralized platform.

## Technology Stack

- Frontend: HTML, CSS, JavaScript, Bootstrap
- Backend: Java, Spring Boot, REST APIs
- Database: MySQL
- Security: Spring Security
- Notifications: Email using JavaMailSender
- Matching: Blood compatibility, availability, location, and distance-based ranking

## Main Modules

- User and donor registration, login, profiles, availability, and donation history
- Emergency blood requests and nearest eligible donor matching
- Donor notifications and response tracking
- Organ donation preference and consent management
- Admin dashboard for users, donors, hospitals, requests, and statistics

See [docs/project-structure.md](docs/project-structure.md) for the folder layout.

## Local setup and run

The application uses **MySQL for every profile**. The `dev` profile only enables demo seed data and local email settings; it does not switch databases. Java 17 and a local MySQL server are required. Maven is available in `.tools/apache-maven-3.9.9` or can be installed separately.

1. Start the backend without demo accounts. The helper prompts for the local MySQL root password, creates the database and a restricted application user automatically, and runs the normal Spring profile:

```powershell
cd E:\MSC\Orgain_Donation\backend
.\run-local.ps1
```

This creates only a local administrator account in MySQL; it does not seed donor, patient, hospital, or request demo data. Local admin login: `admin@gmail.com` / `admin123`. The credential is reset by the `local` profile at startup and is for this college project on a private machine only. Register donor and patient accounts through the portal; public registration cannot create `ADMIN` or `HOSPITAL` accounts. Portal-created records persist in MySQL after application restarts. The older `run-dev.ps1` command enables the separate demo dataset.

To check persistence, create a portal account or blood request, stop the backend with `Ctrl+C`, restart it with `run-local.ps1`, and log in or view the same request again.

2. In another PowerShell window, start the existing frontend:

```powershell
cd frontend
.\run.ps1
```

Open `http://localhost:5501`. The backend API is at `http://localhost:8080/api`. The full case-study report is in `docs/viva-project-report.md`.

### Email notifications

The development profile attempts SMTP delivery through `localhost:2525`. If no local SMTP server is running, the blood matching flow still completes and the notification is saved with `PENDING` status. For actual email delivery, set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, and `SMTP_PASSWORD` before starting the app.

### Automated tests

Unit tests run with Maven. The authentication, session, and persistence integration tests use a dedicated MySQL test database. Create it separately from the application database:

```sql
CREATE DATABASE smart_donation_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Then set these variables in PowerShell before running Maven. The authentication and session tests use `create-drop`, so keep this test database separate from the demo/application database:

```powershell
$env:MYSQL_TEST_URL = "jdbc:mysql://localhost:3306/smart_donation_test"
$env:MYSQL_TEST_USERNAME = "smart_donation"
$env:MYSQL_TEST_PASSWORD = "choose-a-local-password"
cd backend
..\.tools\apache-maven-3.9.9\bin\mvn.cmd test
```

Without `MYSQL_TEST_URL`, MySQL-backed integration tests are skipped; Maven still runs unit tests. To build the executable jar, run `..\.tools\apache-maven-3.9.9\bin\mvn.cmd package` from `backend`.

## Authentication and authorization

- Public registration creates `DONOR` or `PATIENT` accounts only; `ADMIN` and `HOSPITAL` accounts must be provisioned outside public registration.
- Login uses a server-side session. The browser fetches a CSRF token from `/api/auth/csrf` and restores the signed-in profile from `/api/auth/me`.
- Protected requests include the session cookie; state-changing requests include the CSRF header. Logout uses `POST /api/auth/logout`.
- Session cookies default to `Secure` in the base configuration; the `dev` profile disables this for local HTTP. For separately hosted cross-site frontend/API deployments, set `APP_SESSION_COOKIE_SAME_SITE=None` (requires HTTPS); keep `Lax` for same-site deployments. `APP_CORS_ORIGINS` is a comma-separated list of trusted frontend origins. Session timeout, MySQL/SMTP credentials, and login-rate thresholds are configurable with environment variables.
- The development profile seeds demo accounts using `password123`; use those only locally. Quick-login buttons have been removed.

