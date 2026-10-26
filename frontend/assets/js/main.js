const API_BASE = window.SMART_DONATION_API_BASE || "http://localhost:8080/api";
const bloodGroups = ["A_POSITIVE", "A_NEGATIVE", "B_POSITIVE", "B_NEGATIVE", "AB_POSITIVE", "AB_NEGATIVE", "O_POSITIVE", "O_NEGATIVE"];

let currentUser = null;
let activeRole = null;
let csrfToken = null;
let registrationRole = "DONOR";
let selectedOrganRequest = null;
let adminBloodRequestsExpanded = false;

const notifications = document.querySelector("#notifications");
const loginView = document.querySelector("#loginView");
const portalView = document.querySelector("#portalView");
const donorDashboard = document.querySelector("#donorDashboard");
const userBadge = document.querySelector("#userBadge");
const logoutBtn = document.querySelector("#logoutBtn");

document.querySelectorAll("[data-blood-options]").forEach((select) => {
    select.innerHTML = bloodGroups.map((group) => `<option value="${group}">${formatBloodGroup(group)}</option>`).join("");
});
const today = new Date();
document.querySelector("#profileLastDonationDate").max = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;

function formatBloodGroup(group) {
    return group.replace("_POSITIVE", "+").replace("_NEGATIVE", "-");
}

function escapeHtml(value) {
    const element = document.createElement("span");
    element.textContent = value ?? "";
    return element.innerHTML;
}

function log(message, data) {
    const toast = document.createElement("div");
    const lowerMessage = message.toLowerCase();
    const tone = /failed|error|unreachable|denied|expired/.test(lowerMessage) ? "error"
        : /no demo request/.test(lowerMessage) ? "warning"
            : /logged in|registered|saved|created|matched|logged out/.test(lowerMessage) ? "success"
                : "info";
    const detail = data?.error || data?.reason;
    toast.className = `notification notification-${tone}`;
    toast.setAttribute("role", tone === "error" ? "alert" : "status");
    const text = document.createElement("span");
    text.className = "notification-message";
    text.textContent = detail ? `${message}: ${detail}` : message;
    toast.append(text);
    const closeButton = document.createElement("button");
    closeButton.className = "notification-close";
    closeButton.type = "button";
    closeButton.setAttribute("aria-label", "Dismiss notification");
    closeButton.textContent = "\u00d7";
    closeButton.addEventListener("click", () => toast.remove());
    toast.append(closeButton);
    notifications.append(toast);
    window.setTimeout(() => toast.remove(), 6000);
}

async function getCsrfToken() {
    const response = await fetch(`${API_BASE}/auth/csrf`, { credentials: "include" });
    if (!response.ok) throw new Error("Could not initialize secure session");
    csrfToken = (await response.json()).token;
    return csrfToken;
}

async function api(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = { "Content-Type": "application/json", ...options.headers };
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
        headers["X-XSRF-TOKEN"] = csrfToken || await getCsrfToken();
    }
    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        method,
        headers,
        credentials: "include"
    });
    if (response.status === 401 && !["/auth/login", "/auth/register", "/auth/csrf", "/auth/me"].includes(path)) {
        clearSessionView();
        log("Your session expired. Please log in again.");
    }
    if (!response.ok) {
        const error = await response.json().catch(() => ({ error: response.statusText }));
        const message = response.status === 403 ? "This account does not have permission for that action"
            : response.status === 429 ? "Too many login attempts. Wait before trying again"
                : error.error || response.statusText;
        throw new Error(message);
    }
    if (response.status === 204) return null;
    return response.json();
}

function formData(form) {
    const data = Object.fromEntries(new FormData(form).entries());
    form.querySelectorAll("input[type=checkbox]").forEach((input) => {
        data[input.name] = input.checked;
    });
    form.querySelectorAll("input[type=number]").forEach((input) => {
        data[input.name] = Number(input.value);
    });
    Object.keys(data).forEach((key) => {
        if (data[key] === "") data[key] = null;
    });
    return data;
}

document.querySelectorAll("[data-registration-role]").forEach((tab) => {
    tab.addEventListener("click", () => setRegistrationRole(tab.dataset.registrationRole));
});

function setRegistrationRole(role) {
    registrationRole = role === "PATIENT" ? "PATIENT" : "DONOR";
    const form = document.querySelector("#donorForm");
    form.elements.role.value = registrationRole;
    document.querySelectorAll(".donor-registration-only").forEach((field) => {
        field.hidden = registrationRole === "PATIENT";
        field.querySelectorAll("input, select, textarea").forEach((input) => {
            input.disabled = registrationRole === "PATIENT";
        });
    });
    document.querySelectorAll("[data-registration-role]").forEach((tab) => {
        const selected = tab.dataset.registrationRole === registrationRole;
        tab.classList.toggle("active", selected);
        tab.setAttribute("aria-selected", String(selected));
    });
    document.querySelector(".register-submit").firstChild.nodeValue = `Register as ${titleCase(registrationRole)} `;
}
setRegistrationRole(registrationRole);

