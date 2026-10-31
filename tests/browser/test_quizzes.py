import os
import re
import unittest
import uuid

from playwright.sync_api import expect, sync_playwright


BASE_URL = os.environ.get("TEST_BASE_URL", "http://localhost:8080").rstrip("/")


class StatQuizBrowserTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.playwright = sync_playwright().start()
        cls.browser = cls.playwright.chromium.launch()

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()

    def setUp(self):
        self.context = self.browser.new_context(viewport={"width": 390, "height": 844})
        self.page = self.context.new_page()
        self.errors = []
        self.page.on("pageerror", lambda error: self.errors.append(str(error)))

    def tearDown(self):
        self.context.close()
        self.assertEqual(self.errors, [])

    def start(self, stat):
        self.page.goto(f"{BASE_URL}/#/stat-quizzes/{stat}/1")
        expect(self.page.get_by_role("heading", name=f"{stat.title()} quiz", exact=True)).to_be_visible()
        self.page.get_by_role("button", name="Start quiz", exact=True).click()

    def questions(self, count, choose_last=True):
        for index in range(count):
            expect(self.page.get_by_role("heading", name=re.compile(f"question {index + 1} of {count}"))).to_be_visible()
            options = self.page.get_by_role("radio")
            (options.last if choose_last else options.first).check()
            self.page.get_by_role("button", name="Review stat quiz" if index == count - 1 else "Next question", exact=True).click()
        expect(self.page.get_by_role("heading", name="Review stat quiz", exact=True)).to_be_visible()

    def save(self):
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Save stat result", exact=True).click()
        expect(self.page.get_by_role("heading", name=re.compile(" result$"))).to_be_visible()

    def test_all_seven_catalogue_and_untimed_legacy_compatibility(self):
        self.page.goto(BASE_URL)
        self.page.get_by_role("link", name="Explore stat quizzes", exact=True).click()
        expect(self.page.get_by_role("heading", name="Separate stat quizzes", exact=True)).to_be_visible()
        expect(self.page.locator("#view .assessment-card")).to_have_count(7)
        for stat in ("strength", "endurance", "charisma"):
            self.start(stat)
            self.questions(6 if stat == "charisma" else 8)
            self.save()
            expect(self.page.get_by_text("10 / 10", exact=True)).to_be_visible()
            expect(self.page.get_by_role("button", name="Copy result link", exact=True)).to_be_visible()
            url = self.page.url
            other = self.browser.new_context()
            try:
                page = other.new_page()
                page.goto(url)
                expect(page.get_by_role("heading", name=f"{stat.title()} result", exact=True)).to_be_visible()
            finally:
                other.close()
        self.page.goto(f"{BASE_URL}/#/character-sheet")
        expect(self.page.get_by_role("heading", name="Sign in to continue", exact=True)).to_be_visible()

    def test_timed_visual_reasoning_coordination_and_random_games(self):
        for stat, tasks, questions in (("perception", 6, 4), ("intelligence", 10, 5),
                                       ("agility", 12, 4), ("luck", 6, 4)):
            self.start(stat)
            for index in range(tasks):
                expect(self.page.get_by_role("heading", name=f"{stat.title()}: task {index + 1} of {tasks}", exact=True)).to_be_visible()
                if stat != "luck":
                    expect(self.page.get_by_role("timer")).to_have_text(re.compile(r"Task time remaining: \d+ seconds"))
                if stat == "agility":
                    self.page.get_by_role("button", name=re.compile("TARGET$")).click()
                else:
                    self.page.locator("#view .actions button").first.click()
            if stat == "luck":
                expect(self.page.get_by_text("Random draw:", exact=False)).to_be_visible()
            self.questions(questions)
            self.save()
            expect(self.page.get_by_text(f"{tasks} attempted.", exact=False)).to_be_visible()
            self.assertTrue(self.page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))

    def test_private_sheet_latest_history_retry_and_account_boundary(self):
        username = "stat_" + uuid.uuid4().hex[:12]
        secret = uuid.uuid4().hex
        self.page.goto(f"{BASE_URL}/#/register")
        self.page.get_by_label("Username", exact=True).fill(username)
        self.page.get_by_label("Password", exact=True).fill(secret)
        self.page.get_by_label("Confirm password", exact=True).fill(secret)
        self.page.get_by_role("button", name="Create account", exact=True).click()
        expect(self.page.get_by_role("heading", name="Sign in", exact=True)).to_be_visible()
        self.page.get_by_label("Username", exact=True).fill(username)
        self.page.get_by_label("Password", exact=True).fill(secret)
        self.page.get_by_role("button", name="Sign in", exact=True).click()
        expect(self.page.get_by_role("heading", name="My results", exact=True)).to_be_visible()
        self.page.get_by_role("link", name="Character sheet", exact=True).first.click()
        expect(self.page.get_by_text("Untested", exact=True)).to_have_count(7)
        self.start("strength")
        self.questions(8, choose_last=False)
        self.save()
        first_url = self.page.url
        expect(self.page.get_by_role("button", name="Copy result link")).to_have_count(0)
        self.start("strength")
        self.questions(8)
        self.page.route("**/stat-quiz-attempts/*/result", lambda route: route.abort())
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Save stat result", exact=True).click()
        expect(self.page.get_by_role("alert")).to_contain_text("Your answers remain")
        self.page.unroute("**/stat-quiz-attempts/*/result")
        self.page.get_by_role("button", name="Save stat result", exact=True).click()
        expect(self.page.get_by_role("heading", name="Strength result", exact=True)).to_be_visible()
        self.page.get_by_role("link", name="Character sheet", exact=True).first.click()
        expect(self.page.get_by_text("10 / 10", exact=True)).to_be_visible()
        expect(self.page.get_by_text("Untested", exact=True)).to_have_count(6)
        self.page.get_by_role("link", name="My results", exact=True).first.click()
        expect(self.page.locator(".history-list > li")).to_have_count(2)
        self.page.locator(".history-list").get_by_role("link", name="Strength", exact=True).first.click()
        expect(self.page.get_by_role("heading", name="Strength result", exact=True)).to_be_visible()
        self.start("endurance")
        self.questions(8)
        self.context.clear_cookies()
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Save stat result", exact=True).click()
        expect(self.page.get_by_role("alert")).to_contain_text("Attempt expired")
        self.page.on("dialog", lambda dialog: dialog.accept())
        self.page.goto(first_url)
        expect(self.page.get_by_role("heading", name="Stat result unavailable", exact=True)).to_be_visible()

    def test_draft_warning_and_task_submission_retry_does_not_skip(self):
        self.start("luck")
        self.page.route("**/stat-quiz-attempts/*/answers", lambda route: route.abort())
        self.page.get_by_role("button", name="Heads", exact=True).click()
        expect(self.page.get_by_role("alert")).to_contain_text("cannot count twice")
        expect(self.page.get_by_role("heading", name="Luck: task 1 of 6", exact=True)).to_be_visible()
        self.page.unroute("**/stat-quiz-attempts/*/answers")
        self.page.get_by_role("button", name="Heads", exact=True).click()
        expect(self.page.get_by_role("heading", name="Luck: task 2 of 6", exact=True)).to_be_visible()
        self.page.once("dialog", lambda dialog: dialog.dismiss())
        self.page.get_by_role("link", name="My results", exact=True).click()
        expect(self.page.get_by_role("heading", name="Luck: task 2 of 6", exact=True)).to_be_visible()


if __name__ == "__main__":
    unittest.main()
