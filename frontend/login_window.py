import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

from PyQt5.QtCore import QTimer, pyqtSignal
from PyQt5.QtWidgets import QDesktopWidget, QMessageBox, QPushButton, QVBoxLayout, QWidget

from api_worker import ApiWorker
from config import BASE_URL, KAKAO_CLIENT_ID, KAKAO_REDIRECT_URI


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
        self.state_worker = None
        self.check_timer = QTimer(self)
        self.check_timer.timeout.connect(self.check_auth_callback)
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
        self.login_button.setEnabled(False)
        self.login_button.setText("로그인 준비 중...")

        self.state_worker = ApiWorker("GET", f"{BASE_URL}/api/v1/auth/state", timeout=5)
        self.state_worker.finished.connect(self.handle_state_response)
        self.state_worker.start()

    def handle_state_response(self, result):
        if not result.get("ok"):
            self._restore_login_button()
            error_message = result.get("json", {}).get("message", result.get("error", "로그인 준비에 실패했습니다."))
            QMessageBox.critical(self, "로그인 오류", error_message)
            return

        state = result.get("json", {}).get("state")
        if not state:
            self._restore_login_button()
            QMessageBox.critical(self, "로그인 오류", "로그인 state 값이 누락되었습니다.")
            return

        if not KAKAO_CLIENT_ID:
            self._restore_login_button()
            QMessageBox.critical(self, "로그인 오류", "카카오 클라이언트 ID가 설정되지 않았습니다.")
            return

        if not self._start_callback_server():
            return

        auth_url = (
            "https://kauth.kakao.com/oauth/authorize"
            f"?response_type=code&client_id={KAKAO_CLIENT_ID}"
            f"&redirect_uri={KAKAO_REDIRECT_URI}"
            f"&state={state}"
        )
        webbrowser.open(auth_url)

        self.check_timer.start(1000)
        self.login_button.setText("카카오 로그인 진행 중...")

    def check_auth_callback(self):
        if self.httpd and self.httpd.auth_code and self.httpd.auth_state:
            self.check_timer.stop()
            auth_code = self.httpd.auth_code
            auth_state = self.httpd.auth_state
            self._stop_callback_server()
            self._restore_login_button()
            self.login_success.emit(auth_code, auth_state)

    def _start_callback_server(self):
        self._stop_callback_server()
        server_address = ("localhost", 8989)
        try:
            self.httpd = HTTPServer(server_address, OAuthCallbackHandler)
            self.httpd.auth_code = None
            self.httpd.auth_state = None
            server_thread = threading.Thread(target=self.httpd.serve_forever)
            server_thread.daemon = True
            server_thread.start()
            return True
        except OSError as exc:
            self._restore_login_button()
            QMessageBox.critical(self, "로그인 오류", f"콜백 서버를 시작할 수 없습니다.\n{exc}")
            return False

    def _stop_callback_server(self):
        if not self.httpd:
            return

        try:
            self.httpd.shutdown()
        except Exception:
            pass

        try:
            self.httpd.server_close()
        except Exception:
            pass

        self.httpd = None

    def _restore_login_button(self):
        self.login_button.setEnabled(True)
        self.login_button.setText("카카오 로그인")

    def closeEvent(self, event):
        self.check_timer.stop()
        self._stop_callback_server()
        super().closeEvent(event)

    def center(self):
        qr = self.frameGeometry()
        cp = QDesktopWidget().availableGeometry().center()
        qr.moveCenter(cp)
        self.move(qr.topLeft())