function clearSessionView() {
    currentUser = null;
    activeRole = null;
    loginView.classList.remove("d-none");
    portalView.classList.add("d-none");
    donorDashboard.classList.add("d-none");
    userBadge.classList.add("d-none");
    logoutBtn.classList.add("d-none");
    document.querySelector("#heroTitle").innerHTML = 'Login to open your <span>workspace.</span>';
    document.querySelector("#heroText").textContent = "Donors manage consent and availability, hospitals raise emergency requests, patients request blood, and admins monitor the network.";
}

async function refreshDashboard() {
    if (!currentUser || currentUser.role !== "ADMIN") return;
    const results = await Promise.allSettled([
        api("/admin/dashboard"), api("/users"), api("/blood-requests"),
        api("/admin/organ-requests"), api("/organs/preferences")
    ]);
    const [dashboard, users, bloodRequests, organRequests, preferences] = results.map((result) => result.status === "fulfilled" ? result.value : null);
    if (dashboard) {
        document.querySelector("#dashUsers").textContent = dashboard.totalUsers;
        document.querySelector("#dashBlood").textContent = dashboard.totalBloodDonors;
        document.querySelector("#dashOrgan").textContent = dashboard.totalOrganDonors;
    }
    if (organRequests && preferences) {
        const pending = organRequests.filter((request) => request.status === "PENDING").length
            + preferences.filter((preference) => !preference.verified).length;
        document.querySelector("#dashPending").textContent = pending;
    }
    if (users) renderAdminUsers(users);
    if (bloodRequests) renderAdminBloodRequests(bloodRequests);
    if (organRequests) renderAdminOrganRequests(organRequests);
    if (preferences && users) renderAdminVerification(preferences, users);
    if (users) await renderAdminDonationHistory(users);
    renderAdminActivity(bloodRequests, organRequests, preferences, users);
    if (!users) setAdminTableError("#adminUsersTable", 6, "User records could not be loaded.");
    if (!bloodRequests) setAdminTableError("#adminBloodRequestsTable", 5, "Blood requests could not be loaded.");
    if (!organRequests) setAdminTableError("#adminOrganRequestsTable", 6, "Organ requests could not be loaded.");
    if (!preferences) setAdminTableError("#adminVerificationTable", 5, "Verification records could not be loaded.");
    if (!bloodRequests && !organRequests && !preferences) document.querySelector("#adminActivityList").innerHTML = '<li class="table-error">Recent activity could not be loaded.</li>';
    if (results.some((result) => result.status === "rejected")) {
        log("Some dashboard data could not be refreshed. Use Refresh to try again.");
    }
}

function setAdminTableError(selector, columns, message) {
    document.querySelector(selector).innerHTML = `<tr><td colspan="${columns}" class="table-error">${escapeHtml(message)}</td></tr>`;
}

function renderAdminUsers(users) {
    const body = document.querySelector("#adminUsersTable");
    body.innerHTML = users.length ? users.map((user) => `<tr><td>${escapeHtml(user.fullName)}</td><td>${escapeHtml(user.email)}</td><td>${escapeHtml(titleCase(user.role))}</td><td>${escapeHtml(user.bloodGroup ? formatBloodGroup(user.bloodGroup) : "—")}</td><td>${escapeHtml(user.city || "—")}</td><td>${user.bloodDonor ? (user.availableForDonation ? "Available" : "Unavailable") : "—"}</td></tr>`).join("") : '<tr><td colspan="6" class="table-empty">No user records found.</td></tr>';
}

function renderAdminBloodRequests(requests) {
    const body = document.querySelector("#adminBloodRequestsTable");
    const sorted = [...requests].sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
    const displayed = adminBloodRequestsExpanded ? sorted : sorted.slice(0, 5);
    const viewAll = document.querySelector("#adminBloodRequestsViewAll");
    viewAll.classList.toggle("d-none", requests.length <= 5);
    viewAll.innerHTML = adminBloodRequestsExpanded ? 'Show Recent <span aria-hidden="true">↑</span>' : 'View All <span aria-hidden="true">›</span>';
    body.innerHTML = displayed.length ? displayed.map((request) => `<tr><td><strong>${escapeHtml(request.requesterName || "—")}</strong><small>${escapeHtml(request.city || "")}</small></td><td>${escapeHtml(formatBloodGroup(request.requiredBloodGroup || "—"))}</td><td>${escapeHtml(request.hospitalName || "—")}</td><td>${escapeHtml(request.urgency || "—")}</td><td>${statusBadge(request.status)}</td></tr>`).join("") : '<tr><td colspan="5" class="table-empty">No blood requests have been submitted.</td></tr>';
}

