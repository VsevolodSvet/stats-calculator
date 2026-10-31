import os
import json
import struct
import unittest
import uuid
import zlib

from playwright.sync_api import expect, sync_playwright


BASE_URL = os.environ.get("TEST_BASE_URL", "http://localhost:8080").rstrip("/")


def solid_png(value):
    def chunk(name, data):
        return struct.pack(">I", len(data)) + name + data + struct.pack(">I", zlib.crc32(name + data) & 0xFFFFFFFF)
    pixels = (b"\x00" + bytes([value, value, value]) * 64) * 64
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b""))


class PrivatePhotoBrowserTest(unittest.TestCase):
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

    def create_account(self):
        username = "image_" + uuid.uuid4().hex[:12]
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
        self.page.get_by_role("link", name="Private photo", exact=True).click()
        expect(self.page.get_by_role("heading", name="Private profile photo", exact=True)).to_be_visible()

    def choose(self, value):
        self.page.get_by_label("Choose a photo", exact=True).set_input_files(
            {"name": "synthetic.png", "mimeType": "image/png", "buffer": solid_png(value)})

    def test_private_upload_reload_replace_delete_and_no_external_requests(self):
        self.create_account()
        external = []
        self.page.on("request", lambda request: external.append(request.url)
                     if request.url.startswith("http") and not request.url.startswith(BASE_URL + "/") else None)
        expect(self.page.get_by_text("No photo is saved for this account.", exact=True)).to_be_visible()
        self.choose(0)
        expect(self.page.get_by_role("img", name="Your private profile photo preview")).to_be_visible()
        self.page.get_by_role("button", name="Save private photo", exact=True).click()
        expect(self.page.get_by_role("checkbox")).not_to_be_checked()
        expect(self.page.get_by_text("No photo is saved for this account.", exact=True)).to_have_count(0)
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Save private photo", exact=True).click()
        expect(self.page.get_by_role("heading", name="Local image-quality feedback", exact=True)).to_be_visible()
        expect(self.page.get_by_text("The whole image is relatively dark.", exact=False)).to_be_visible()
        self.page.reload()
        expect(self.page.get_by_role("heading", name="Local image-quality feedback", exact=True)).to_be_visible()
        expect(self.page.get_by_role("img")).to_be_visible()
        self.choose(255)
        expect(self.page.get_by_role("checkbox")).not_to_be_checked()
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Replace private photo", exact=True).click()
        expect(self.page.get_by_text("The whole image is relatively bright.", exact=False)).to_be_visible()
        self.assertTrue(self.page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
        other = self.browser.new_context()
        try:
            response = other.request.get(f"{BASE_URL}/api/v1/me/photo/image")
            self.assertEqual(response.status, 401)
        finally:
            other.close()
        self.page.once("dialog", lambda dialog: dialog.accept())
        self.page.get_by_role("button", name="Delete stored photo", exact=True).click()
        expect(self.page.get_by_text("No photo is saved for this account.", exact=True)).to_be_visible()
        expect(self.page.get_by_role("img")).to_be_hidden()
        self.assertEqual(external, [])
        self.page.get_by_role("button", name="Sign out", exact=True).click()
        expect(self.page.get_by_role("heading", name="Discover your S.P.E.C.I.A.L.", exact=True)).to_be_visible()
        self.page.goto(f"{BASE_URL}/#/photo")
        expect(self.page.get_by_role("heading", name="Sign in to continue", exact=True)).to_be_visible()

    def test_invalid_upload_retry_and_unsaved_file_warning(self):
        self.create_account()
        self.page.get_by_label("Choose a photo", exact=True).set_input_files(
            {"name": "wrong.svg", "mimeType": "image/svg+xml", "buffer": b"<svg/>"})
        expect(self.page.get_by_role("alert")).to_contain_text("JPEG or PNG")
        self.choose(128)
        self.page.once("dialog", lambda dialog: dialog.dismiss())
        self.page.get_by_role("link", name="Character sheet", exact=True).first.click()
        expect(self.page.get_by_role("heading", name="Private profile photo", exact=True)).to_be_visible()
        self.page.route("**/api/v1/me/photo", lambda route: route.abort() if route.request.method == "POST" else route.continue_())
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Save private photo", exact=True).click()
        expect(self.page.get_by_role("alert")).to_contain_text("available for retry")
        self.page.unroute("**/api/v1/me/photo")
        self.page.get_by_role("button", name="Save private photo", exact=True).click()
        expect(self.page.get_by_role("heading", name="Local image-quality feedback", exact=True)).to_be_visible()

    def test_ilook_disabled_without_key(self):
        self.create_account()
        expect(self.page.get_by_text("iLook is disabled.", exact=False)).to_be_visible()
        expect(self.page.get_by_role("button", name="Analyze saved photo with iLook")).to_have_count(0)

    def test_optional_ilook_consent_retry_report_and_replace_clears_feedback(self):
        attempts = []
        report = {"analysisId": str(uuid.uuid4()), "rubricVersion": "test-v1", "model": "mock",
                  "overallScore": 65, "signals": [{"name": "symmetry", "score": 6.5,
                                                  "explanation": "<b>Subjective photo feedback</b>"}],
                  "summary": "Mock appearance feedback.", "confidence": "medium",
                  "disclaimer": "Not a personality assessment."}

        def provider(route):
            if route.request.method == "GET":
                route.fulfill(json={"available": True})
                return
            attempts.append(route.request.post_data_json)
            if len(attempts) == 1:
                route.fulfill(status=429, content_type="application/problem+json",
                              body=json.dumps({"detail": "iLook's quota is exhausted."}))
            else:
                route.fulfill(json=report)

        self.page.route("**/api/v1/me/photo/ilook", provider)
        self.create_account()
        self.choose(128)
        self.page.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Save private photo", exact=True).click()
        analyze = self.page.get_by_role("button", name="Analyze saved photo with iLook")
        expect(analyze).to_be_visible()
        self.assertEqual(attempts, [])
        analyze.click()
        self.assertEqual(attempts, [])
        panel = self.page.get_by_role("region", name="Optional Charisma photo feedback", exact=True)
        panel.get_by_role("checkbox").nth(0).check()
        analyze.click()
        self.assertEqual(attempts, [])
        panel.get_by_role("checkbox").nth(1).check()
        analyze.click()
        expect(panel.get_by_role("alert")).to_contain_text("quota is exhausted")
        analyze.click()
        expect(panel.get_by_role("heading", name="iLook subjective appearance score: 65/100")).to_be_visible()
        expect(panel.get_by_text("<b>Subjective photo feedback</b>", exact=False)).to_be_visible()
        self.assertEqual(panel.locator("b").count(), 0)
        expect(panel.get_by_role("alert")).to_have_count(0)
        expect(panel.get_by_role("checkbox").nth(0)).not_to_be_checked()
        self.assertEqual(len(attempts), 2)
        self.assertTrue(attempts[1]["externalConsent"])
        self.assertTrue(attempts[1]["adultAndAuthorized"])
        self.assertIn("revision", attempts[1])
        self.assertTrue(self.page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
        self.choose(0)
        expect(panel).to_be_hidden()
        self.page.locator(".photo-form").first.get_by_role("checkbox").check()
        self.page.get_by_role("button", name="Replace private photo", exact=True).click()
        expect(self.page.get_by_text("Mock appearance feedback.", exact=True)).to_have_count(0)
        expect(self.page.get_by_role("button", name="Analyze saved photo with iLook")).to_be_visible()


if __name__ == "__main__":
    unittest.main()
