import os
import unittest
from pathlib import Path

from playwright.sync_api import expect, sync_playwright


BASE_URL = os.environ.get("TEST_BASE_URL", "http://localhost:8080").rstrip("/")


class TerminalBrowserTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.playwright = sync_playwright().start()
        cls.browser = cls.playwright.chromium.launch()

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()

    def setUp(self):
        self.context = self.browser.new_context(viewport={"width": 1440, "height": 1000})
        self.page = self.context.new_page()
        self.page_errors = []
        self.page.on("pageerror", lambda error: self.page_errors.append(str(error)))

    def tearDown(self):
        self.context.close()
        self.assertEqual(self.page_errors, [], "Uncaught browser errors")

    def begin(self):
        self.page.goto(BASE_URL)
        self.page.get_by_role("link", name="Begin assessment").click()
        expect(self.page.get_by_role("heading", name="Before you begin")).to_be_visible()
        self.page.get_by_role("button", name="Start questions").click()

    def answer_all(self):
        for index in range(1, 22):
            expect(self.page.get_by_role("heading", name=f"Question {index} of 21", exact=True)).to_be_visible()
            self.page.get_by_role("radio", name="Strongly agree", exact=True).check()
            self.page.get_by_role("button", name="Review answers" if index == 21 else "Next question").click()
        expect(self.page.get_by_role("heading", name="Review your record")).to_be_visible()

    def assert_no_overflow(self):
        self.assertTrue(self.page.evaluate(
            "document.documentElement.scrollWidth <= window.innerWidth"
        ), "Page overflows horizontally")

    def screenshot(self, name):
        directory = os.environ.get("TEST_SCREENSHOT_DIR")
        if directory:
            path = Path(directory)
            path.mkdir(parents=True, exist_ok=True)
            self.page.screenshot(path=str(path / name), full_page=True)

    def test_theme_contrast_hidden_controls_and_native_status(self):
        self.page.goto(BASE_URL)
        expect(self.page.get_by_role("heading", name="Discover your S.P.E.C.I.A.L.")).to_be_visible()
        expect(self.page.locator("#message")).to_have_js_property("tagName", "OUTPUT")
        ratios = self.page.evaluate(r"""() => {
            const luminance = rgb => {
                const [r, g, b] = rgb.map(value => {
                    const channel = value / 255;
                    return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
                });
                return 0.2126 * r + 0.7152 * g + 0.0722 * b;
            };
            const colors = text => Array.from(text.matchAll(/rgba?\(([^)]+)\)/g),
                match => match[1].split(",").slice(0, 3).map(Number));
            const brightestBackground = selector => Math.max(...colors(
                getComputedStyle(document.querySelector(selector)).backgroundImage).map(luminance));
            const consoleBackground = brightestBackground(".console");
            const bodyBackground = brightestBackground("body");
            const ratios = [".brand small", ".hardware-label", ".attribute-strip span",
                ".console-footer", ".outside-note"].map(selector => {
                    const foreground = luminance(colors(getComputedStyle(document.querySelector(selector)).color)[0]);
                    const background = selector === ".outside-note" ? bodyBackground : consoleBackground;
                    return {selector, ratio: (foreground + 0.05) / (background + 0.05)};
                });
            for (const className of ["notice", "assessment-card", "option", "error"]) {
                const sample = document.createElement("p");
                sample.className = className;
                sample.textContent = "Contrast sample";
                if (className === "option") {
                    const input = document.createElement("input");
                    input.type = "checkbox";
                    input.checked = true;
                    sample.append(input);
                }
                document.querySelector("#view").append(sample);
                const style = getComputedStyle(sample);
                const foreground = luminance(colors(style.color)[0]);
                const background = luminance(colors(style.backgroundColor)[0]);
                ratios.push({selector: `.${className}`, ratio: (foreground + 0.05) / (background + 0.05)});
                sample.remove();
            }
            return ratios;
        }""")
        for result in ratios:
            self.assertGreaterEqual(result["ratio"], 4.5, result["selector"])
        for selector in ("a.button", "#account-nav", ".console-footer > span:last-child", ".screen"):
            node = self.page.locator(selector).first
            node.evaluate("(node) => { node.hidden = true; }")
            expect(node).to_be_hidden()
            node.evaluate("(node) => { node.hidden = false; }")

    def test_keyboard_journey_reopen_copy_and_retake(self):
        self.page.goto(BASE_URL)
        expect(self.page.get_by_role("heading", name="Discover your S.P.E.C.I.A.L.")).to_be_visible()
        self.screenshot("terminal-desktop.png")
        # The heading receives focus on navigation. Tab reaches the assessment link.
        self.page.keyboard.press("Tab")
        expect(self.page.get_by_role("link", name="Begin assessment")).to_be_focused()
        self.page.keyboard.press("Enter")
        expect(self.page.get_by_role("heading", name="Before you begin")).to_be_visible()
        self.page.keyboard.press("Tab")
        expect(self.page.get_by_role("button", name="Start questions")).to_be_focused()
        self.page.keyboard.press("Enter")
        for index in range(1, 22):
            expect(self.page.get_by_role("heading", name=f"Question {index} of 21", exact=True)).to_be_visible()
            self.page.keyboard.press("Tab")
            expect(self.page.get_by_role("radio", name="Strongly disagree", exact=True)).to_be_focused()
            self.page.keyboard.press("Space")
            for _ in range(4):
                self.page.keyboard.press("ArrowDown")
            expect(self.page.get_by_role("radio", name="Strongly agree", exact=True)).to_be_checked()
            self.page.keyboard.press("Tab")
            if index > 1:
                expect(self.page.get_by_role("button", name="Previous", exact=True)).to_be_focused()
                self.page.keyboard.press("Tab")
            self.page.keyboard.press("Enter")
        expect(self.page.get_by_role("heading", name="Review your record")).to_be_visible()
        # Keyboard-only review and privacy acknowledgement.
        for _ in range(22):
            self.page.keyboard.press("Tab")
        expect(self.page.get_by_role("checkbox")).to_be_focused()
        self.page.keyboard.press("Space")
        self.page.keyboard.press("Tab")
        self.page.keyboard.press("Tab")
        expect(self.page.get_by_role("button", name="Calculate & save record")).to_be_focused()
        self.page.keyboard.press("Enter")
        expect(self.page.get_by_role("heading", name="Your S.P.E.C.I.A.L.", exact=True)).to_be_visible()
        self.assertEqual(self.page.get_by_role("meter").count(), 7)
        for meter in self.page.get_by_role("meter").all():
            expect(meter).to_have_attribute("value", "10")
        result_url = self.page.url
        self.assertEqual(self.page.get_by_label("Unlisted result link").input_value(), result_url)
        self.screenshot("terminal-results.png")

        # A fresh browser session can reopen the unlisted result without cookies.
        other = self.browser.new_context()
        try:
            reopened = other.new_page()
            reopened.goto(result_url)
            expect(reopened.get_by_role("heading", name="Your S.P.E.C.I.A.L.", exact=True)).to_be_visible()
            self.assertEqual(reopened.get_by_role("meter").count(), 7)
        finally:
            other.close()

        self.context.grant_permissions(["clipboard-read", "clipboard-write"])
        self.page.get_by_role("button", name="Copy result link").click()
        expect(self.page.get_by_role("status")).to_contain_text("Result link copied")
        self.assertEqual(self.page.evaluate("navigator.clipboard.readText()"), result_url)
        self.page.get_by_role("link", name="Retake assessment").click()
        self.page.get_by_role("button", name="Start questions").click()
        expect(self.page.get_by_role("radio", checked=True)).to_have_count(0)
        self.answer_all()
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Calculate & save record").click()
        expect(self.page.get_by_role("heading", name="Your S.P.E.C.I.A.L.", exact=True)).to_be_visible()
        self.assertNotEqual(self.page.url, result_url)

    def test_mobile_validation_backtracking_failed_save_and_retry(self):
        self.page.set_viewport_size({"width": 360, "height": 800})
        self.begin()
        self.assert_no_overflow()
        self.page.get_by_role("button", name="Next question").click()
        expect(self.page.get_by_role("heading", name="Question 1 of 21", exact=True)).to_be_visible()
        expect(self.page.get_by_role("radio", checked=True)).to_have_count(0)
        self.screenshot("terminal-mobile.png")
        self.page.get_by_role("radio", name="Strongly agree", exact=True).check()
        self.page.get_by_role("button", name="Next question").click()
        self.page.get_by_role("button", name="Previous", exact=True).click()
        expect(self.page.get_by_role("radio", name="Strongly agree", exact=True)).to_be_checked()
        self.page.get_by_role("radio", name="Strongly disagree", exact=True).check()
        self.page.get_by_role("button", name="Next question").click()
        for index in range(2, 22):
            self.page.get_by_role("radio", name="Strongly agree", exact=True).check()
            self.page.get_by_role("button", name="Review answers" if index == 21 else "Next question").click()
        self.assert_no_overflow()
        self.page.get_by_role("button", name="Calculate & save record").click()
        expect(self.page.get_by_role("heading", name="Review your record")).to_be_visible()
        expect(self.page.get_by_role("checkbox")).not_to_be_checked()
        self.page.get_by_role("checkbox").check()
        self.page.route("**/api/v1/results", lambda route: route.fulfill(
            status=503, content_type="application/problem+json",
            body='{"detail":"Data storage is unavailable. Please try again later."}'
        ))
        self.page.get_by_role("button", name="Calculate & save record").click()
        expect(self.page.get_by_role("alert")).to_contain_text("Your answers are still here")
        expect(self.page.get_by_role("alert")).to_be_focused()
        expect(self.page.get_by_role("checkbox")).to_be_checked()
        self.assertEqual(self.page.locator(".review-answer").count(), 21)
        self.page.unroute("**/api/v1/results")
        self.page.get_by_role("button", name="Calculate & save record").click()
        expect(self.page.get_by_role("heading", name="Your S.P.E.C.I.A.L.", exact=True)).to_be_visible()
        expect(self.page.get_by_role("meter", name="Strength: 7 out of 10")).to_have_attribute("value", "7")
        self.assert_no_overflow()
        self.screenshot("terminal-results-mobile.png")
        # Explicit fallback instead of reporting a false clipboard success.
        self.page.evaluate("Object.defineProperty(navigator, 'clipboard', {value: undefined, configurable: true})")
        self.page.get_by_role("button", name="Copy result link").click()
        expect(self.page.get_by_role("status")).to_contain_text("Automatic copying is unavailable")
        expect(self.page.get_by_label("Unlisted result link")).to_be_focused()
        self.assertTrue(self.page.get_by_label("Unlisted result link").evaluate(
            "input => input.selectionEnd - input.selectionStart === input.value.length"
        ))

    def test_load_failures_empty_archive_and_invalid_routes(self):
        self.page.route("**/api/v1/assessments", lambda route: route.abort())
        self.page.goto(BASE_URL)
        expect(self.page.get_by_role("alert")).to_contain_text("Cannot reach the server")
        self.page.unroute("**/api/v1/assessments")
        self.page.get_by_role("button", name="Try again").click()
        expect(self.page.get_by_role("link", name="Begin assessment")).to_be_visible()
        self.page.route("**/api/v1/assessments", lambda route: route.fulfill(
            status=200, content_type="application/json", body="[]"
        ))
        self.page.reload()
        expect(self.page.get_by_text("No assessments are published yet.", exact=False)).to_be_visible()
        self.page.unroute("**/api/v1/assessments")
        self.page.get_by_role("button", name="Refresh archive").click()
        expect(self.page.get_by_role("link", name="Begin assessment")).to_be_visible()
        self.page.goto(f"{BASE_URL}/#/results/00000000-0000-0000-0000-000000000000")
        expect(self.page.get_by_role("heading", name="Record unavailable")).to_be_visible()
        expect(self.page.get_by_role("alert")).to_contain_text("Result not found")
        self.page.goto(f"{BASE_URL}/#/unknown")
        expect(self.page.get_by_role("heading", name="Unknown address")).to_be_visible()
        self.page.get_by_role("link", name="Return to archive").click()
        expect(self.page.get_by_role("link", name="Begin assessment")).to_be_visible()

    def test_leaving_assessment_warns_and_skip_link_preserves_answers(self):
        self.begin()
        self.page.get_by_role("radio", name="Agree", exact=True).check()
        self.page.keyboard.press("Control+Home")
        self.page.locator(".skip-link").focus()
        self.page.keyboard.press("Enter")
        expect(self.page.locator("#main")).to_be_focused()
        expect(self.page.get_by_role("radio", name="Agree", exact=True)).to_be_checked()
        self.page.once("dialog", lambda dialog: dialog.dismiss())
        self.page.get_by_role("link", name="S.P.E.C.I.A.L. terminal home").click()
        expect(self.page.get_by_role("radio", name="Agree", exact=True)).to_be_checked()
        self.page.once("dialog", lambda dialog: dialog.accept())
        self.page.get_by_role("link", name="S.P.E.C.I.A.L. terminal home").click()
        expect(self.page.get_by_role("link", name="Begin assessment")).to_be_visible()


if __name__ == "__main__":
    unittest.main()
