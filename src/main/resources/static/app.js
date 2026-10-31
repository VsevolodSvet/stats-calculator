import { createPractice } from "./practice.js";
import { createAccount } from "./account.js";

const view = document.querySelector("#view");
const message = document.querySelector("#message");
const attributes = ["strength", "perception", "endurance", "charisma", "intelligence", "agility", "luck"];
let navigation = 0;
let attempt = null;
let saving = false;
const account = createAccount({
    view, element, button, link, heading, request, showError, announce,
    loading, loadFailure, route, isCurrent: token => token === navigation, canLeave
});
const practice = createPractice({
    view, element, button, link, heading, request, showError, announce,
    loading, loadFailure, route, shareLink,
    privacy: account.privacy, expectation: account.expectation, identity: account.identity,
    isCurrent: token => token === navigation,
    setSaving: value => {
        if (value && saving) return false;
        saving = value;
        return true;
    }
});

function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
}

function button(text, action, className = "secondary") {
    const node = element("button", text, className);
    node.type = "button";
    node.addEventListener("click", action);
    return node;
}

function link(text, href, className = "button secondary") {
    const node = element("a", text, className);
    node.href = href;
    return node;
}

function heading(text, eyebrow) {
    message.replaceChildren();
    view.replaceChildren();
    view.setAttribute("aria-busy", "false");
    if (eyebrow) view.append(element("p", eyebrow, "eyebrow"));
    const title = element("h1", text);
    title.tabIndex = -1;
    view.append(title);
    title.focus();
    document.title = `${text} / S.P.E.C.I.A.L.`;
}

function announce(text) {
    message.textContent = text;
}

function showError(text, parent = view) {
    parent.querySelector(".error")?.remove();
    const error = element("p", text, "error");
    error.setAttribute("role", "alert");
    error.tabIndex = -1;
    parent.append(error);
    error.focus();
}

async function request(path, options = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 15000);
    try {
        const headers = { Accept: "application/json, application/problem+json", ...options.headers };
        if (options.method && !["GET", "HEAD"].includes(options.method)) {
            const csrfResponse = await fetch("/api/v1/auth/csrf", {
                credentials: "same-origin", signal: controller.signal, cache: "no-store"
            });
            if (!csrfResponse.ok) throw new Error("Cannot obtain a session token. Please reload and try again.");
            const csrf = await csrfResponse.json();
            headers[csrf.headerName] = csrf.token;
        }
        const response = await fetch(`/api/v1${path}`, {
            ...options, signal: controller.signal, credentials: "same-origin", cache: "no-store", headers
        });
        if (!response.ok) {
            const problem = response.headers.get("content-type")?.includes("json")
                ? await response.json() : null;
            const error = new Error(problem?.detail || `The terminal received HTTP ${response.status}. Please try again.`);
            error.status = response.status;
            throw error;
        }
        if (response.status === 204 || response.status === 201 && !response.headers.get("content-type")) return null;
        return await response.json();
    } catch (error) {
        if (error.name === "AbortError") {
            throw new Error("The server took too long to respond. Please check your connection and try again.");
        }
        if (error instanceof TypeError) {
            throw new Error("Cannot reach the server. Please check your connection and try again.");
        }
        throw error;
    } finally {
        clearTimeout(timer);
    }
}

function loading(text) {
    heading(text, "Archive connection");
    view.setAttribute("aria-busy", "true");
    view.append(element("p", "Please wait for the terminal to retrieve the record.", "intro"));
}

function loadFailure(error, retry, title = "Connection interrupted") {
    heading(title, "Archive notice");
    showError(error.message);
    const actions = element("div", undefined, "actions");
    actions.append(button("Try again", retry, "primary"), link("Return to archive", "#/"));
    view.append(actions);
}

