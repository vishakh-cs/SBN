# Requirements Summary

This summary is based on the supplied abstract document.

## Goal

Build a web application that helps patients and hospitals find suitable blood donors quickly, while also supporting organ donor registration and awareness.

## Core Workflow

```text
Blood Request -> Find Eligible Donors -> Calculate Distance -> Rank Donors -> Notify Donors -> Donor Response -> Update Donation History
```

## Modules

1. User and Donor Module
   - User registration and login.
   - Donor profile with blood group, location, availability, and last donation date.
   - Blood and organ donation registration.
   - Donation history tracking.

2. Blood Request and Matching Module
   - Patients or hospitals create emergency blood requests.
   - System identifies eligible donors using blood group, availability, and location.
   - System calculates distance and suggests nearest suitable donors.
   - System sends notifications to selected donors.

3. Admin and Organ Donation Module
   - Admin manages users, donors, hospitals, and blood requests.
   - Users register organ donation preferences and consent.
   - Admin verifies and manages organ donor records.
   - Dashboard shows active requests and donation statistics.
