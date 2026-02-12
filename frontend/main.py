import sys
from urllib.parse import quote_plus

from PyQt5.QtWidgets import QApplication, QMessageBox

from api_worker import ApiWorker
from config import BASE_URL
from login_window import LoginWindow
from main_window import MainWindow


class MainController:
    def __init__(self):
        self.login_win = LoginWindow()
        self.main_win = None
        self.api_worker = None
        self.access_token = None

        self.login_win.login_success.connect(self.process_login)

    def show_login_window(self):
        self.login_win.show()

    def process_login(self, auth_code, state):
        redirect_uri = "http://localhost:8989"
        url = (
            f"{BASE_URL}/api/v1/auth/callback/kakao"
            f"?code={quote_plus(auth_code)}"
            f"&state={quote_plus(state)}"
            f"&redirectUri={quote_plus(redirect_uri)}"
        )

        self.api_worker = ApiWorker("GET", url)
        self.api_worker.finished.connect(self.handle_login_response)
        self.api_worker.start()

    def handle_login_response(self, response):
        if not response.get("ok"):
            error_msg = response.get("json", {}).get("message", "알 수 없는 로그인 오류")
            QMessageBox.critical(self.login_win, "로그인 실패", error_msg)
            return

        headers = response.get("headers", {})
        self.access_token = headers.get("Authorization")

        if not self.access_token:
            QMessageBox.critical(self.login_win, "로그인 실패", "Access Token을 받지 못했습니다.")
            return

        print(f"Access Token 수신 성공: {self.access_token}")
        self.show_main_window(self.access_token)

    def show_main_window(self, access_token):
        if self.main_win is None:
            self.main_win = MainWindow(access_token=access_token)

        self.login_win.close()
        self.main_win.show()


if __name__ == "__main__":
    app = QApplication(sys.argv)
    controller = MainController()
    controller.show_login_window()
    sys.exit(app.exec_())
