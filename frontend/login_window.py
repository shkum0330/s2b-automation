import webbrowser
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

from PyQt5.QtWidgets import QWidget, QPushButton, QVBoxLayout, QDesktopWidget
from PyQt5.QtCore import pyqtSignal, QTimer


# --- 임시 서버 로직 ---
class OAuthCallbackHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        # URL에서 'code' 파라미터 추출
        parsed_path = urlparse(self.path)
        query_params = parse_qs(parsed_path.query)
        auth_code = query_params.get('code', [None])[0]

        # 응답 페이지 작성
        self.send_response(200)
        self.send_header('Content-type', 'text/html; charset=utf-8')
        self.end_headers()

        if auth_code:
            # 성공 시, 서버에 저장하고 성공 메시지 표시
            self.server.auth_code = auth_code
            # --- 이 부분을 수정했습니다 ---
            success_message = "<h1>로그인 성공!</h1><p>이 창을 닫고 애플리케이션으로 돌아가세요.</p>"
            self.wfile.write(success_message.encode('utf-8'))
        else:
            # 실패 시, 에러 메시지 표시
            # --- 이 부분도 함께 수정했습니다 ---
            error_message = "<h1>로그인 실패</h1><p>오류가 발생했습니다. 다시 시도해주세요.</p>"
            self.wfile.write(error_message.encode('utf-8'))

        # HTML 전송 후 바로 서버 종료 요청
        threading.Thread(target=self.server.shutdown).start()


class LoginWindow(QWidget):
    # 로그인 성공 시 auth_code를 함께 전달하도록 시그널 변경
    login_success = pyqtSignal(str)

    def __init__(self):
        super().__init__()
        self.httpd = None
        self.initUI()

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

    def handle_login(self):
        # --- 1. 임시 로컬 서버를 스레드에서 시작 ---
        server_address = ('localhost', 8989)
        self.httpd = HTTPServer(server_address, OAuthCallbackHandler)
        self.httpd.auth_code = None # 코드를 저장할 변수

        server_thread = threading.Thread(target=self.httpd.serve_forever)
        server_thread.daemon = True
        server_thread.start()
        print(f"{server_address}에서 콜백 대기 중...")

        # --- 2. 수정된 Redirect URI로 브라우저 열기 ---
        KAKAO_CLIENT_ID = "7a67e9cf21317084df575359a9a70221"
        # Redirect URI를 백엔드가 아닌 로컬 서버로 변경!
        KAKAO_REDIRECT_URI = "http://localhost:8989"

        auth_url = f"https://kauth.kakao.com/oauth/authorize?response_type=code&client_id={KAKAO_CLIENT_ID}&redirect_uri={KAKAO_REDIRECT_URI}"
        webbrowser.open(auth_url)

        # --- 3. 코드가 들어왔는지 1초마다 확인 ---
        # QTimer를 사용해 주기적으로 self.check_auth_code 함수를 실행
        self.check_timer = QTimer(self)
        self.check_timer.timeout.connect(self.check_auth_code)
        self.check_timer.start(1000) # 1000ms = 1초

    def check_auth_code(self):
        # httpd 서버에 auth_code가 저장되었는지 확인
        if self.httpd and self.httpd.auth_code:
            self.check_timer.stop() # 타이머 중지
            print(f"인증 코드 수신: {self.httpd.auth_code}")
            # 성공 시그널에 코드를 담아서 보냄
            self.login_success.emit(self.httpd.auth_code)

    def center(self):
        qr = self.frameGeometry()
        cp = QDesktopWidget().availableGeometry().center()
        qr.moveCenter(cp)
        self.move(qr.topLeft())