function renderAdminOrganRequests(requests) {
    const body = document.querySelector("#adminOrganRequestsTable");
    body.innerHTML = requests.length ? requests.map((item) => `<tr><td><strong>${escapeHtml(item.patientName)}</strong><small>${escapeHtml(item.patientEmail)}</small></td><td>${escapeHtml(item.organType)}<small>${escapeHtml(item.urgency)}</small></td><td>${escapeHtml(item.hospitalName)}</td><td>${escapeHtml(formatRequestDate(item.submittedAt))}</td><td>${statusBadge(item.status)}</td><td><button class="btn btn-sm ${item.status === "PENDING" ? "btn-outline-danger" : "btn-outline-secondary"}" type="button" data-review-request="${Number(item.id)}">${item.status === "PENDING" ? "Review" : "View details"}</button></td></tr>`).join("") : '<tr><td colspan="6" class="table-empty">No organ requests have been submitted.</td></tr>';
    body.querySelectorAll("[data-review-request]").forEach((button) => button.addEventListener("click", () => openOrganReview(requests.find((item) => Number(item.id) === Number(button.dataset.reviewRequest)))));
}

function renderAdminVerification(preferences, users) {
    const body = document.querySelector("#adminVerificationTable");
    const usersById = new Map(users.map((user) => [Number(user.id), user]));
    body.innerHTML = preferences.length ? preferences.map((preference) => {
        const donor = usersById.get(Number(preference.donorId));
        const action = preference.verified ? "Verified" : `<button class="btn btn-sm btn-danger" type="button" data-verify-preference="${Number(preference.id)}">Verify</button>`;
        return `<tr><td><strong>${escapeHtml(donor?.fullName || "Donor")}</strong><small>${escapeHtml(donor?.city || "")}</small></td><td><span class="admin-consent-yes">Yes</span><small>${escapeHtml(preference.organs || "No organs specified")}</small></td><td>${escapeHtml(formatRequestDate(preference.registeredAt))}</td><td>${statusBadge(preference.verified ? "VERIFIED" : "PENDING")}</td><td>${action}</td></tr>`;
    }).join("") : '<tr><td colspan="5" class="table-empty">No organ donation preferences have been submitted.</td></tr>';
    body.querySelectorAll("[data-verify-preference]").forEach((button) => button.addEventListener("click", async () => {
        button.disabled = true;
        try {
            await api(`/organs/preferences/${Number(button.dataset.verifyPreference)}/verify`, { method: "POST" });
            log("Organ donor preference verified.");
            await refreshDashboard();
        } catch (error) {
            log("Organ donor preference could not be verified", { error: error.message });
            button.disabled = false;
        }
    }));
}

async function renderAdminDonationHistory(users) {
    const body = document.querySelector("#adminDonationHistoryTable");
    const donors = users.filter((user) => user.role === "DONOR");
    const results = await Promise.allSettled(donors.map(async (donor) => ({
        donor, donations: await api(`/donations/donor/${Number(donor.id)}`)
    })));
    const rows = results.filter((result) => result.status === "fulfilled")
        .flatMap((result) => result.value.donations.map((donation) => ({ donor: result.value.donor, donation })))
        .sort((a, b) => new Date(b.donation.donatedOn || 0) - new Date(a.donation.donatedOn || 0));
    if (!rows.length && results.some((result) => result.status === "rejected")) {
        setAdminTableError("#adminDonationHistoryTable", 5, "Donation history could not be loaded.");
        return;
    }
    body.innerHTML = rows.length ? rows.map(({ donor, donation }) => `<tr><td>${escapeHtml(donor.fullName)}</td><td>${escapeHtml(formatRequestDate(donation.donatedOn))}</td><td>${escapeHtml(donation.donationType || "—")}</td><td>${escapeHtml(donation.location || "—")}</td><td>${donation.bloodRequestId ? `#${Number(donation.bloodRequestId)}` : "—"}</td></tr>`).join("") : '<tr><td colspan="5" class="table-empty">No donation history has been recorded.</td></tr>';
    if (results.some((result) => result.status === "rejected")) body.insertAdjacentHTML("beforeend", '<tr><td colspan="5" class="table-error">Some donors’ history could not be loaded.</td></tr>');
}

function renderAdminActivity(bloodRequests, organRequests, preferences, users) {
    const list = document.querySelector("#adminActivityList");
    const userNames = new Map((users || []).map((user) => [Number(user.id), user.fullName]));
    const events = [
        ...(bloodRequests || []).map((item) => ({ date: item.createdAt, label: `Blood request submitted by ${item.requesterName || "a user"}` })),
        ...(organRequests || []).flatMap((item) => [
            { date: item.submittedAt, label: `Organ request submitted by ${item.patientName || "a patient"}` },
            ...(item.reviewedAt ? [{ date: item.reviewedAt, label: `Organ request ${String(item.status || "reviewed").toLowerCase()} by ${item.reviewedByName || "an administrator"}` }] : [])
        ]),
        ...(preferences || []).map((item) => ({ date: item.registeredAt, label: `Organ donation consent registered by ${userNames.get(Number(item.donorId)) || "a donor"}` }))
    ].filter((event) => event.date).sort((a, b) => new Date(b.date) - new Date(a.date)).slice(0, 8);
    list.innerHTML = events.length ? events.map((event) => `<li><span>${escapeHtml(event.label)}</span><time datetime="${escapeHtml(event.date)}">${escapeHtml(formatRequestDate(event.date))}</time></li>`).join("") : '<li class="table-empty">No recent activity is available.</li>';
}

function initializeAdminDate() {
    document.querySelector("#adminToday").textContent = new Intl.DateTimeFormat(undefined, {
        year: "numeric", month: "short", day: "numeric", weekday: "long"
    }).format(new Date());
}

document.querySelector("#loginForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await getCsrfToken();
        const result = await api("/auth/login", {
            method: "POST",
            body: JSON.stringify(formData(form))
        });
        csrfToken = null;
        await getCsrfToken();
        setCurrentUser(await api("/auth/me"));
        log("Logged in", { name: result.user.fullName, role: result.user.role });
    } catch (error) {
        log("Login failed", { error: error.message });
    }
});

