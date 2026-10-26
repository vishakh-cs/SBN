# Smart Blood & Organ Donation Network

## Tech Stack

- Frontend: HTML, CSS, JavaScript, Bootstrap - simple to explain, runs on any laptop, and is enough for a college demo.
- Backend: Java 17, Spring Boot REST APIs - standard Java full-stack choice with clear controller, service, repository layers.
- Database: MySQL for both development and normal execution; the development profile seeds demo data into MySQL.
- Hosting: Render/Railway for backend and Netlify/GitHub Pages for frontend - free-tier friendly and easy to deploy separately.

## System Design

Roles: Admin manages records and statistics; donor registers blood/organ preferences; patient or hospital raises emergency requests.

Entities: `UserAccount` stores login/profile/donor data; `BloodRequest` stores emergency need; `DonorNotification` tracks alert attempts; `DonationHistory` stores completed donations; `OrganDonationPreference` stores consent and verification.

Core relation flow: a donor is a user; a blood request can notify many donors; a donation history record links one donor to an optional request; an organ preference belongs to one donor.

Main workflow: user registers as donor, patient/hospital creates a blood request, backend filters compatible donors, calculates distance with haversine, ranks donors, stores notification records, and admin marks the request fulfilled after donation.

## Feature Set

MVP demo:
- Register donors/users with blood group, location, availability, and last donation date.
- Create emergency blood requests.
- Match compatible donors by blood group, 90-day interval, availability, and distance.
- Save organ donation preferences and consent.
- View admin dashboard statistics.
- Run with seeded MySQL demo data on one laptop.

Implemented security:
- BCrypt password authentication with server-side HTTP sessions, CSRF protection and role-based authorization.
- Session restoration through `/api/auth/me`, explicit logout and per-record ownership checks.

Stretch goals:
- Shared login rate limiting for multi-instance deployment.
- Real SMS integration.
- Google Maps location picker and route distance.
- Donor accept/decline response screen.
- Appointment scheduling and hospital verification workflow.

## Project Structure

```text
backend/src/main/java/com/smartdonation
  config/          CORS and dev seed data
  controller/      REST endpoints
  dto/             request/response records
  entity/          JPA tables
  matching/        blood compatibility and distance logic
  notification/    email notification adapter
  repository/      Spring Data repositories
  security/        demo security and password encoding
  service/         business workflows
frontend/
  index.html
  assets/css/styles.css
  assets/js/main.js
database/schema.sql
docs/viva-project-report.md
```

## Build Plan

1. Define entities and database schema - done when users, requests, history, organ preferences, and notifications map to tables.
2. Add repositories and DTOs - done when controllers do not expose password hashes and can persist all modules.
3. Implement registration/login - done when a user can register with encoded password and login can validate it.
4. Implement blood request workflow - done when a request can be created and moved to notified/fulfilled.
5. Implement matching algorithm - done when only compatible, available, interval-safe donors are ranked by distance.
6. Implement organ consent - done when donor consent can be saved and admin can verify it.
7. Build frontend dashboard - done when demo forms call the backend and show matches/stats.
8. Add dev profile and seed data - done when the dev-only seeder adds demonstration records to MySQL.
9. Verify API and UI - done when `/api/admin/dashboard` responds and the frontend shows connected API status.

## Examiner Notes

- Matching is called AI-based in the abstract, but the implemented MVP uses a deterministic scoring algorithm. This is easier to justify in a college viva because it is transparent, testable, and medically safer than an unexplained model.
- Haversine distance is approximate straight-line distance; real deployment should use route distance from a maps API.
- Email sending is wrapped so failed local SMTP does not crash the emergency workflow; notification rows still prove which donors were selected.
- Both profiles use MySQL. Only the dev profile loads demo records, and those records remain in the database after restart.
