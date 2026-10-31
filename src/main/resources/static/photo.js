export function createPhoto(ui) {
    const { view, element, button, link, heading, request, showError, announce,
        loading, loadFailure, route, isCurrent, setSaving, account } = ui;
    let previewUrl = null;
    let selected = false;
    let busy = false;

    function reset() {
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        previewUrl = null;
        selected = false;
    }

    function preview(blob, image) {
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        previewUrl = URL.createObjectURL(blob);
        image.src = previewUrl;
        image.hidden = false;
    }

    function feedback(metadata, parent) {
        parent.replaceChildren();
        if (!metadata.stored) {
            parent.append(element("p", "No photo is saved for this account.", "metadata"));
            return;
        }
        parent.append(element("p", `Stored ${new Date(metadata.updatedAt).toLocaleString()} / ${
            metadata.analysis.width} x ${metadata.analysis.height} pixels`, "metadata"));
        const list = element("ul");
        for (const text of metadata.analysis.feedback) list.append(element("li", text));
        parent.append(element("h2", "Local image-quality feedback"), list,
            element("p", `Whole-image brightness: ${metadata.analysis.brightnessPercent}%. Edge detail: ${
                metadata.analysis.edgeDetail} (uncalibrated heuristic, not a face or beauty score).`, "metadata"));
    }

    function externalFeedback(metadata, provider, headers) {
        const panel = element("section");
        panel.setAttribute("aria-label", "Optional Charisma photo feedback");
        panel.append(element("h2", "Optional Charisma photo feedback / iLook"),
            element("p", "Separate subjective appearance feedback, not a measure of personality, social skill or personal worth. It never changes your Charisma or other S.P.E.C.I.A.L. scores.", "notice"));
        if (!provider.available) {
            panel.append(element("p", "iLook is disabled. The administrator must obtain and configure an operator-issued API key. Local photo storage and quality feedback remain available.", "metadata"));
            return panel;
        }
        if (!metadata.stored) {
            panel.append(element("p", "Save a photo first, then confirm external processing separately.", "metadata"));
            return panel;
        }
        const form = element("form", undefined, "photo-form");
        const adultLabel = element("label", undefined, "option consent");
        const adult = element("input");
        adult.type = "checkbox";
        adult.required = true;
        adultLabel.append(adult, element("span", "The person pictured is 18 or older, and I am authorized to submit this photo to iLook."));
        const externalLabel = element("label", undefined, "option consent");
        const consent = element("input");
        consent.type = "checkbox";
        consent.required = true;
        externalLabel.append(consent, element("span", "I consent to sending this saved photo to iLook (Arctan Technologies Private Limited) for AI appearance analysis. iLook says API images are transient; downstream processing and exact deletion timing are not independently verified."));
        const policies = element("p");
        policies.append(link("iLook privacy policy", "https://www.ilook.fit/privacy"),
            link("iLook terms", "https://www.ilook.fit/terms"));
        const submit = element("button", "Analyze saved photo with iLook", "primary");
        submit.type = "submit";
        const result = element("section");
        result.setAttribute("aria-label", "iLook report");
        form.append(adultLabel, externalLabel, policies,
            element("p", "Only the normalized saved PNG is sent, without your account name. Results are displayed for this visit only, not saved in history. Reloading, replacing or deleting the photo clears this report. Each attempt uses provider quota; failures are not automatically retried.", "metadata"),
            submit);
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (!adult.checked || !consent.checked) {
                showError("Confirm adult authorization and external processing consent.", form);
                return;
            }
            result.replaceChildren();
            form.querySelector(".error")?.remove();
            await mutate(async () => {
                announce("Waiting for iLook photo feedback...");
                const report = await request("/me/photo/ilook", { method: "POST",
                    headers: { ...headers, "Content-Type": "application/json" },
                    body: JSON.stringify({ revision: metadata.revision, adultAndAuthorized: true, externalConsent: true }) },
                "json", 60000);
                result.append(element("h3", `iLook subjective appearance score: ${report.overallScore}/100`),
                    element("p", report.summary));
                const list = element("ul");
                for (const signal of report.signals) {
                    list.append(element("li", `${signal.name.replaceAll("_", " ")}: ${signal.score}/10 - ${signal.explanation}`));
                }
                result.append(list, element("p", report.disclaimer, "notice"),
                    element("p", `Confidence: ${report.confidence}. Model: ${report.model}. Rubric: ${report.rubricVersion}. Analysis: ${report.analysisId}.`, "metadata"));
                adult.checked = false;
                consent.checked = false;
                announce("iLook feedback received. Your Charisma score is unchanged.");
            }, form, "No report was saved. An attempted request may have used provider quota.");
        });
        panel.append(form, result);
        return panel;
    }

    async function show(token) {
        if (!account.authenticated()) { account.signInPrompt(); return; }
        const identity = account.identity();
        const headers = { "X-Result-Account": identity };
        loading("Loading your private photo...");
        try {
            const metadata = await request("/me/photo", { headers });
            const provider = await request("/me/photo/ilook", { headers });
            const blob = metadata.stored ? await request("/me/photo/image", { headers }, "blob") : null;
            if (!isCurrent(token)) return;
            heading("Private profile photo", `Photo presentation / ${identity}`);
            view.append(element("p",
                "Optional: save one photo privately in this application's PostgreSQL database. Saving only checks whole-image lighting, detail and resolution locally. Nothing is sent externally unless you separately request iLook analysis below. Photo feedback never changes S.P.E.C.I.A.L. scores.",
                "notice"),
                element("p", "Use your own photo or one you have permission to store. JPEG/PNG only, up to 5 MiB, 6000 pixels per side and 12 megapixels. The server strips metadata, flattens transparency and stores a resized PNG (up to 1200 pixels per side). Check the preview's orientation before saving; the server does not apply EXIF rotation.", "metadata"));
            const image = element("img", undefined, "profile-photo");
            image.alt = "Your private profile photo preview";
            image.hidden = true;
            if (blob) preview(blob, image);
            const report = element("section");
            report.setAttribute("aria-label", "Photo feedback");
            feedback(metadata, report);
            const external = externalFeedback(metadata, provider, headers);
            const form = element("form", undefined, "photo-form");
            const fileLabel = element("label", "Choose a photo");
            const file = element("input");
            file.id = "profile-photo-file";
            file.type = "file";
            file.accept = "image/jpeg,image/png";
            file.required = true;
            fileLabel.htmlFor = file.id;
            const consentLabel = element("label", undefined, "option consent");
            const consent = element("input");
            consent.type = "checkbox";
            consent.required = true;
            consentLabel.append(consent, element("span",
                "I have permission to store this photo. I consent to private storage in this application's database until I replace or delete it. No third-party processing."));
            file.addEventListener("change", () => {
                consent.checked = false;
                const candidate = file.files[0];
                selected = Boolean(candidate);
                if (!candidate) return;
                external.hidden = true;
                if (!["image/jpeg", "image/png"].includes(candidate.type) || candidate.size > 5 * 1024 * 1024) {
                    showError("Choose a JPEG or PNG up to 5 MiB.", form);
                    file.value = "";
                    selected = false;
                    return;
                }
                preview(candidate, image);
                report.replaceChildren(element("p", "Unsaved preview. Feedback below will be recalculated only after saving.", "metadata"));
            });
            const actions = element("div", undefined, "actions");
            const submit = element("button", metadata.stored ? "Replace private photo" : "Save private photo", "primary");
            submit.type = "submit";
            const remove = button("Delete stored photo", async () => {
                if (busy || !window.confirm("Delete your stored photo? Copies in existing backups are not removed.")) return;
                await mutate(async () => {
                    await request("/me/photo", { method: "DELETE", headers });
                    selected = false;
                    await show(token);
                    if (isCurrent(token)) announce("Stored photo deleted from the active database.");
                }, form);
            });
            remove.disabled = !metadata.stored;
            actions.append(submit, remove);
            form.append(fileLabel, file, consentLabel, actions);
            form.addEventListener("submit", async event => {
                event.preventDefault();
                const candidate = file.files[0];
                if (!candidate || !consent.checked) { showError("Choose a photo and confirm storage consent.", form); return; }
                await mutate(async () => {
                    await request("/me/photo", { method: "POST",
                        headers: { ...headers, "Content-Type": candidate.type, "X-Photo-Consent": "true" },
                        body: candidate });
                    selected = false;
                    await show(token);
                    if (isCurrent(token)) announce("Private photo saved. No third-party processing was used.");
                }, form);
            });
            view.append(image, report, form, external,
                element("p", "Replace overwrites the active photo. Delete removes it from the active database; existing database backups may retain earlier copies. Storage is not application-level encrypted. Protect the local machine and database. Do not upload sensitive images to this prototype.", "notice"),
                link("Character sheet", "#/character-sheet"), link("Charisma quiz", "#/stat-quizzes/charisma/1"));
        } catch (error) {
            if (!isCurrent(token)) return;
            if (error.status === 401) account.signInPrompt();
            else loadFailure(error, route, "Private photo unavailable");
        }
    }

    async function mutate(action, form, failureNote = "Your selected file is still available for retry.") {
        if (busy || !setSaving(true)) return;
        busy = true;
        view.inert = true;
        try {
            await action();
        } catch (error) {
            if (form.isConnected) {
                view.inert = false;
                announce("The action could not be completed.");
                showError(`${error.message} ${failureNote}`, form);
            }
        } finally {
            busy = false;
            view.inert = false;
            setSaving(false);
        }
    }

    async function handle(hash, token) {
        if (hash !== "#/photo") return false;
        await show(token);
        return true;
    }

    return { handle, reset, dirty: () => selected };
}