logoutBtn.addEventListener("click", async () => {
    try {
        await api("/auth/logout", { method: "POST" });
        csrfToken = null;
        await getCsrfToken();
        clearSessionView();
        log("Logged out");
    } catch (error) {
        log("Logout failed", { error: error.message });
    }
});

function setCurrentUser(user) {
    currentUser = user;
    activeRole = user.role;
    loginView.classList.add("d-none");
    const donor = user.role === "DONOR";
    donorDashboard.classList.toggle("d-none", !donor);
    portalView.classList.toggle("d-none", donor);
    userBadge.classList.remove("d-none");
    logoutBtn.classList.remove("d-none");
    userBadge.textContent = `${user.fullName} | ${user.role}`;
    document.querySelector("#demoMatchBtn").textContent = user.role === "PATIENT" ? "Refresh My Matches" : "Match Demo Request";
    document.querySelector("#heroTitle").textContent = `${titleCase(user.role)} workspace`;
    document.querySelector("#heroText").textContent = workspaceCopy(user.role);
    if (donor) {
        populateDonorProfile(user);
        loadDonorDashboard(user);
    } else {
        prefillRoleForms(user);
        if (user.role === "PATIENT") {
            setPatientRequestMode("blood");
            loadMyRequests();
            loadPatientDonorMatches();
        }
        if (user.role === "ADMIN") initializeAdminDate();
    }
    showRole(activeRole);
    if (user.role === "PATIENT") setPatientRequestMode("blood");
    refreshDashboard();
}

document.querySelectorAll("[data-patient-request-mode]").forEach((tab) => {
    tab.addEventListener("click", () => setPatientRequestMode(tab.dataset.patientRequestMode));
});

function setPatientRequestMode(mode) {
    const organ = mode === "organ";
    document.querySelector("#requestForm").classList.toggle("d-none", organ);
    document.querySelector("#organRequestForm").classList.toggle("d-none", !organ || activeRole !== "PATIENT");
    document.querySelectorAll("[data-patient-request-mode]").forEach((tab) => {
        const selected = tab.dataset.patientRequestMode === (organ ? "organ" : "blood");
        tab.classList.toggle("active", selected);
        tab.setAttribute("aria-selected", String(selected));
    });
}

function statusBadge(status) {
    const normalized = String(status || "PENDING").toUpperCase();
    const label = normalized.charAt(0) + normalized.slice(1).toLowerCase();
    return `<span class="request-status status-${normalized.toLowerCase()}">${escapeHtml(label)}</span>`;
}

async function loadMyRequests() {
    const body = document.querySelector("#myRequestsTable");
    body.innerHTML = '<tr><td colspan="6"><span class="table-loading">Loading your requests…</span></td></tr>';
    const [bloodResult, organResult] = await Promise.allSettled([api("/blood-requests"), api("/organ-requests/mine")]);
    if (bloodResult.status === "rejected" && organResult.status === "rejected") {
        body.innerHTML = '<tr><td colspan="6" class="table-error">Your requests could not be loaded. Please refresh.</td></tr>';
        log("My requests could not be loaded", { error: bloodResult.reason.message });
        return;
    }
    const blood = bloodResult.status === "fulfilled" ? bloodResult.value.filter((item) => item.status !== "CANCELLED").map((item) => ({
        type: "Blood", submittedAt: item.createdAt, hospital: item.hospitalName || "—", status: item.status || "OPEN",
        detail: `${formatBloodGroup(item.requiredBloodGroup || "")}${item.notes ? ` · ${item.notes}` : ""}`, id: item.id
    })) : [];
    const organs = organResult.status === "fulfilled" ? organResult.value.map((item) => ({
        type: "Organ transplant", submittedAt: item.submittedAt, hospital: item.hospitalName,
        status: item.status, detail: `${item.organType}${item.notes ? ` · ${item.notes}` : ""}${item.rejectionReason ? ` · Reason: ${item.rejectionReason}` : ""}`
    })) : [];
    const rows = [...blood, ...organs].sort((a, b) => new Date(b.submittedAt || 0) - new Date(a.submittedAt || 0));
    body.innerHTML = rows.length ? rows.map((item) => `<tr><td>${escapeHtml(item.type)}</td><td>${escapeHtml(formatRequestDate(item.submittedAt))}</td><td>${escapeHtml(item.hospital || "—")}</td><td>${statusBadge(item.status)}</td><td>${escapeHtml(item.detail || "—")}</td><td>${item.type === "Blood" && ["OPEN", "NOTIFIED"].includes(item.status) ? `<button class="btn btn-sm btn-outline-danger delete-request-button" type="button" data-delete-blood-request="${Number(item.id)}">Delete</button>` : "—"}</td></tr>`).join("") : '<tr><td colspan="6" class="table-empty">You have not submitted any requests yet.</td></tr>';
    body.querySelectorAll("[data-delete-blood-request]").forEach((button) => button.addEventListener("click", () => deleteBloodRequest(button.dataset.deleteBloodRequest, button)));
}

