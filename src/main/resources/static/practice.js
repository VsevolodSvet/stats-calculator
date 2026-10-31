export function createPractice(ui) {
    const { view, element, button, link, heading, request, showError, announce,
        loading, loadFailure, route, isCurrent, setSaving, privacy, expectation, identity } = ui;
    let draft = null;
    let clock = null;

    function stopClock() {
        clearInterval(clock);
        clock = null;
    }

    function reset() {
        stopClock();
        draft = null;
    }

    async function catalogue(token) {
        loading("Loading practice activities...");
        try {
            const activities = await request("/practice/activities");
            if (!isCurrent(token)) return;
            heading("Practice range", "Separate activities / No S.P.E.C.I.A.L. changes");
            view.append(element("p",
                "Try short visual-detail, reasoning and coordination exercises. Results stay separate from your character sheet. There are no ability rankings or comparisons between people.",
                "notice"));
            if (activities.length === 0) {
                view.append(element("p", "No practice activities are published yet."),
                    button("Refresh activities", route));
            }
            for (const activity of activities) {
                const card = element("article", undefined, "assessment-card");
                card.append(element("p", `VERSION ${activity.version} / ${activity.itemCount} ITEMS / ${
                    activity.coordination ? "UNTIMED OR ELAPSED-TIME" : "UNTIMED"}`, "metadata"),
                element("h2", activity.title), element("p", activity.description));
                const actions = element("div", undefined, "actions");
                actions.append(link(`Open ${activity.title}`,
                    `#/practice/activities/${encodeURIComponent(activity.id)}/${activity.version}`, "button primary"));
                card.append(actions);
                view.append(card);
            }
            view.append(link("Back to S.P.E.C.I.A.L.", "#/"));
        } catch (error) {
            if (isCurrent(token)) loadFailure(error, route, "Practice archive unavailable");
        }
    }

    async function start(id, version, token) {
        loading("Preparing practice...");
        try {
            const activity = await request(`/practice/activities/${encodeURIComponent(id)}/versions/${version}`);
            if (!isCurrent(token)) return;
            draft = { activity, selections: new Map(), index: 0, mode: "UNTIMED",
                inputMethods: new Set(), startedAt: null, elapsedMs: null, interrupted: false, consent: false,
                privacy: expectation(), account: identity() };
            briefing();
        } catch (error) {
            if (isCurrent(token)) loadFailure(error, route, "Practice unavailable");
        }
    }

    function briefing() {
        heading(draft.activity.title, "Practice briefing / No score conversion");
        view.append(element("p", draft.activity.instructions, "notice"),
            element("p",
                `Only the summary is saved, not your individual selections. ${privacy()} Drafts are lost when you reload or leave.`,
                "metadata"));
        if (draft.activity.coordination) {
            const modes = element("fieldset");
            modes.append(element("legend", "Choose a comfortable practice mode"));
            for (const [value, text] of [
                ["UNTIMED", "Untimed (default) - no clock or time pressure"],
                ["TIMED", "Elapsed-time - no deadline; browser-reported duration"]
            ]) {
                const label = element("label", undefined, "option");
                const input = element("input");
                input.type = "radio";
                input.name = "practice-mode";
                input.value = value;
                input.checked = draft.mode === value;
                input.addEventListener("change", () => { draft.mode = value; });
                label.append(input, element("span", text));
                modes.append(label);
            }
            view.append(modes);
        }
        const actions = element("div", undefined, "actions");
        actions.append(button("Start practice", () => {
            draft.startedAt = performance.now();
            showItem();
        }, "primary"), link("Choose another activity", "#/practice"));
        view.append(actions);
    }

    function recordInput(event) {
        draft.inputMethods.add(event.detail === 0 ? "KEYBOARD" : "POINTER");
    }

    function showItem() {
        stopClock();
        const { activity, index } = draft;
        const item = activity.items[index];
        heading(`Practice item ${index + 1} of ${activity.items.length}`, activity.title);
        const bar = element("progress");
        bar.max = activity.items.length;
        bar.value = draft.selections.size;
        bar.setAttribute("aria-label", "Practice items completed");
        view.append(element("p", `${draft.selections.size} of ${activity.items.length} completed / ${draft.mode}`, "metadata"), bar);
        if (activity.coordination) showGrid(item);
        else showChoices(item);
    }

    function advance() {
        if (draft.index === draft.activity.items.length - 1) {
            draft.elapsedMs = draft.mode === "TIMED" ? Math.max(1, Math.round(performance.now() - draft.startedAt)) : null;
            stopClock();
            review();
        } else {
            draft.index++;
            showItem();
        }
    }

    function showChoices(item) {
        const form = element("form");
        const fieldset = element("fieldset");
        fieldset.append(element("legend", item.prompt));
        for (const option of item.options) {
            const label = element("label", undefined, "option");
            const input = element("input");
            input.type = "radio";
            input.name = "practice-answer";
            input.required = true;
            input.checked = draft.selections.get(item.id) === option.id;
            input.addEventListener("click", recordInput);
            input.addEventListener("keydown", event => {
                if (event.key.startsWith("Arrow")) draft.inputMethods.add("KEYBOARD");
            });
            input.addEventListener("change", () => { draft.selections.set(item.id, option.id); });
            label.append(input, element("span", option.label));
            fieldset.append(label);
        }
        form.append(fieldset);
        const actions = element("div", undefined, "actions");
        const previous = button("Previous item", () => { draft.index--; showItem(); });
        previous.disabled = draft.index === 0;
        const next = element("button", draft.index === draft.activity.items.length - 1 ? "Review practice" : "Next item", "primary");
        next.type = "submit";
        actions.append(previous, next);
        form.append(actions);
        form.addEventListener("submit", event => {
            event.preventDefault();
            if (!draft.selections.has(item.id)) {
                showError("Select an option before continuing.", form);
                return;
            }
            advance();
        });
        view.append(form);
    }

    function showGrid(item) {
        view.append(element("p", item.prompt, "intro"),
            element("p", "Select one cell. A miss also advances the round. Use pointer, touch, or Tab and Enter/Space.", "metadata"));
        if (draft.mode === "TIMED") {
            const timer = element("p", undefined, "practice-timer");
            timer.setAttribute("aria-hidden", "true");
            const tick = () => { timer.textContent = `Elapsed: ${((performance.now() - draft.startedAt) / 1000).toFixed(1)} s`; };
            tick();
            clock = setInterval(tick, 250);
            view.append(timer);
        }
        const grid = element("div", undefined, "target-grid");
        grid.setAttribute("role", "group");
        grid.setAttribute("aria-label", "Nine-cell target grid");
        for (const option of item.options) {
            const target = option.id === item.targetOptionId;
            const cell = button(`${option.label}${target ? " / TARGET" : ""}`, event => {
                if (event.detail > 1 || draft.interrupted) return;
                recordInput(event);
                draft.selections.set(item.id, option.id);
                advance();
            }, target ? "target-cell marked" : "target-cell");
            grid.append(cell);
        }
        view.append(grid);
    }

    function interrupt() {
        if (draft?.mode !== "TIMED" || draft.startedAt === null
                || draft.elapsedMs !== null || draft.interrupted) return;
        stopClock();
        draft.interrupted = true;
        heading("Timing interrupted", "Practice paused / No result submitted");
        view.append(element("p",
            "The tab or window lost focus. To avoid a misleading time, continue without timing or restart. Your completed selections are still here.",
            "notice"));
        const actions = element("div", undefined, "actions");
        actions.append(button("Continue untimed", () => {
            draft.mode = "UNTIMED";
            draft.interrupted = false;
            draft.startedAt = null;
            showItem();
        }, "primary"), button("Restart practice", () => {
            const activity = draft.activity;
            reset();
            draft = { activity, selections: new Map(), index: 0, mode: "UNTIMED",
                inputMethods: new Set(), startedAt: null, elapsedMs: null, interrupted: false, consent: false,
                privacy: expectation(), account: identity() };
            briefing();
        }));
        view.append(actions);
    }

    document.addEventListener("visibilitychange", () => {
        if (document.hidden) interrupt();
    });
    window.addEventListener("blur", interrupt);

    function review() {
        heading("Review practice", "Complete / Not yet saved");
        view.append(element("h2", draft.activity.title),
            element("p", `${draft.selections.size} items completed. Mode: ${draft.mode}.`, "intro"));
        if (draft.elapsedMs !== null) {
            view.append(element("p", `Browser-reported elapsed time: ${(draft.elapsedMs / 1000).toFixed(2)} seconds.`, "metadata"));
        }
        if (!draft.activity.coordination) {
            const list = element("ol", undefined, "review-list");
            draft.activity.items.forEach((item, index) => {
                const row = element("li");
                const edit = link(item.prompt, "#practice-review", "");
                edit.addEventListener("click", event => {
                    event.preventDefault();
                    draft.index = index;
                    showItem();
                });
                row.append(edit, element("span",
                    item.options.find(option => option.id === draft.selections.get(item.id))?.label,
                    "review-answer"));
                list.append(row);
            });
            view.append(list);
        }
        const form = element("form");
        const label = element("label", undefined, "option consent");
        const checkbox = element("input");
        checkbox.type = "checkbox";
        checkbox.required = true;
        checkbox.checked = draft.consent;
        checkbox.addEventListener("change", () => { draft.consent = checkbox.checked; });
        label.append(checkbox, element("span",
            `I understand this is unvalidated practice, separate from S.P.E.C.I.A.L. My summary includes timing and input method. ${privacy()}`));
        const submit = element("button", "Save practice result", "primary");
        submit.type = "submit";
        const actions = element("div", undefined, "actions");
        actions.append(submit);
        form.append(label, actions);
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (draft.consent) await save(form, submit);
        });
        view.append(form);
    }

    async function save(form, submit) {
        if (!setSaving(true)) return;
        const current = draft;
        form.querySelector(".error")?.remove();
        view.inert = true;
        view.setAttribute("aria-busy", "true");
        submit.textContent = "Saving practice...";
        announce("Saving the separate practice summary. Keep this page open.");
        const inputMethod = current.inputMethods.size > 1 ? "MIXED" : [...current.inputMethods][0];
        try {
            const result = await request("/practice/results", {
                method: "POST", headers: { "Content-Type": "application/json", "X-Result-Privacy": current.privacy,
                    "X-Result-Account": current.account },
                body: JSON.stringify({
                    activityId: current.activity.id, activityVersion: current.activity.version,
                    mode: current.mode, inputMethod, elapsedMs: current.elapsedMs,
                    selections: [...current.selections].map(([itemId, optionId]) => ({ itemId, optionId }))
                })
            });
            if (current !== draft) return;
            reset();
            window.location.hash = `/practice/results/${result.id}`;
        } catch (error) {
            if (current !== draft) return;
            view.inert = false;
            announce("");
            showError(`${error.message} Your selections are still here. Retrying after a lost response may create a second result.`, form);
        } finally {
            setSaving(false);
            view.inert = false;
            if (current === draft) {
                view.setAttribute("aria-busy", "false");
                submit.textContent = "Save practice result";
            }
        }
    }

    async function result(id, token) {
        loading("Retrieving practice result...");
        try {
            const record = await request(`/practice/results/${encodeURIComponent(id)}`);
            if (!isCurrent(token)) return;
            heading("Practice result", "Separate record / S.P.E.C.I.A.L. unchanged");
            view.append(element("h2", record.title),
                element("p", `ACTIVITY V${record.activityVersion} / SCORING V${record.scoringVersion} / ${new Date(record.createdAt).toLocaleString()}`, "metadata"),
                element("p", `${record.correct} of ${record.total} correct (${record.accuracyPercent}%)`, "practice-score"),
                element("p", `Mode: ${record.mode} / Input: ${record.inputMethod} (browser-reported)`, "metadata"),
                element("p", record.elapsedMs === null ? "Untimed - no duration recorded."
                    : `Elapsed time: ${(record.elapsedMs / 1000).toFixed(2)} seconds (browser-reported).`, "intro"),
                element("p", record.disclaimer, "notice"), element("p", record.sharingNotice, "metadata"));
            if (!record.privateResult) ui.shareLink(`/practice/results/${record.id}`, "practice-result-link");
            const actions = element("div", undefined, "actions");
            actions.append(link("Practice again",
                `#/practice/activities/${encodeURIComponent(record.activityId)}/${record.activityVersion}`, "button primary"),
            link("All practice activities", "#/practice"), link("S.P.E.C.I.A.L. archive", "#/"));
            if (record.privateResult) actions.append(link("My results", "#/my-results"));
            view.append(actions);
        } catch (error) {
            if (isCurrent(token)) {
                loadFailure(error, route, "Practice result unavailable");
                view.append(link("Sign in to your account", "#/login"));
            }
        }
    }

    async function handle(hash, token) {
        if (hash === "#/practice") {
            await catalogue(token);
            return true;
        }
        const activity = /^#\/practice\/activities\/([^/]+)\/([1-9]\d*)$/.exec(hash);
        const saved = /^#\/practice\/results\/([0-9a-f-]{36})$/i.exec(hash);
        if (activity) {
            let id;
            try {
                id = decodeURIComponent(activity[1]);
            } catch {
                loadFailure(new Error("Malformed practice activity address."), route, "Unknown address");
                return true;
            }
            await start(id, activity[2], token);
            return true;
        }
        if (saved) {
            await result(saved[1], token);
            return true;
        }
        return false;
    }

    return { handle, reset, dirty: () => Boolean(draft?.startedAt != null || draft?.selections.size) };
}
