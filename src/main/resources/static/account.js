function clearCredentials(password, confirm) {
    password.value = "";
    if (confirm) confirm.value = "";
}

export function createAccount(ui) {
    const { view, element, button, link, heading, request, showError,
        loading, loadFailure, route, isCurrent, announce, canLeave } = ui;
    let session = null;
    let registrationComplete = false;
    let loginDestination = "#/my-results";
    const nav = document.querySelector("#account-nav");

    async function refresh(token) {
        const latest = await request("/auth/me");
        if (!isCurrent(token)) return;
        session = latest;
        nav.replaceChildren();
        if (session.authenticated) {
            nav.append(element("span", `SIGNED IN / ${session.username}`, "metadata"),
                link("My results", "#/my-results", "nav-link"),
                button("Sign out", async () => {
                    if (!canLeave()) return;
                    try {
                        await request("/auth/logout", { method: "POST" });
                        session = null;
                        if (window.location.hash === "#/") await route();
                        else window.location.hash = "/";
                    } catch (error) {
                        announce(`Sign out failed: ${error.message}`);
                    }
                }));
        } else {
            nav.append(link("Sign in", "#/login", "nav-link"),
                link("Create account", "#/register", "nav-link"),
                link("My results", "#/my-results", "nav-link"));
        }
    }

    function privacy() {
        return session?.authenticated
            ? "This result will be private to your signed-in account and appear in My results. Its link will not grant access to anyone else."
            : "You are continuing anonymously. Anyone with the unlisted result link can view it. Sign in before starting to save private results to My results.";
    }

    function expectation() {
        return session?.authenticated ? "PRIVATE" : "UNLISTED";
    }

    async function authenticate(register, username, password) {
        if (register) {
            await request("/auth/register", {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username, password })
            });
            registrationComplete = true;
            return "#/login";
        }
        await request("/auth/login", {
            method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams({ username, password })
        });
        const destination = loginDestination;
        loginDestination = "#/my-results";
        return destination;
    }

    async function submitAuth(event, register, { form, username, password, confirm, submit }) {
        event.preventDefault();
        if (confirm && confirm.value !== password.value) {
            showError("Passwords do not match.", form);
            return;
        }
        if (submit.disabled) return;
        submit.disabled = true;
        form.querySelector(".error")?.remove();
        try {
            const destination = await authenticate(register, username.value, password.value);
            clearCredentials(password, confirm);
            window.location.hash = destination;
        } catch (error) {
            if (!form.isConnected) return;
            clearCredentials(password, confirm);
            showError(error.message, form);
        } finally {
            submit.disabled = false;
        }
    }

    function authForm(register) {
        heading(register ? "Create your account" : "Sign in", "Personnel access");
        view.append(element("p", register
            ? "Use a unique username and a strong password. No email is collected; password recovery is not available yet."
            : "Sign in to view your history and save new results privately.", "intro"));
        const form = element("form", undefined, "auth-form");
        const username = element("input");
        username.id = "username";
        username.name = "username";
        username.type = "text";
        username.autocomplete = "username";
        username.required = true;
        username.minLength = 3;
        username.maxLength = 30;
        username.pattern = "[A-Za-z0-9_]{3,30}";
        username.autocapitalize = "none";
        username.spellcheck = false;
        const userLabel = element("label", "Username");
        userLabel.htmlFor = username.id;
        form.append(userLabel, username, element("p", "3-30 letters, numbers or underscores. Usernames are case-insensitive.", "metadata"));
        const password = element("input");
        password.id = "password";
        password.name = "password";
        password.type = "password";
        password.autocomplete = register ? "new-password" : "current-password";
        password.required = true;
        password.minLength = register ? 12 : 1;
        password.maxLength = 128;
        const passwordLabel = element("label", "Password");
        passwordLabel.htmlFor = password.id;
        form.append(passwordLabel, password);
        let confirm;
        if (register) {
            form.append(element("p", "12-128 characters. Use a password manager; do not reuse an important password on this local prototype.", "metadata"));
            confirm = element("input");
            confirm.id = "confirm-password";
            confirm.type = "password";
            confirm.autocomplete = "new-password";
            confirm.required = true;
            const confirmLabel = element("label", "Confirm password");
            confirmLabel.htmlFor = confirm.id;
            form.append(confirmLabel, confirm);
        }
        const actions = element("div", undefined, "actions");
        const submit = element("button", register ? "Create account" : "Sign in", "primary");
        submit.type = "submit";
        actions.append(submit, link(register ? "Already registered? Sign in" : "Create account",
            register ? "#/login" : "#/register"));
        form.append(actions);
        form.addEventListener("submit", async event => {
            await submitAuth(event, register, { form, username, password, confirm, submit });
        });
        view.append(form, link("Continue anonymously", "#/"));
        if (!register && registrationComplete) {
            registrationComplete = false;
            announce("Account created. Sign in with your new username and password.");
        }
    }

    function signInPrompt(destination = window.location.hash) {
        heading("Sign in to continue", "Private personnel archive");
        view.append(element("p", "Your personal history and private results require your account.", "notice"));
        const signIn = link("Sign in", "#/login", "button primary");
        signIn.addEventListener("click", () => { loginDestination = destination; });
        const actions = element("div", undefined, "actions");
        actions.append(signIn, link("Create account", "#/register"), link("Continue anonymously", "#/"));
        view.append(actions);
    }

    function historyRow(result) {
        const row = element("li");
        const hash = result.kind === "SPECIAL" ? `#/results/${result.id}` : `#/practice/results/${result.id}`;
        row.append(element("p", `${result.kind} / V${result.version} / ${new Date(result.createdAt).toLocaleString()}`, "metadata"),
            link(result.title, hash, "history-title"));
        if (result.kind === "PRACTICE") row.append(element("p", `${result.correct} of ${result.total} correct`, "metadata"));
        return row;
    }

    async function history(page, token) {
        if (!session?.authenticated) {
            signInPrompt();
            return;
        }
        loading("Loading personal results...");
        try {
            const data = await request(`/me/results?page=${page}&size=20`);
            if (!isCurrent(token)) return;
            heading("My results", `Private archive / ${session.username}`);
            view.append(element("p",
                "Only results saved while signed in appear here. Anonymous and older unlisted results remain accessible by their original links and are not claimed automatically.",
                "notice"));
            if (data.items.length === 0) view.append(element("p", "No results on this page. Start an assessment or practice activity to create a private record."));
            const list = element("ol", undefined, "history-list");
            list.append(...data.items.map(historyRow));
            view.append(list);
            const actions = element("div", undefined, "actions");
            if (page > 0) actions.append(link("Newer results", `#/my-results/${page - 1}`));
            if (data.hasNext) actions.append(link("Older results", `#/my-results/${page + 1}`));
            actions.append(link("Start assessment", "#/", "button primary"), link("Practice range", "#/practice"));
            view.append(element("p", `Page ${page + 1}`, "metadata"), actions);
        } catch (error) {
            if (isCurrent(token)) {
                if (error.status === 401) signInPrompt();
                else loadFailure(error, route, "Personal archive unavailable");
            }
        }
    }

    async function handle(hash, token) {
        if (hash === "#/login" || hash === "#/register") {
            if (session?.authenticated) await history(0, token);
            else authForm(hash === "#/register");
            return true;
        }
        const match = /^#\/my-results(?:\/(\d+))?$/.exec(hash);
        if (match) {
            await history(Number(match[1] || 0), token);
            return true;
        }
        return false;
    }

    return { refresh, handle, privacy, expectation, signInPrompt, identity: () => session?.username || "" };
}
