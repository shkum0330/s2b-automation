import os
import sys
import socket
import threading
from http.server import SimpleHTTPRequestHandler, HTTPServer

from PyQt5.QtWidgets import QDialog, QVBoxLayout, QMessageBox
from PyQt5.QtCore import QUrl, pyqtSignal
from PyQt5.QtWebEngineWidgets import QWebEngineView


# 1. [추가] 현재 폴더를 서빙하는 핸들러
class LocalFileHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        # frontend 폴더를 루트로 설정
        base_dir = os.path.dirname(os.path.abspath(__file__))
        super().__init__(*args, directory=base_dir, **kwargs)

    # 로그 출력 끄기 (콘솔 깔끔하게)
    def log_message(self, format, *args):
        pass


class PaymentWindow(QDialog):
    payment_success = pyqtSignal()

    def __init__(self, client_key, order_id, order_name, amount, parent=None):
        super().__init__(parent)
        self.setWindowTitle("S2B 크레딧 결제")
        self.setModal(True)
        self.setGeometry(300, 300, 500, 700)

        self.server = None
        self.server_thread = None
        self.port = 0

        # 2. [추가] 내장 웹 서버 시작
        self.start_local_server()

        self.webview = QWebEngineView()

        # 3. 웹 뷰 설정 (이제 http://로 접속하므로 보안 설정이 덜 까다로움)
        #    하지만 여전히 팝업 등을 위해 기본 설정은 유지
        #    (main.py의 전역 설정이 적용됨)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.webview)
        self.setLayout(layout)

        # 4. [핵심] file:// 대신 http://localhost로 접속
        local_url = f"http://localhost:{self.port}/payment.html"
        print(f"결제창 접속 시도: {local_url}")
        self.webview.setUrl(QUrl(local_url))

        # 5. 시그널 연결
        self.webview.loadFinished.connect(
            lambda ok: self.inject_payment_data(ok, client_key, order_id, order_name, amount)
        )
        self.webview.urlChanged.connect(self.check_url)

    # --- [서버 관련 메서드] ---
    def start_local_server(self):
        """빈 포트를 찾아 로컬 웹 서버를 실행합니다."""
        try:
            # 빈 포트 자동 할당 (포트 0)
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.bind(('localhost', 0))
            self.port = sock.getsockname()[1]
            sock.close()

            # 서버 생성
            self.server = HTTPServer(('localhost', self.port), LocalFileHandler)

            # 데몬 스레드로 실행 (앱 종료 시 같이 종료됨)
            self.server_thread = threading.Thread(target=self.server.serve_forever)
            self.server_thread.daemon = True
            self.server_thread.start()
            print(f"로컬 결제 서버 시작됨: http://localhost:{self.port}")

        except Exception as e:
            QMessageBox.critical(self, "오류", f"로컬 서버 시작 실패: {e}")

    def stop_local_server(self):
        """창이 닫힐 때 서버를 종료합니다."""
        if self.server:
            self.server.shutdown()
            self.server.server_close()
            print("로컬 결제 서버 종료됨")

    # 창 닫기 이벤트 오버라이드
    def closeEvent(self, event):
        self.stop_local_server()
        super().closeEvent(event)

    # --- [기존 로직] ---
    def inject_payment_data(self, ok, client_key, order_id, order_name, amount):
        if not ok:
            print("페이지 로드 실패")
            return

        print("페이지 로드 완료. 결제 함수 실행.")
        # payment.html 내의 startPayment 함수 호출
        js_code = f"startPayment('{client_key}', '{order_id}', '{order_name}', {amount});"
        self.webview.page().runJavaScript(js_code)

    def check_url(self, url):
        url_str = url.toString()
        print(f"URL 감지: {url_str}")

        if "api/v1/payments/success" in url_str:
            print("✅ 결제 성공!")
            self.payment_success.emit()
            self.accept()

        elif "api/v1/payments/fail" in url_str:
            print("❌ 결제 실패!")
            self.reject()