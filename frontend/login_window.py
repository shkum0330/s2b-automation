# login_window.py

import webbrowser
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

from PyQt5.QtWidgets import QWidget, QPushButton, QVBoxLayout, QDesktopWidget
from PyQt5.QtCore import pyqtSignal, QTimer


# 카카오 콜백을 처리하기 위한 임시 HTTP 서버 핸들러
class OAuthCallbackHandler(BaseHTTPRequestHandler):
    # GET 요청 처리
    def do_GET(self):
        parsed_path = urlparse(self.path)
        query_params = parse_qs(parsed_path.query)
        auth_code = query_params.get('code', [None])[0]

        self.send_response(200)
        self.send_header('Content-type', 'text/html; charset=utf-8')
        self.end_headers()

        if auth_code:
            self.server.auth_code = auth_code
            success_message = "<h1>로그인 성공!</h1><p>이 창을 닫고 애플리케이션으로 돌아가세요.</p>"
            self.wfile.write(success_message.encode('utf-8'))
        else:
            error_message = "<h1>로그인 실패</h1><p>오류가 발생했습니다. 다시 시도해주세요.</p>"
            self.wfile.write(error_message.encode('utf-8'))

        threading.Thread(target=self.server.shutdown).start()


# 로그인 UI를 담당하는 윈도우
class LoginWindow(QWidget):
    # 로그인 성공 시 auth_code를 전달하는 신호
    login_success = pyqtSignal(str)

    # 로그인 윈도우 초기화
    def __init__(self):
        super().__init__()
        self.httpd = None
        self.initUI()

    # UI 요소(버튼, 레이아웃 등) 설정
    def initUI(self):
        self.setWindowTitle('S2B 상품 정보 AI 생성기 - 로그인')
        self.login_button = QPushButton('카카오 로그인')
        self.login_button.setMinimumHeight(40)
        layout = QVBoxLayout()
        layout.addWidget(self.login_button)
        self.setLayout(layout)
        self.login_button.clicked.connect(self.handle_login)
        self.resize(300, 100)
        self.center()

    # '카카오 로그인' 버튼 클릭 시 호출
    def handle_login(self):
        server_address = ('localhost', 8989)
        self.httpd = HTTPServer(server_address, OAuthCallbackHandler)
        self.httpd.auth_code = None

        server_thread = threading.Thread(target=self.httpd.serve_forever)
        server_thread.daemon = True
        server_thread.start()

        KAKAO_CLIENT_ID = "7a67e9cf21317084df575359a9a70221"
        KAKAO_REDIRECT_URI = "http://localhost:8989"

        auth_url = f"https://kauth.kakao.com/oauth/authorize?response_type=code&client_id={KAKAO_CLIENT_ID}&redirect_uri={KAKAO_REDIRECT_URI}"
        webbrowser.open(auth_url)

        self.check_timer = QTimer(self)
        self.check_timer.timeout.connect(self.check_auth_code)
        self.check_timer.start(1000)

    # 1초마다 로컬 서버가 인증 코드를 받았는지 확인
    def check_auth_code(self):
        if self.httpd and self.httpd.auth_code:
            self.check_timer.stop()
            self.login_success.emit(self.httpd.auth_code)

    # 윈도우를 화면 중앙에 위치시킴
    def center(self):
        qr = self.frameGeometry()
        cp = QDesktopWidget().availableGeometry().center()
        qr.moveCenter(cp)
        self.move(qr.topLeft())