async function home(token) {
    loading("Accessing the archive...");
    try {
        const assessments = await request("/assessments");
        if (token !== navigation) return;
        heading("Discover your S.P.E.C.I.A.L.", "Personal record / New entry");
        view.append(element("p",
            "Seven attributes. One character sheet. Take a moment to reflect on your everyday strengths, then create your own personnel record.",
            "intro"));
        view.append(element("p",
            "For curiosity, not classification. These self-reported scores are not validated measures of health, fitness, IQ, or future success. Luck reflects perceived opportunity.",
            "notice"));
        if (assessments.length === 0) {
            view.append(element("p", "No assessments are published yet. Please check back later."));
            view.append(button("Refresh archive", route));
        }
        for (const assessment of assessments) {
            const card = element("article", undefined, "assessment-card");
            card.append(element("p", `VERSION ${assessment.version} / ${assessment.questionCount} QUESTIONS / ABOUT ${assessment.estimatedMinutes} MIN`, "metadata"),
                element("h2", assessment.title), element("p", assessment.description, "intro"));
            const actions = element("div", undefined, "actions");
            actions.append(link("Begin assessment",
                `#/assessments/${encodeURIComponent(assessment.id)}/${assessment.version}`, "button primary"));
            card.append(actions);
            view.append(card);
        }
        const practiceCard = element("article", undefined, "assessment-card");
        practiceCard.append(element("p", "OPTIONAL / SEPARATE RESULTS", "metadata"),
            element("h2", "Practice range"),
            element("p", "Try visual-detail matching, reasoning puzzles, or target-selection coordination. These practice activities do not change your S.P.E.C.I.A.L. scores."));
        const practiceActions = element("div", undefined, "actions");
        practiceActions.append(link("Explore practice activities", "#/practice", "button primary"));
        practiceCard.append(practiceActions);
        view.append(practiceCard);
        view.append(element("p", account.privacy(), "metadata"));
    } catch (error) {
        if (token === navigation) loadFailure(error, route);
    }
}

async function startAssessment(id, version, token) {
    loading("Preparing your assessment...");
    try {
        const assessment = await request(`/assessments/${encodeURIComponent(id)}/versions/${version}`);
        if (token !== navigation) return;
        attempt = { assessment, answers: new Map(), index: 0, consent: false,
            privacy: account.expectation(), account: account.identity() };
        heading("Before you begin", "Assessment briefing");
        view.append(element("h2", assessment.title),
            element("p", assessment.instructions, "notice"),
            element("p", account.privacy(), "notice"),
            element("p", "Answer based on your usual experience during the past month. There are no right or wrong answers. Use Previous to revisit any question.", "intro"),
            element("p", "Your answers stay in this page's memory until submission. Reloading or leaving the assessment clears them. Raw answers are never saved to the server.", "metadata"));
        const actions = element("div", undefined, "actions");
        actions.append(button("Start questions", showQuestion, "primary"), link("Not now", "#/"));
        view.append(actions);
    } catch (error) {
        if (token === navigation) loadFailure(error, route, "Assessment unavailable");
    }
}

function progress() {
    const text = element("p", `${attempt.answers.size} of ${attempt.assessment.questions.length} answered`, "metadata");
    const bar = element("progress");
    bar.max = attempt.assessment.questions.length;
    bar.value = attempt.answers.size;
    bar.setAttribute("aria-label", "Questions answered");
    return [text, bar];
}

function showQuestion() {
    const { assessment, index } = attempt;
    const question = assessment.questions[index];
    heading(`Question ${index + 1} of ${assessment.questions.length}`, "Self-assessment / In progress");
    const [progressText, bar] = progress();
    view.append(progressText, bar);
    const form = element("form");
    const fieldset = element("fieldset");
    const legend = element("legend", question.prompt);
    fieldset.append(legend);
    for (const option of question.options) {
        const label = element("label", undefined, "option");
        const input = element("input");
        input.type = "radio";
        input.name = "answer";
        input.value = option.id;
        input.required = true;
        input.checked = attempt.answers.get(question.id) === option.id;
        input.addEventListener("change", () => {
            attempt.answers.set(question.id, option.id);
            progressText.textContent = `${attempt.answers.size} of ${assessment.questions.length} answered`;
            bar.value = attempt.answers.size;
            form.querySelector(".error")?.remove();
        });
        label.append(input, element("span", option.label));
        fieldset.append(label);
    }
    form.append(fieldset);
    const actions = element("div", undefined, "actions");
    const previous = button("Previous", () => {
        attempt.index--;
        showQuestion();
    });
    previous.disabled = index === 0;
    const next = element("button", index === assessment.questions.length - 1 ? "Review answers" : "Next question", "primary");
    next.type = "submit";
    actions.append(previous, next);
    form.append(actions);
    form.addEventListener("submit", event => {
        event.preventDefault();
        if (!attempt.answers.has(question.id)) {
            showError("Choose an answer before continuing.", form);
            return;
        }
        if (index === assessment.questions.length - 1) showReview();
        else {
            attempt.index++;
            showQuestion();
        }
    });
    view.append(form);
}

