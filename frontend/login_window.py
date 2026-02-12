import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

import requests
from PyQt5.QtCore import QTimer, pyqtSignal
from PyQt5.QtWidgets import QDesktopWidget, QMessageBox, QPushButton, QVBoxLayout, QWidget

from config import BASE_URL


class OAuthCallbackHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed_path = urlparse(self.path)
        query_params = parse_qs(parsed_path.query)
        auth_code = query_params.get("code", [None])[0]
        state = query_params.get("state", [None])[0]

        self.send_response(200)
        self.send_header("Content-type", "text/html; charset=utf-8")
        self.end_headers()

        if auth_code and state:
            self.server.auth_code = auth_code
            self.server.auth_state = state
            success_message = "<h1>로그인 성공</h1><p>이 창을 닫고 프로그램으로 돌아가세요.</p>"
            self.wfile.write(success_message.encode("utf-8"))
        else:
            error_message = "<h1>로그인 실패</h1><p>code/state 파라미터가 올바르지 않습니다.</p>"
            self.wfile.write(error_message.encode("utf-8"))

        threading.Thread(target=self.server.shutdown).start()


class LoginWindow(QWidget):
    login_success = pyqtSignal(str, str)

    def __init__(self):
        super().__init__()
        self.httpd = None
        self.check_timer = None
        self.initUI()

    def initUI(self):
        self.setWindowTitle("S2B 상품 정보 AI 생성기 - 로그인")
        self.login_button = QPushButton("카카오 로그인")
        self.login_button.setMinimumHeight(40)

        layout = QVBoxLayout()
        layout.addWidget(self.login_button)
        self.setLayout(layout)

        self.login_button.clicked.connect(self.handle_login)
        self.resize(300, 100)
        self.center()

    def handle_login(self):
        state = self._issue_oauth_state()
        if not state:
            return

        server_address = ("localhost", 8989)
        self.httpd = HTTPServer(server_address, OAuthCallbackHandler)
        self.httpd.auth_code = None
        self.httpd.auth_state = None

        server_thread = threading.Thread(target=self.httpd.serve_forever)
        server_thread.daemon = True
        server_thread.start()

        kakao_client_id = "7a67e9cf21317084df575359a9a70221"
        kakao_redirect_uri = "http://localhost:8989"
        auth_url = (
            "https://kauth.kakao.com/oauth/authorize"
            f"?response_type=code&client_id={kakao_client_id}"
            f"&redirect_uri={kakao_redirect_uri}"
            f"&state={state}"
        )
        webbrowser.open(auth_url)

        self.check_timer = QTimer(self)
        self.check_timer.timeout.connect(self.check_auth_callback)
        self.check_timer.start(1000)

    def check_auth_callback(self):
        if self.httpd and self.httpd.auth_code and self.httpd.auth_state:
            self.check_timer.stop()
            self.login_success.emit(self.httpd.auth_code, self.httpd.auth_state)

    def _issue_oauth_state(self):
        try:
            response = requests.get(f"{BASE_URL}/api/v1/auth/state", timeout=5)
            response.raise_for_status()
            payload = response.json()
            state = payload.get("state")
            if not state:
                raise ValueError("state 누락")
            return state
        except Exception as exc:
            QMessageBox.critical(self, "로그인 오류", f"로그인 준비 중 오류가 발생했습니다.\n{exc}")
            return None

    def center(self):
        qr = self.frameGeometry()
        cp = QDesktopWidget().availableGeometry().center()
        qr.moveCenter(cp)
        self.move(qr.topLeft())