async function deleteBloodRequest(id, button) {
    if (!window.confirm("Delete this blood request? It will be cancelled and removed from your request list.")) return;
    button.disabled = true;
    try {
        await api(`/blood-requests/${Number(id)}`, { method: "DELETE" });
        log("Blood request deleted.");
        await Promise.all([loadMyRequests(), loadPatientDonorMatches()]);
    } catch (error) {
        log("Blood request could not be deleted", { error: error.message });
        button.disabled = false;
    }
}

async function loadAdminOrganRequests() {
    const body = document.querySelector("#adminOrganRequestsTable");
    body.innerHTML = '<tr><td colspan="6"><span class="table-loading">Loading organ requests…</span></td></tr>';
    try {
        const requests = await api("/admin/organ-requests");
        renderAdminOrganRequests(requests);
    } catch (error) {
        body.innerHTML = '<tr><td colspan="6" class="table-error">Organ requests could not be loaded. Please refresh.</td></tr>';
        log("Organ requests could not be loaded", { error: error.message });
    }
}

function openOrganReview(request) {
    if (!request) return;
    selectedOrganRequest = request;
    document.querySelector("#reviewRequestFacts").innerHTML = [
        ["Patient", `${request.patientName} (${request.patientEmail})`], ["Organ", request.organType], ["Hospital / centre", request.hospitalName],
        ["Urgency", request.urgency], ["Submitted", formatRequestDate(request.submittedAt)], ["Status", request.status],
        ...(request.reviewedByName ? [["Reviewed by", request.reviewedByName]] : []), ...(request.reviewedAt ? [["Reviewed at", formatRequestDate(request.reviewedAt)]] : [])
    ].map(([label, value]) => `<div><span>${escapeHtml(label)}</span><strong>${escapeHtml(value || "—")}</strong></div>`).join("");
    document.querySelector("#reviewRequestNotes").textContent = request.notes || "No notes provided.";
    document.querySelector("#reviewRejectionReason").value = request.rejectionReason || "";
    document.querySelector("#reviewRejectionLabel").classList.toggle("d-none", request.status !== "PENDING");
    document.querySelector("#reviewRejectionReason").required = request.status === "PENDING";
    document.querySelector("#reviewFeedback").textContent = request.rejectionReason ? `Rejection reason: ${request.rejectionReason}` : "";
    document.querySelector("#verifyOrganRequestBtn").classList.toggle("d-none", request.status !== "PENDING");
    document.querySelector("#rejectOrganRequestBtn").classList.toggle("d-none", request.status !== "PENDING");
    document.querySelector("#organReviewDialog").showModal();
}

async function decideOrganRequest(decision) {
    if (!selectedOrganRequest || selectedOrganRequest.status !== "PENDING") return;
    const reason = document.querySelector("#reviewRejectionReason").value.trim();
    if (decision === "reject" && !reason) {
        document.querySelector("#reviewFeedback").textContent = "Enter a reason before rejecting this request.";
        document.querySelector("#reviewRejectionReason").focus();
        return;
    }
    const buttons = [document.querySelector("#verifyOrganRequestBtn"), document.querySelector("#rejectOrganRequestBtn")];
    buttons.forEach((button) => button.disabled = true);
    document.querySelector("#reviewFeedback").textContent = "Saving decision…";
    try {
        const path = `/admin/organ-requests/${Number(selectedOrganRequest.id)}/${decision}`;
        await api(path, { method: "POST", ...(decision === "reject" ? { body: JSON.stringify({ reason }) } : {}) });
        document.querySelector("#organReviewDialog").close();
        log(`Organ request ${decision === "verify" ? "verified" : "rejected"}.`);
        await refreshDashboard();
    } catch (error) {
        document.querySelector("#reviewFeedback").textContent = `Decision could not be saved: ${error.message}`;
    } finally {
        buttons.forEach((button) => button.disabled = false);
    }
}

function showRole(role) {
    activeRole = role;
    document.querySelectorAll("[data-role-panel]").forEach((panel) => {
        const allowedRoles = panel.dataset.rolePanel.split(" ");
        panel.classList.toggle("d-none", !allowedRoles.includes(role));
    });
}

