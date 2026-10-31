export function createQuizzes(ui) {
    const { view, element, button, link, heading, request, showError, announce,
        loading, loadFailure, route, isCurrent, setSaving, account, shareLink } = ui;
    let draft = null;
    let timer = null;
    let busy = false;
    let expiresAt = null;

    function stopTimer() {
        clearInterval(timer);
        timer = null;
    }

    function reset() {
        stopTimer();
        draft = null;
        expiresAt = null;
    }

    function expectedHeaders() {
        return { "X-Result-Privacy": account.expectation(), "X-Result-Account": account.identity() };
    }

    async function catalogue(token) {
        loading("Loading stat quizzes...");
        try {
            const quizzes = await request("/stat-quizzes");
            if (!isCurrent(token)) return;
            heading("Separate stat quizzes", "Seven attributes / Individual records");
            view.append(element("p",
                "Choose one stat at a time. These are original, unvalidated game assessments, not fitness diagnoses or IQ tests. Signed-in results update your character sheet; untested stats stay untested.",
                "notice"));
            for (const quiz of quizzes) {
                const card = element("article", undefined, "assessment-card");
                card.append(element("h2", quiz.title),
                    element("p", `${quiz.questions.length} questions / ${quiz.taskCount} tasks / ${
                        quiz.timeLimitSeconds ? `${quiz.timeLimitSeconds}-second task deadline` : "Untimed"}`, "metadata"),
                    link(`Open ${quiz.title} quiz`, `#/stat-quizzes/${quiz.stat}/${quiz.version}`, "button primary"));
                view.append(card);
            }
            view.append(link("Character sheet", "#/character-sheet"), link("Original assessment", "#/"));
        } catch (error) {
            if (isCurrent(token)) loadFailure(error, route, "Stat quizzes unavailable");
        }
    }

    async function briefing(stat, version, token) {
        loading("Preparing stat quiz...");
        try {
            const quiz = await request(`/stat-quizzes/${encodeURIComponent(stat)}/versions/${version}`);
            if (!isCurrent(token)) return;
            heading(`${quiz.title} quiz`, "Individual stat / Briefing");
            view.append(element("p", quiz.instructions, "notice"),
                element("p", account.privacy(), "notice"),
                element("p", `${quiz.taskWeightPercent}% tasks / ${100 - quiz.taskWeightPercent}% scored questions. ${
                    quiz.timeLimitSeconds ? "The timer starts only when you press Start quiz and cannot be paused. Questions after the tasks are untimed." : "There is no timer."
                } Original content repeats on retakes; scores are not comparable norms.`, "metadata"),
                element("p", quiz.source, "metadata"),
                element("p", "One active stat quiz per browser session. Starting another replaces it. Closing, reloading or leaving this page loses this draft; completed history is kept.", "metadata"));
            if (stat === "charisma") view.append(link("Optional private photo / local quality and iLook feedback", "#/photo"));
            view.append(button("Start quiz", async () => {
                if (busy) return;
                busy = true;
                const headers = expectedHeaders();
                if (!setSaving(true)) { busy = false; return; }
                view.inert = true;
                try {
                    const state = await request(`/stat-quizzes/${quiz.stat}/versions/${quiz.version}/attempts`,
                        { method: "POST", headers });
                    if (!isCurrent(token)) return;
                    draft = { quiz, state, answers: new Map(), index: 0, consent: false };
                    updateState(state);
                } catch (error) {
                    if (isCurrent(token)) showError(error.message);
                } finally {
                    busy = false;
                    setSaving(false);
                    view.inert = false;
                }
            }, "primary"), link("All stat quizzes", "#/stat-quizzes"));
        } catch (error) {
            if (isCurrent(token)) loadFailure(error, route, "Quiz unavailable");
        }
    }

    function updateState(state) {
        stopTimer();
        draft.state = state;
        expiresAt = state.deadline
            ? performance.now() + (new Date(state.deadline) - new Date(state.serverNow)) : null;
        if (state.task) showTask();
        else showQuestion();
    }

    async function mutateTask(task, option) {
        if (busy || !setSaving(true)) return;
        busy = true;
        const current = draft;
        view.inert = true;
        try {
            const state = await request(`/stat-quiz-attempts/${current.state.id}/answers`, {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ questionId: task.id, optionId: option.id })
            });
            if (current === draft) updateState(state);
        } catch (error) {
            if (current === draft) {
                view.inert = false;
                showError(`${error.message} Retry the same selection if the connection failed; it cannot count twice.`);
            }
        } finally {
            busy = false;
            setSaving(false);
            view.inert = false;
        }
    }

    async function refreshExpiredTask() {
        if (busy || !draft?.state.task) return;
        busy = true;
        const current = draft;
        stopTimer();
        try {
            const state = await request(`/stat-quiz-attempts/${current.state.id}`);
            if (current === draft) updateState(state);
        } catch (error) {
            if (current === draft) {
                showError(`${error.message} The server deadline still applies.`);
                view.append(button("Reconnect to quiz", async () => { await refreshExpiredTask(); }));
            }
        } finally {
            busy = false;
        }
    }

    function showTask() {
        const { quiz, state } = draft;
        const task = state.task;
        heading(`${quiz.title}: task ${state.answered + 1} of ${state.total}`, "Task section");
        if (state.feedback) view.append(element("p", state.feedback, "metadata"));
        if (expiresAt !== null) {
            const clock = element("p", undefined, "practice-timer");
            clock.setAttribute("role", "timer");
            clock.setAttribute("aria-live", "off");
            const tick = () => {
                const remaining = Math.max(0, Math.ceil((expiresAt - performance.now()) / 1000));
                clock.textContent = `Task time remaining: ${remaining} seconds`;
                if (remaining === 0) void refreshExpiredTask();
            };
            view.append(clock);
            tick();
            timer = setInterval(tick, 200);
        }
        view.append(element("p", task.prompt, "intro"));
        const choices = element("div", undefined, task.kind === "GRID" ? "target-grid" : "actions");
        for (const option of task.options) {
            const target = option.id === task.targetOptionId;
            choices.append(button(`${option.label}${target ? " / TARGET" : ""}`,
                async event => {
                    if (event.detail > 1) return;
                    await mutateTask(task, option);
                },
                task.kind === "GRID" ? `target-cell${target ? " marked" : ""}` : "secondary"));
        }
        view.append(choices, element("p",
            "Each selection is recorded immediately. No backtracking during tasks. Timing includes network delays and is not reaction time.",
            "metadata"));
    }

    function showQuestion() {
        stopTimer();
        const question = draft.quiz.questions[draft.index];
        heading(`${draft.quiz.title}: question ${draft.index + 1} of ${draft.quiz.questions.length}`, "Untimed reflection");
        if (draft.state.feedback) view.append(element("p", draft.state.feedback, "metadata"));
        if (draft.state.answered < draft.state.total) {
            view.append(element("p", "Task time has ended. Unanswered tasks count as misses. Continue with untimed questions.", "notice"));
        }
        if (!question.scored) view.append(element("p", "Context only: does not affect the score and is not saved. Prefer not to say is available.", "metadata"));
        const form = element("form");
        const fieldset = element("fieldset");
        fieldset.append(element("legend", question.prompt));
        for (const option of question.options) {
            const label = element("label", undefined, "option");
            const input = element("input");
            input.type = "radio";
            input.name = "stat-answer";
            input.value = option.id;
            input.required = true;
            input.checked = draft.answers.get(question.id) === option.id;
            input.addEventListener("change", () => { draft.answers.set(question.id, option.id); });
            label.append(input, element("span", option.label));
            fieldset.append(label);
        }
        const actions = element("div", undefined, "actions");
        const previous = button("Previous question", () => { draft.index--; showQuestion(); });
        previous.disabled = draft.index === 0;
        const next = element("button", draft.index === draft.quiz.questions.length - 1 ? "Review stat quiz" : "Next question", "primary");
        next.type = "submit";
        actions.append(previous, next);
        form.append(fieldset, actions);
        form.addEventListener("submit", event => {
            event.preventDefault();
            if (!draft.answers.has(question.id)) { showError("Choose an answer before continuing.", form); return; }
            if (draft.index === draft.quiz.questions.length - 1) review();
            else { draft.index++; showQuestion(); }
        });
        view.append(form);
    }

    function review() {
        heading("Review stat quiz", draft.quiz.title);
        view.append(element("p",
            `${draft.answers.size} questions answered. ${draft.state.answered} of ${draft.state.total} tasks attempted. Only summary scores are saved, not question answers or education/work context.`,
            "intro"));
        const form = element("form");
        const label = element("label", undefined, "option consent");
        const checkbox = element("input");
        checkbox.type = "checkbox";
        checkbox.required = true;
        checkbox.checked = draft.consent;
        checkbox.addEventListener("change", () => { draft.consent = checkbox.checked; });
        label.append(checkbox, element("span", `I understand this is an unvalidated game score, not a diagnosis or IQ. ${account.privacy()}`));
        const submit = element("button", "Save stat result", "primary");
        submit.type = "submit";
        const actions = element("div", undefined, "actions");
        actions.append(button("Revise questions", () => { draft.index = 0; showQuestion(); }), submit);
        form.append(label, actions);
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (draft.consent) await save(form);
        });
        view.append(form);
    }

    async function save(form) {
        if (busy || !setSaving(true)) return;
        busy = true;
        const current = draft;
        view.inert = true;
        announce("Saving your stat result...");
        try {
            const result = await request(`/stat-quiz-attempts/${current.state.id}/result`, {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ answers: [...current.answers].map(([questionId, optionId]) => ({ questionId, optionId })) })
            });
            if (current !== draft) return;
            reset();
            window.location.hash = `/stat-quizzes/results/${result.id}`;
        } catch (error) {
            if (current === draft) {
                view.inert = false;
                showError(`${error.message} Your answers remain on this page. Restore your original sign-in state to retry.`, form);
            }
        } finally {
            busy = false;
            setSaving(false);
            view.inert = false;
        }
    }

    async function result(id, token) {
        loading("Retrieving stat result...");
        try {
            const record = await request(`/stat-quiz-results/${id}`);
            const quiz = await request(`/stat-quizzes/${record.stat}/versions/${record.quizVersion}`);
            if (!isCurrent(token)) return;
            heading(`${record.title} result`, "Individual stat / Archived");
            view.append(element("p", `${record.score} / 10`, "practice-score"),
                element("p", `QUIZ V${record.quizVersion} / SCORING V${record.scoringVersion} / ${new Date(record.createdAt).toLocaleString()}`, "metadata"),
                element("p", `Reflection: ${record.surveyPercent}%. Tasks: ${record.tasksCorrect} of ${record.tasksTotal} correct / matches; ${record.tasksAnswered} attempted.`, "intro"),
                element("p", quiz.instructions, "notice"),
                element("p", `${quiz.taskWeightPercent}% tasks / ${100 - quiz.taskWeightPercent}% reflection. Game score only; not a diagnosis, IQ or prediction of future success.`, "metadata"),
                element("p", record.privateResult ? "Private result: only the saving account can view it. Your latest completed quiz for this stat updates your character sheet."
                    : "Anyone with this unlisted link can view this result. Anonymous results do not update a personal character sheet.", "notice"));
            if (!record.privateResult) shareLink(`/stat-quizzes/results/${record.id}`, "stat-result-link");
            const actions = element("div", undefined, "actions");
            actions.append(link("Retake stat quiz", `#/stat-quizzes/${record.stat}/${record.quizVersion}`),
                link("All stat quizzes", "#/stat-quizzes"));
            if (record.privateResult) actions.append(link("Character sheet", "#/character-sheet"), link("My results", "#/my-results"));
            view.append(actions);
        } catch (error) {
            if (isCurrent(token)) {
                loadFailure(error, route, "Stat result unavailable");
                view.append(link("Sign in to your account", "#/login"));
            }
        }
    }

    async function sheet(token) {
        if (!account.authenticated()) { account.signInPrompt(); return; }
        loading("Loading character sheet...");
        try {
            const entries = await request("/me/character-sheet");
            if (!isCurrent(token)) return;
            heading("Your character sheet", "Latest completed quiz / Per stat");
            view.append(element("p", "Each stat uses your latest saved individual quiz, not an average or your old combined assessment. Untested stats have no score. Older results stay in My results. Luck includes random outcomes; these are game scores, not comparable ability measurements.", "notice"));
            const grid = element("div", undefined, "score-grid");
            for (const entry of entries) {
                const row = element("section", undefined, "assessment-card");
                row.append(element("h2", entry.title),
                    element("p", entry.score === null ? "Untested" : `${entry.score} / 10`, "practice-score"));
                if (entry.resultId) {
                    row.append(element("p", `Recorded ${new Date(entry.createdAt).toLocaleString()}`, "metadata"),
                        link("View latest result", `#/stat-quizzes/results/${entry.resultId}`));
                }
                row.append(link(`Take ${entry.title} quiz`, `#/stat-quizzes/${entry.stat}/${entry.quizVersion}`));
                grid.append(row);
            }
            view.append(grid, link("All stat quizzes", "#/stat-quizzes"));
        } catch (error) {
            if (!isCurrent(token)) return;
            if (error.status === 401) account.signInPrompt();
            else loadFailure(error, route, "Character sheet unavailable");
        }
    }

    async function handle(hash, token) {
        if (hash === "#/stat-quizzes") { await catalogue(token); return true; }
        if (hash === "#/character-sheet") { await sheet(token); return true; }
        const record = /^#\/stat-quizzes\/results\/([0-9a-f-]{36})$/i.exec(hash);
        if (record) { await result(record[1], token); return true; }
        const quiz = /^#\/stat-quizzes\/([a-z]+)\/([1-9]\d*)$/.exec(hash);
        if (quiz) { await briefing(quiz[1], quiz[2], token); return true; }
        return false;
    }

    return { handle, reset, dirty: () => draft !== null };
}