function showReview() {
    heading("Review your record", "Assessment complete / Not yet saved");
    view.append(...progress(),
        element("p", "Check your responses below. Select any question to revise it before saving.", "intro"));
    const list = element("ol", undefined, "review-list");
    attempt.assessment.questions.forEach((question, index) => {
        const item = element("li");
        const edit = link(`${index + 1}. ${question.prompt}`, "#review", "");
        edit.addEventListener("click", event => {
            event.preventDefault();
            attempt.index = index;
            showQuestion();
        });
        const option = question.options.find(value => value.id === attempt.answers.get(question.id));
        item.append(edit, element("span", option?.label || "Not answered", "review-answer"));
        list.append(item);
    });
    view.append(list);
    const form = element("form");
    const consent = element("label", undefined, "option consent");
    const checkbox = element("input");
    checkbox.type = "checkbox";
    checkbox.required = true;
    checkbox.checked = attempt.consent;
    checkbox.addEventListener("change", () => { attempt.consent = checkbox.checked; });
    consent.append(checkbox, element("span",
        `I understand these are playful self-reflection scores, not a diagnosis. Only scores are saved. ${account.privacy()}`));
    form.append(consent);
    const actions = element("div", undefined, "actions");
    actions.append(button("Back to questions", () => {
        attempt.index = attempt.assessment.questions.length - 1;
        showQuestion();
    }));
    const submit = element("button", "Calculate & save record", "primary");
    submit.type = "submit";
    actions.append(submit);
    form.append(actions);
    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (!attempt.consent || attempt.answers.size !== attempt.assessment.questions.length) {
            showError("Answer every question and acknowledge the sharing notice before saving.", form);
            return;
        }
        await save(form, submit);
    });
    view.append(form);
}

async function save(form, submit) {
    if (saving) return;
    saving = true;
    const token = navigation;
    form.querySelector(".error")?.remove();
    const controls = view.querySelectorAll("button, input");
    controls.forEach(control => { control.disabled = true; });
    view.querySelectorAll(".review-list a").forEach(anchor => { anchor.inert = true; });
    submit.textContent = "Saving record...";
    view.setAttribute("aria-busy", "true");
    announce("Calculating and saving your record. Please keep this page open.");
    try {
        const result = await request("/results", {
            method: "POST",
            headers: { "Content-Type": "application/json", "X-Result-Privacy": attempt.privacy,
                "X-Result-Account": attempt.account },
            body: JSON.stringify({
                assessmentId: attempt.assessment.id,
                assessmentVersion: attempt.assessment.version,
                answers: [...attempt.answers].map(([questionId, optionId]) => ({ questionId, optionId }))
            })
        });
        if (token !== navigation) return;
        attempt = null;
        window.location.hash = `/results/${result.id}`;
    } catch (error) {
        if (token !== navigation) return;
        announce("");
        showError(`${error.message} Your answers are still here. You can retry. If the response was lost after saving, retrying may create a second record.`, form);
    } finally {
        saving = false;
        if (token === navigation) {
            view.setAttribute("aria-busy", "false");
            controls.forEach(control => { control.disabled = false; });
            view.querySelectorAll(".review-list a").forEach(anchor => { anchor.inert = false; });
            submit.textContent = "Calculate & save record";
        }
    }
}