function workspaceCopy(role) {
    const copy = {
        ADMIN: "Monitor network statistics, inspect donor matching, and verify the emergency workflow.",
        DONOR: "Review your donor profile and save organ donation consent details.",
        HOSPITAL: "Create emergency blood requests and notify the nearest compatible donors.",
        PATIENT: "Raise blood requests and view matched donors ranked by compatibility and distance."
    };
    return copy[role] || "Authenticated workspace.";
}

function prefillRoleForms(user) {
    const requestForm = document.querySelector("#requestForm");
    requestForm.elements.requesterName.value = user.fullName;
    requestForm.elements.contactEmail.value = user.email;
    requestForm.elements.contactPhone.value = user.phone || "";
    requestForm.elements.requiredBloodGroup.value = user.bloodGroup || "A_POSITIVE";
}

function populateDonorProfile(user) {
    document.querySelector("#donorName").textContent = user.fullName || "Name not set";
    document.querySelector("#donorProfileBlood").textContent = user.bloodGroup ? formatBloodGroup(user.bloodGroup) : "Not set";
    document.querySelector("#donorBloodGroup").textContent = user.bloodGroup ? formatBloodGroup(user.bloodGroup) : "—";
    document.querySelector("#donorCity").textContent = user.city || "City not set";
    document.querySelector("#donorPhone").textContent = user.phone || "Not provided";
    document.querySelector("#donorLastDonationDate").textContent = user.lastDonationDate ? formatRequestDate(user.lastDonationDate) : "Not recorded";
    document.querySelector("#organDonorId").value = user.id;
    const profileForm = document.querySelector("#donorProfileForm");
    profileForm.elements.fullName.value = user.fullName || "";
    profileForm.elements.bloodGroup.value = user.bloodGroup || "A_POSITIVE";
    profileForm.elements.phone.value = user.phone || "";
    profileForm.elements.city.value = user.city || "";
    profileForm.elements.lastDonationDate.value = user.lastDonationDate || "";
    setAvailabilityDisplay(user);
}

document.querySelector("#editDonorProfileBtn").addEventListener("click", () => {
    document.querySelector("#donorProfileFeedback").textContent = "";
    document.querySelector("#donorProfileForm").classList.remove("d-none");
    document.querySelector("#editDonorProfileBtn").classList.add("d-none");
    document.querySelector("#profileFullName").focus();
});

document.querySelector("#cancelDonorProfileEditBtn").addEventListener("click", () => {
    document.querySelector("#donorProfileForm").classList.add("d-none");
    document.querySelector("#editDonorProfileBtn").classList.remove("d-none");
    if (currentUser) populateDonorProfile(currentUser);
});

document.querySelector("#donorProfileForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.reportValidity()) return;
    const feedback = document.querySelector("#donorProfileFeedback");
    const saveButton = document.querySelector("#saveDonorProfileBtn");
    saveButton.disabled = true;
    feedback.textContent = "Saving profile…";
    try {
        const updated = await api("/users/me/profile", { method: "PATCH", body: JSON.stringify(formData(form)) });
        currentUser = updated;
        populateDonorProfile(updated);
        form.classList.add("d-none");
        document.querySelector("#editDonorProfileBtn").classList.remove("d-none");
        log("Donor profile updated.");
    } catch (error) {
        feedback.textContent = `Profile could not be saved: ${error.message}`;
    } finally {
        saveButton.disabled = false;
    }
});

function setAvailabilityDisplay(user) {
    const status = document.querySelector("#availabilityStatus");
    const switchButton = document.querySelector("#availabilityToggle");
    const profileStatus = document.querySelector("#donorProfileAvailability");
    const bloodDonor = Boolean(user.bloodDonor);
    status.textContent = bloodDonor ? (user.availableForDonation ? "Available" : "Unavailable") : "Not enrolled";
    profileStatus.textContent = bloodDonor ? (user.availableForDonation ? "Available" : "Unavailable") : "Blood donation not selected";
    switchButton.setAttribute("aria-checked", String(Boolean(user.availableForDonation)));
    switchButton.disabled = !bloodDonor;
    switchButton.title = bloodDonor ? "Toggle your blood donation availability" : "Blood donation was not selected in your profile";
}

async function loadDonorDashboard(user) {
    const requestsContainer = document.querySelector("#nearbyRequests");
    document.querySelector("#consentStatus").textContent = "Loading…";
    document.querySelector("#consentStatusDot").classList.remove("is-saved");
    requestsContainer.innerHTML = '<div class="requests-loading">Loading matching requests…</div>';
    const [preferenceResult] = await Promise.allSettled([
        api("/organs/preferences/me"),
        refreshNearbyRequests()
    ]);
    if (preferenceResult.status === "fulfilled") {
        const preference = preferenceResult.value;
        document.querySelector("#consentStatus").textContent = preference ? "Registered" : "Not registered";
        document.querySelector("#consentStatusDot").classList.toggle("is-saved", Boolean(preference));
        if (preference) {
            const form = document.querySelector("#organForm");
            form.elements.organs.value = preference.organs || "";
            form.elements.consentStatement.value = preference.consentStatement || "";
        }
    } else {
        document.querySelector("#consentStatus").textContent = "Could not load";
        log("Organ consent status could not be loaded", { error: preferenceResult.reason.message });
    }
}

