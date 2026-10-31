import os
import re
import unittest
import uuid

from playwright.sync_api import expect, sync_playwright


BASE_URL = os.environ.get("TEST_BASE_URL", "http://localhost:8080").rstrip("/")
PASSWORD = "local browser test password"


class AccountBrowserTest(unittest.TestCase):
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

    def register(self, username):
        self.page.goto(f"{BASE_URL}/#/register")
        self.page.get_by_label("Username", exact=True).fill(username)
        self.page.get_by_label("Password", exact=True).fill(PASSWORD)
        self.page.get_by_label("Confirm password", exact=True).fill(PASSWORD)
        self.page.get_by_role("button", name="Create account", exact=True).click()
        expect(self.page.get_by_role("heading", name="Sign in", exact=True)).to_be_visible()
        expect(self.page.get_by_role("status")).to_contain_text("Account created")

    def login(self, username, password=PASSWORD):
        self.page.get_by_label("Username", exact=True).fill(username)
        self.page.get_by_label("Password", exact=True).fill(password)
        self.page.get_by_role("button", name="Sign in", exact=True).click()

    def complete_practice(self):
        self.page.goto(f"{BASE_URL}/#/practice/activities/reasoning/1")
        self.page.get_by_role("button", name="Start practice", exact=True).click()
        for index in range(6):
            self.page.get_by_role("radio").first.check()
            self.page.get_by_role("button", name="Review practice" if index == 5 else "Next item", exact=True).click()
        expect(self.page.get_by_role("checkbox")).to_be_visible()

    def save_practice(self):
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Save practice result", exact=True).click()
        expect(self.page.get_by_role("heading", name="Practice result", exact=True)).to_be_visible()

    def test_registration_login_private_history_both_result_kinds_and_logout(self):
        username = "browser_" + uuid.uuid4().hex[:12]
        self.register(username)
        self.login(username.upper(), "incorrect password")
        expect(self.page.get_by_role("alert")).to_contain_text("Invalid username or password")
        previous_cookie = next(cookie for cookie in self.context.cookies() if cookie["name"] == "JSESSIONID")
        self.login(username.upper())
        expect(self.page.get_by_role("heading", name="My results", exact=True)).to_be_visible()
        signed_in_cookie = next(cookie for cookie in self.context.cookies() if cookie["name"] == "JSESSIONID")
        self.assertNotEqual(previous_cookie["value"], signed_in_cookie["value"])
        self.assertTrue(signed_in_cookie["httpOnly"])
        self.assertEqual(signed_in_cookie["sameSite"], "Lax")
        expect(self.page.get_by_text("No results on this page.", exact=False)).to_be_visible()
        expect(self.page.get_by_role("navigation", name="Account")).to_contain_text(username)
        self.complete_practice()
        expect(self.page.get_by_role("checkbox")).to_have_accessible_name(
            re.compile("private to your signed-in account"))
        self.save_practice()
        practice_url = self.page.url
        expect(self.page.get_by_role("button", name="Copy result link")).to_have_count(0)
        expect(self.page.get_by_text("Private result:", exact=False)).to_be_visible()
        self.page.get_by_role("link", name="My results", exact=True).first.click()
        expect(self.page.locator(".history-list > li")).to_have_count(1)
        self.page.get_by_role("link", name="Start assessment", exact=True).click()
        self.page.get_by_role("link", name="Begin assessment", exact=True).click()
        self.page.get_by_role("button", name="Start questions", exact=True).click()
        for index in range(21):
            self.page.get_by_role("radio", name="Agree", exact=True).check()
            self.page.get_by_role("button", name="Review answers" if index == 20 else "Next question", exact=True).click()
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Calculate & save record").click()
        expect(self.page.get_by_role("heading", name="Your S.P.E.C.I.A.L.", exact=True)).to_be_visible()
        special_url = self.page.url
        expect(self.page.get_by_role("button", name="Copy result link")).to_have_count(0)
        self.page.get_by_role("link", name="My results", exact=True).first.click()
        expect(self.page.locator(".history-list > li")).to_have_count(2)
        expect(self.page.locator(".history-list > li").first).to_contain_text("SPECIAL")
        self.page.reload()
        expect(self.page.locator(".history-list > li")).to_have_count(2)
        self.assertTrue(self.page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
        self.page.get_by_role("link", name="Reasoning puzzles", exact=True).click()
        expect(self.page.get_by_role("heading", name="Practice result", exact=True)).to_be_visible()
        self.page.get_by_role("button", name="Sign out", exact=True).click()
        expect(self.page.get_by_role("heading", name="Discover your S.P.E.C.I.A.L.", exact=True)).to_be_visible()
        self.page.goto(practice_url)
        expect(self.page.get_by_role("heading", name="Practice result unavailable", exact=True)).to_be_visible()
        self.page.goto(special_url)
        expect(self.page.get_by_role("heading", name="Record unavailable", exact=True)).to_be_visible()
        self.page.get_by_role("link", name="My results", exact=True).click()
        expect(self.page.get_by_role("heading", name="Sign in to continue", exact=True)).to_be_visible()
        self.page.locator("#view").get_by_role("link", name="Sign in", exact=True).click()
        self.login(username)
        expect(self.page.locator(".history-list > li")).to_have_count(2)

    def test_expired_session_never_saves_private_draft_as_unlisted(self):
        username = "expiry_" + uuid.uuid4().hex[:12]
        self.register(username)
        self.login(username)
        expect(self.page.get_by_role("heading", name="My results", exact=True)).to_be_visible()
        self.complete_practice()
        self.page.get_by_role("checkbox").check()
        self.context.clear_cookies()
        self.page.get_by_role("button", name="Save practice result", exact=True).click()
        expect(self.page.get_by_role("alert")).to_contain_text("Your session has expired")
        expect(self.page.get_by_role("alert")).to_contain_text("Your selections are still here")
        expect(self.page.get_by_role("checkbox")).to_be_checked()
        expect(self.page.get_by_role("heading", name="Review practice", exact=True)).to_be_visible()
        # Sign in again in a second tab, then retry the intact draft.
        other = self.context.new_page()
        other.goto(f"{BASE_URL}/#/login")
        other.get_by_label("Username", exact=True).fill(username)
        other.get_by_label("Password", exact=True).fill(PASSWORD)
        other.get_by_role("button", name="Sign in", exact=True).click()
        expect(other.get_by_role("heading", name="My results", exact=True)).to_be_visible()
        other.close()
        self.page.get_by_role("button", name="Save practice result", exact=True).click()
        expect(self.page.get_by_role("heading", name="Practice result", exact=True)).to_be_visible()
        expect(self.page.get_by_role("button", name="Copy result link")).to_have_count(0)

    def test_registration_validation_duplicate_and_account_isolation(self):
        username = "isolate_" + uuid.uuid4().hex[:12]
        self.page.goto(f"{BASE_URL}/#/register")
        self.page.get_by_label("Username", exact=True).fill(username)
        self.page.get_by_label("Password", exact=True).fill(PASSWORD)
        self.page.get_by_label("Confirm password", exact=True).fill("a different password")
        self.page.get_by_role("button", name="Create account", exact=True).click()
        expect(self.page.get_by_role("alert")).to_contain_text("Passwords do not match")
        self.page.get_by_label("Confirm password", exact=True).fill(PASSWORD)
        self.page.get_by_role("button", name="Create account", exact=True).click()
        expect(self.page.get_by_role("heading", name="Sign in", exact=True)).to_be_visible()
        self.login(username)
        expect(self.page.get_by_role("heading", name="My results", exact=True)).to_be_visible()
        self.complete_practice()
        self.save_practice()
        url = self.page.url
        self.page.get_by_role("button", name="Sign out", exact=True).click()
        self.page.get_by_role("link", name="Create account", exact=True).click()
        self.page.get_by_label("Username", exact=True).fill(username.upper())
        self.page.get_by_label("Password", exact=True).fill(PASSWORD)
        self.page.get_by_label("Confirm password", exact=True).fill(PASSWORD)
        self.page.get_by_role("button", name="Create account", exact=True).click()
        expect(self.page.get_by_role("alert")).to_contain_text("username is unavailable")
        self.page.get_by_label("Username", exact=True).fill("another_" + uuid.uuid4().hex[:12])
        self.page.get_by_label("Password", exact=True).fill(PASSWORD)
        self.page.get_by_label("Confirm password", exact=True).fill(PASSWORD)
        other_username = self.page.get_by_label("Username", exact=True).input_value()
        self.page.get_by_role("button", name="Create account", exact=True).click()
        expect(self.page.get_by_role("heading", name="Sign in", exact=True)).to_be_visible()
        self.login(other_username)
        expect(self.page.get_by_text("No results on this page.", exact=False)).to_be_visible()
        self.page.goto(url)
        expect(self.page.get_by_role("heading", name="Practice result unavailable", exact=True)).to_be_visible()


if __name__ == "__main__":
    unittest.main()
