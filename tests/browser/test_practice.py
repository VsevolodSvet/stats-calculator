import os
import unittest

from playwright.sync_api import expect, sync_playwright


BASE_URL = os.environ.get("TEST_BASE_URL", "http://localhost:8080").rstrip("/")


class PracticeBrowserTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.playwright = sync_playwright().start()
        cls.browser = cls.playwright.chromium.launch()

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()

    def setUp(self):
        self.context = self.browser.new_context(viewport={"width": 360, "height": 800})
        self.page = self.context.new_page()
        self.errors = []
        self.page.on("pageerror", lambda error: self.errors.append(str(error)))

    def tearDown(self):
        self.context.close()
        self.assertEqual(self.errors, [])

    def open_activity(self, activity):
        self.page.goto(f"{BASE_URL}/#/practice/activities/{activity}/1")
        expect(self.page.get_by_role("button", name="Start practice", exact=True)).to_be_visible()

    def complete_choices(self):
        for index in range(1, 7):
            expect(self.page.get_by_role("heading", name=f"Practice item {index} of 6", exact=True)).to_be_visible()
            self.page.get_by_role("radio").first.check()
            self.page.get_by_role("button", name="Review practice" if index == 6 else "Next item", exact=True).click()

    def save(self):
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Save practice result", exact=True).click()
        expect(self.page.get_by_role("heading", name="Practice result", exact=True)).to_be_visible()

    def complete_targets(self, keyboard=False, miss_first=False):
        for index in range(1, 13):
            expect(self.page.get_by_role("heading", name=f"Practice item {index} of 12", exact=True)).to_be_visible()
            if keyboard:
                # Focus starts at the heading. Tab through native buttons to the marked cell.
                for _ in range(10):
                    self.page.keyboard.press("Tab")
                    if self.page.evaluate("document.activeElement.textContent.includes('TARGET')"):
                        break
                self.assertIn("TARGET", self.page.locator(":focus").inner_text())
                self.page.keyboard.press("Enter")
            elif index == 1 and miss_first:
                self.page.locator(".target-cell:not(.marked)").first.click()
            else:
                self.page.get_by_role("button", name="TARGET", exact=False).click()

    def test_catalogue_and_both_choice_activities_with_save_retry(self):
        self.page.goto(BASE_URL)
        self.page.get_by_role("link", name="Explore practice activities").click()
        expect(self.page.get_by_role("heading", name="Practice range", exact=True)).to_be_visible()
        expect(self.page.get_by_role("link", name="Open ", exact=False)).to_have_count(3)
        for activity in ["visual-matching", "reasoning"]:
            self.open_activity(activity)
            self.page.get_by_role("button", name="Start practice", exact=True).click()
            self.page.get_by_role("button", name="Next item", exact=True).click()
            expect(self.page.get_by_role("heading", name="Practice item 1 of 6", exact=True)).to_be_visible()
            self.page.get_by_role("radio").last.check()
            self.page.get_by_role("button", name="Next item", exact=True).click()
            self.page.get_by_role("button", name="Previous item", exact=True).click()
            expect(self.page.get_by_role("radio").last).to_be_checked()
            self.complete_choices()
            self.page.get_by_role("button", name="Save practice result", exact=True).click()
            expect(self.page.get_by_role("heading", name="Review practice", exact=True)).to_be_visible()
            self.page.route("**/api/v1/practice/results", lambda route: route.fulfill(
                status=503, content_type="application/problem+json",
                body='{"detail":"Practice storage unavailable."}'
            ))
            self.page.get_by_role("checkbox").check()
            self.page.get_by_role("button", name="Save practice result", exact=True).click()
            expect(self.page.get_by_role("alert")).to_contain_text("Your selections are still here")
            expect(self.page.get_by_role("checkbox")).to_be_checked()
            self.page.unroute("**/api/v1/practice/results")
            self.page.get_by_role("button", name="Save practice result", exact=True).click()
            expect(self.page.get_by_role("heading", name="Practice result", exact=True)).to_be_visible()
            expect(self.page.get_by_text("2 of 6 correct (33%)", exact=True)).to_be_visible()
            expect(self.page.get_by_text("Untimed - no duration recorded.", exact=True)).to_be_visible()
            expect(self.page.get_by_text("Separate record / S.P.E.C.I.A.L. unchanged", exact=True)).to_be_visible()
            saved_url = self.page.url
            other = self.browser.new_context()
            try:
                page = other.new_page()
                page.goto(saved_url)
                expect(page.get_by_role("heading", name="Practice result", exact=True)).to_be_visible()
            finally:
                other.close()
            self.page.reload()
            expect(self.page.get_by_role("heading", name="Practice result", exact=True)).to_be_visible()
            self.assertEqual(self.page.get_by_label("Unlisted result link").input_value(), saved_url)

    def test_coordination_untimed_keyboard_and_misses(self):
        self.open_activity("coordination")
        expect(self.page.get_by_role("radio", name="Untimed (default)", exact=False)).to_be_checked()
        self.page.get_by_role("button", name="Start practice", exact=True).click()
        expect(self.page.locator(".practice-timer")).to_have_count(0)
        self.complete_targets(keyboard=True)
        self.save()
        expect(self.page.get_by_text("12 of 12 correct (100%)", exact=True)).to_be_visible()
        expect(self.page.get_by_text("Mode: UNTIMED / Input: KEYBOARD (browser-reported)", exact=True)).to_be_visible()
        saved = self.page.url
        self.page.get_by_role("link", name="Practice again", exact=True).click()
        self.page.get_by_role("button", name="Start practice", exact=True).click()
        self.complete_targets(miss_first=True)
        self.save()
        expect(self.page.get_by_text("11 of 12 correct (92%)", exact=True)).to_be_visible()
        self.assertNotEqual(saved, self.page.url)
        self.assertTrue(self.page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))

    def test_timing_interruption_untimed_recovery_and_elapsed_result(self):
        self.open_activity("coordination")
        self.page.get_by_role("radio", name="Elapsed-time", exact=False).check()
        self.page.get_by_role("button", name="Start practice", exact=True).click()
        expect(self.page.locator(".practice-timer")).to_contain_text("Elapsed:")
        self.page.get_by_role("button", name="TARGET", exact=False).click()
        self.page.evaluate("window.dispatchEvent(new Event('blur'))")
        expect(self.page.get_by_role("heading", name="Timing interrupted", exact=True)).to_be_visible()
        self.page.get_by_role("button", name="Continue untimed", exact=True).click()
        expect(self.page.get_by_role("heading", name="Practice item 2 of 12", exact=True)).to_be_visible()
        for _ in range(11):
            self.page.get_by_role("button", name="TARGET", exact=False).click()
        self.save()
        expect(self.page.get_by_text("Untimed - no duration recorded.", exact=True)).to_be_visible()
        self.page.get_by_role("link", name="Practice again", exact=True).click()
        self.page.get_by_role("radio", name="Elapsed-time", exact=False).check()
        self.page.get_by_role("button", name="Start practice", exact=True).click()
        self.page.evaluate("window.dispatchEvent(new Event('blur'))")
        self.page.get_by_role("button", name="Restart practice", exact=True).click()
        expect(self.page.get_by_role("radio", name="Untimed (default)", exact=False)).to_be_checked()
        self.page.get_by_role("radio", name="Elapsed-time", exact=False).check()
        self.page.get_by_role("button", name="Start practice", exact=True).click()
        self.complete_targets()
        expect(self.page.get_by_text("Browser-reported elapsed time:", exact=False)).to_be_visible()
        self.save()
        expect(self.page.get_by_text("Mode: TIMED / Input: POINTER (browser-reported)", exact=True)).to_be_visible()
        expect(self.page.get_by_text("Elapsed time:", exact=False)).to_be_visible()
        self.assertEqual(self.page.get_by_role("meter").count(), 0)

    def test_practice_load_error_retry_unknown_record_and_draft_warning(self):
        self.page.route("**/api/v1/practice/activities", lambda route: route.abort())
        self.page.goto(f"{BASE_URL}/#/practice")
        expect(self.page.get_by_role("alert")).to_contain_text("Cannot reach")
        self.page.unroute("**/api/v1/practice/activities")
        self.page.get_by_role("button", name="Try again").click()
        expect(self.page.get_by_role("heading", name="Practice range", exact=True)).to_be_visible()
        self.open_activity("reasoning")
        self.page.get_by_role("button", name="Start practice", exact=True).click()
        self.page.get_by_role("radio").first.check()
        self.page.once("dialog", lambda dialog: dialog.dismiss())
        self.page.get_by_role("link", name="S.P.E.C.I.A.L. terminal home").click()
        expect(self.page.get_by_role("radio").first).to_be_checked()
        self.page.once("dialog", lambda dialog: dialog.accept())
        self.page.get_by_role("link", name="S.P.E.C.I.A.L. terminal home").click()
        expect(self.page.get_by_role("link", name="Explore practice activities")).to_be_visible()
        self.page.goto(f"{BASE_URL}/#/practice/results/00000000-0000-0000-0000-000000000000")
        expect(self.page.get_by_role("heading", name="Practice result unavailable", exact=True)).to_be_visible()
        expect(self.page.get_by_role("alert")).to_contain_text("Practice result not found")


if __name__ == "__main__":
    unittest.main()