async function refreshNearbyRequests() {
    const container = document.querySelector("#nearbyRequests");
    container.innerHTML = '<div class="requests-loading">Loading matching requests…</div>';
    try {
        const requests = await api("/blood-requests/nearby");
        renderNearbyRequests(requests);
    } catch (error) {
        container.innerHTML = '<div class="requests-state requests-error">Nearby requests could not be loaded. Please try refreshing.</div>';
        throw error;
    }
}

function renderNearbyRequests(requests) {
    const container = document.querySelector("#nearbyRequests");
    if (!requests.length) {
        container.innerHTML = '<div class="requests-state"><span class="empty-state-mark" aria-hidden="true">✓</span><strong>No matching requests nearby.</strong><span>We will show active requests that match your blood group and area.</span></div>';
        return;
    }
    container.innerHTML = requests.map((request) => {
        const detailsId = `request-details-${Number(request.id)}`;
        return `<article class="nearby-request-card">
            <div class="request-card-main">
                <span class="request-blood-badge">${escapeHtml(formatBloodGroup(request.requiredBloodGroup))}</span>
                <div class="request-card-info"><div class="request-card-title"><h3>${escapeHtml(request.hospitalName || "Blood request")}</h3><span class="urgency-badge ${urgencyClass(request.urgency)}">${escapeHtml(request.urgency || "Active")}</span></div>
                    <p class="request-card-location">${escapeHtml(request.city || "Location not specified")} · ${Number(request.distanceKm).toFixed(1)} km away · Posted ${escapeHtml(formatRequestDate(request.createdAt))}</p>
                </div>
                <button class="request-detail-button" type="button" aria-expanded="false" aria-controls="${detailsId}" data-request-details="${detailsId}">View details</button>
            </div>
            <div class="request-card-details d-none" id="${detailsId}"><span>${Number(request.unitsRequired)} unit${Number(request.unitsRequired) === 1 ? "" : "s"} requested</span><span>Posted ${escapeHtml(formatRequestDate(request.createdAt))}</span></div>
        </article>`;
    }).join("");
    container.querySelectorAll("[data-request-details]").forEach((button) => {
        button.addEventListener("click", () => {
            const details = document.getElementById(button.dataset.requestDetails);
            const expanded = button.getAttribute("aria-expanded") === "true";
            button.setAttribute("aria-expanded", String(!expanded));
            button.textContent = expanded ? "View details" : "Hide details";
            details.classList.toggle("d-none", expanded);
        });
    });
}

function urgencyClass(urgency) {
    const normalized = String(urgency || "").toLowerCase();
    return normalized === "critical" ? "urgency-critical" : normalized === "high" ? "urgency-high" : "urgency-normal";
}

function formatRequestDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "recently" : date.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

function titleCase(value) {
    return value.charAt(0) + value.slice(1).toLowerCase();
}

async function restoreSession() {
    try {
        await getCsrfToken();
        setCurrentUser(await api("/auth/me"));
    } catch (error) {
        clearSessionView();
    }
}

document.querySelector("#donorForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        const created = await api("/auth/register", {
            method: "POST",
            body: JSON.stringify(formData(form))
        });
        const loginEmail = document.querySelector('#loginForm input[name="email"]');
        const loginPassword = document.querySelector('#loginForm input[name="password"]');
        loginEmail.value = created.email;
        form.reset();
        setRegistrationRole(registrationRole);
        loginPassword.focus();
        log(`Account created for ${created.email}. Enter the password you just registered with to log in.`);
    } catch (error) {
        log("Registration failed", { error: error.message });
    }
});

document.querySelector("#requestForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        const request = await api("/blood-requests", {
            method: "POST",
            body: JSON.stringify(formData(event.currentTarget))
        });
        const matches = await api(`/blood-requests/${request.id}/match`, { method: "POST" });
        renderMatches(matches);
        if (currentUser?.role === "PATIENT") loadMyRequests();
        log("Blood request created and donors notified", { request, matches });
    } catch (error) {
        log("Request matching failed", { error: error.message });
    }
});

document.querySelector("#organRequestForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const feedback = document.querySelector("#organRequestFeedback");
    if (!form.reportValidity()) return;
    const button = document.querySelector("#submitOrganRequestBtn");
    button.disabled = true;
    feedback.textContent = "Submitting request…";
    try {
        await api("/organ-requests", { method: "POST", body: JSON.stringify(formData(form)) });
        form.reset();
        feedback.textContent = "Your request was submitted and is pending administrator review.";
        await loadMyRequests();
    } catch (error) {
        feedback.textContent = `Request could not be submitted: ${error.message}`;
    } finally {
        button.disabled = false;
    }
});