async function showResult(id, token) {
    loading("Retrieving personnel record...");
    try {
        const result = await request(`/results/${encodeURIComponent(id)}`);
        if (token !== navigation) return;
        heading("Your S.P.E.C.I.A.L.", "Personnel record / Archived");
        const date = new Date(result.createdAt).toLocaleString();
        view.append(element("p", `RECORDED ${date} / ASSESSMENT V${result.assessmentVersion} / SCORING V${result.scoringVersion}`, "metadata"));
        const stats = element("div", undefined, "score-grid");
        for (const attribute of attributes) {
            const name = attribute[0].toUpperCase() + attribute.slice(1);
            const score = result.scores[attribute];
            const stat = element("section", undefined, "stat");
            const letter = element("span", name[0], "stat-letter");
            letter.setAttribute("aria-hidden", "true");
            const detail = element("div");
            detail.append(element("h2", name));
            const meter = element("meter");
            meter.min = 1;
            meter.max = 10;
            meter.value = score;
            meter.setAttribute("aria-label", `${name}: ${score} out of 10`);
            detail.append(meter, element("p", result.explanations[attribute]));
            const value = element("span", String(score), "stat-value");
            value.setAttribute("aria-hidden", "true");
            value.append(element("small", " / 10"));
            stat.append(letter, detail, value);
            stats.append(stat);
        }
        view.append(stats, element("p", result.disclaimer, "notice"),
            element("p", result.sharingNotice, "metadata"));
        if (!result.privateResult) shareLink(`/results/${result.id}`, "result-link");
        const actions = element("div", undefined, "actions");
        actions.append(link("Retake assessment",
            `#/assessments/${encodeURIComponent(result.assessmentId)}/${result.assessmentVersion}`),
        link("All assessments", "#/"));
        if (result.privateResult) actions.append(link("My results", "#/my-results"));
        view.append(actions);
    } catch (error) {
        if (token === navigation) {
            loadFailure(error, route, "Record unavailable");
            view.append(link("Sign in to your account", "#/login"));
        }
    }
}

function shareLink(hash, id) {
    const label = element("label", "Unlisted result link", "share-label");
    label.htmlFor = id;
    const url = element("input", undefined, "share-input");
    url.id = id;
    url.type = "text";
    url.readOnly = true;
    const shareUrl = new URL(window.location.href);
    shareUrl.hash = hash;
    url.value = shareUrl.href;
    const actions = element("div", undefined, "actions");
    actions.append(button("Copy result link", async () => {
        try {
            if (!navigator.clipboard?.writeText) throw new Error("Clipboard unavailable");
            await navigator.clipboard.writeText(url.value);
            announce("Result link copied. Only share it with people who may see your summary.");
        } catch {
            url.focus();
            url.select();
            announce("Automatic copying is unavailable. The link is selected; use your browser's Copy command.");
        }
    }, "primary"));
    view.append(label, url, actions);
}

async function route() {
    const hash = window.location.hash;
    if (hash === "#main") {
        document.querySelector("#main").focus();
        return;
    }
    const token = ++navigation;
    attempt = null;
    practice.reset();
    loading("Checking terminal access...");
    try {
        await account.refresh(token);
    } catch (error) {
        if (token === navigation) loadFailure(error, route, "Session unavailable");
        return;
    }
    if (token !== navigation) return;
    const assessmentMatch = /^#\/assessments\/([^/]+)\/([1-9]\d*)$/.exec(hash);
    const resultMatch = /^#\/results\/([0-9a-f-]{36})$/i.exec(hash);
    if (await account.handle(hash, token) || token !== navigation) return;
    if (await practice.handle(hash, token) || token !== navigation) return;
    if (hash === "" || hash === "#/" || hash === "#") await home(token);
    else if (assessmentMatch) {
        let id;
        try {
            id = decodeURIComponent(assessmentMatch[1]);
        } catch {
            loadFailure(new Error("The assessment address is malformed. Return to the archive to begin."), route, "Unknown address");
            return;
        }
        await startAssessment(id, assessmentMatch[2], token);
    }
    else if (resultMatch) await showResult(resultMatch[1], token);
    else loadFailure(new Error("This terminal address is not recognized. Return to the archive to begin."), route, "Unknown address");
}

function canLeave() {
    if (saving) {
        announce("Please wait for saving to finish.");
        return false;
    }
    return !(attempt?.answers.size || practice.dirty())
        || window.confirm("Leave this assessment? Your unsaved answers will be cleared.");
}

window.addEventListener("beforeunload", event => {
    if (saving || attempt?.answers.size || practice.dirty()) event.preventDefault();
});

document.addEventListener("click", event => {
    const anchor = event.target.closest("a");
    if (!anchor) return;
    if (anchor.classList.contains("skip-link")) {
        event.preventDefault();
        document.querySelector("#main").focus();
    } else if (saving) {
        event.preventDefault();
        announce("Please wait for the save to finish before leaving this record.");
    } else if ((attempt?.answers.size || practice.dirty())
            && !["#review", "#practice-review", "#main"].includes(anchor.hash)) {
        if (!window.confirm("Leave this assessment? Your unsaved answers will be cleared.")) event.preventDefault();
    }
});

window.addEventListener("hashchange", () => { void route(); });
await route();
