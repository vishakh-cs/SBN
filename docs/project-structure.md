# Project Structure

```text
Orgain_Donation/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/smartdonation/
│   │   │   │   ├── config/
│   │   │   │   ├── controller/
│   │   │   │   ├── dto/
│   │   │   │   ├── entity/
│   │   │   │   ├── exception/
│   │   │   │   ├── matching/
│   │   │   │   ├── notification/
│   │   │   │   ├── repository/
│   │   │   │   ├── security/
│   │   │   │   ├── service/
│   │   │   │   └── SmartDonationApplication.java
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       ├── static/
│   │   │       └── templates/
│   │   └── test/
│   │       └── java/com/smartdonation/
│   └── pom.xml
├── frontend/
│   ├── assets/
│   │   ├── css/
│   │   ├── images/
│   │   └── js/
│   ├── pages/
│   │   ├── admin/
│   │   ├── auth/
│   │   ├── donor/
│   │   ├── hospital/
│   │   └── patient/
│   └── index.html
├── database/
│   ├── migrations/
│   ├── seeds/
│   └── schema.sql
├── docs/
│   ├── project-structure.md
│   └── requirements-summary.md
└── tests/
    ├── api/
    └── manual/
```

## Backend Package Purpose

- `config`: Spring configuration such as CORS, mail, Swagger/OpenAPI, and app constants.
- `controller`: REST controllers for users, donors, requests, organs, admin, and authentication.
- `dto`: Request and response objects used by APIs.
- `entity`: JPA entities for users, donors, hospitals, blood requests, organ preferences, and donation history.
- `exception`: Global exception handling and custom exception classes.
- `matching`: Donor eligibility, blood compatibility, distance calculation, and ranking logic.
- `notification`: Email/SMS notification service integration.
- `repository`: Spring Data JPA repositories.
- `security`: Spring Security, authentication, authorization, JWT/session handling.
- `service`: Business logic for modules and workflows.

## Frontend Page Groups

- `auth`: Login and registration screens.
- `donor`: Donor profile, availability, organ preferences, and history screens.
- `patient`: Emergency blood request screens.
- `hospital`: Hospital request and donor tracking screens.
- `admin`: Admin dashboard, user management, request management, and statistics.

## Database Folder Purpose

- `schema.sql`: Base database schema.
- `migrations`: Versioned SQL changes as the project evolves.
- `seeds`: Sample data for testing and demos.