document.querySelector("#refreshMyRequestsBtn").addEventListener("click", loadMyRequests);
document.querySelector("#refreshOrganRequestsBtn").addEventListener("click", loadAdminOrganRequests);
document.querySelector("#verifyOrganRequestBtn").addEventListener("click", () => decideOrganRequest("verify"));
document.querySelector("#rejectOrganRequestBtn").addEventListener("click", () => decideOrganRequest("reject"));
document.querySelector("#cancelReviewBtn").addEventListener("click", () => document.querySelector("#organReviewDialog").close());

document.querySelector("#organForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        const preference = await api("/organs/preferences", {
            method: "POST",
            body: JSON.stringify(formData(event.currentTarget))
        });
        document.querySelector("#consentStatus").textContent = "Registered";
        document.querySelector("#consentStatusDot").classList.add("is-saved");
        log("Organ preference saved", preference);
    } catch (error) {
        log("Organ preference failed", { error: error.message });
    }
});

document.querySelector("#availabilityToggle").addEventListener("click", async (event) => {
    const button = event.currentTarget;
    if (!currentUser || currentUser.role !== "DONOR" || !currentUser.bloodDonor) return;
    const nextAvailability = !currentUser.availableForDonation;
    button.disabled = true;
    try {
        const updated = await api("/users/me/availability", {
            method: "PATCH",
            body: JSON.stringify({ available: nextAvailability })
        });
        currentUser = updated;
        setAvailabilityDisplay(updated);
        log(`Blood donation availability set to ${updated.availableForDonation ? "Available" : "Unavailable"}.`);
    } catch (error) {
        log("Availability could not be updated", { error: error.message });
    } finally {
        button.disabled = !currentUser?.bloodDonor;
    }
});

document.querySelector("#refreshRequestsBtn").addEventListener("click", async (event) => {
    const button = event.currentTarget;
    button.disabled = true;
    try {
        await refreshNearbyRequests();
        log("Nearby blood requests refreshed.");
    } catch (error) {
        log("Nearby requests could not be refreshed", { error: error.message });
    } finally {
        button.disabled = false;
    }
});

document.querySelector("#refreshBtn").addEventListener("click", refreshDashboard);
document.querySelector("#adminBloodRequestsViewAll").addEventListener("click", () => {
    adminBloodRequestsExpanded = !adminBloodRequestsExpanded;
    refreshDashboard();
});
document.querySelectorAll("[data-admin-nav]").forEach((link) => link.addEventListener("click", () => {
    document.querySelectorAll("[data-admin-nav]").forEach((item) => item.classList.toggle("active", item === link));
}));
document.querySelector("#demoMatchBtn").addEventListener("click", () => currentUser?.role === "PATIENT" ? loadPatientDonorMatches() : matchFirstOpenRequest());

function renderMatches(matches) {
    const table = document.querySelector("#matchesTable");
    if (!matches.length) {
        table.innerHTML = '<tr><td colspan="5" class="table-empty">No available compatible donors were found for this request. Donors need blood donation enabled, current availability, and a compatible blood group.</td></tr>';
        return;
    }
    table.innerHTML = matches.map((match) => `
        <tr>
            <td>${escapeHtml(match.fullName)}<br><small class="text-muted">${escapeHtml(match.city || "")}</small></td>
            <td>${formatBloodGroup(match.bloodGroup)}</td>
            <td>${match.distanceKm} km</td>
            <td>${match.score}</td>
            <td>${escapeHtml(match.email)}<br><small>${escapeHtml(match.phone || "")}</small></td>
        </tr>
    `).join("");
}

async function matchFirstOpenRequest() {
    try {
        const requests = await api("/blood-requests");
        const request = requests.find((item) => item.status === "OPEN" || item.status === "NOTIFIED") || requests[0];
        if (!request) {
            log("No open blood request found.");
            return;
        }
        const matches = await api(`/blood-requests/${request.id}/match`, { method: "POST" });
        renderMatches(matches);
        log("Blood request matched", { requestId: request.id, matches });
    } catch (error) {
        log("Matching failed", { error: error.message });
    }
}

async function loadPatientDonorMatches() {
    const table = document.querySelector("#matchesTable");
    table.innerHTML = '<tr><td colspan="5"><span class="table-loading">Finding compatible donors…</span></td></tr>';
    try {
        const requests = await api("/blood-requests");
        const openRequests = requests.filter((request) => ["OPEN", "NOTIFIED"].includes(request.status))
            .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
        if (!openRequests.length) {
            table.innerHTML = '<tr><td colspan="5" class="table-empty">Submit a blood request to see compatible donors from your area.</td></tr>';
            return;
        }
        const matches = await api(`/blood-requests/${Number(openRequests[0].id)}/matches`);
        renderMatches(matches);
    } catch (error) {
        table.innerHTML = '<tr><td colspan="5" class="table-error">Compatible donors could not be loaded. Please refresh.</td></tr>';
        log("Compatible donors could not be loaded", { error: error.message });
    }
}

clearSessionView();
restoreSession();
