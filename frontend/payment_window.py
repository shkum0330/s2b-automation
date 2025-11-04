import os
from PyQt5.QtWidgets import QDialog, QVBoxLayout
from PyQt5.QtCore import QUrl, pyqtSignal
from PyQt5.QtWebEngineWidgets import QWebEngineView

class PaymentWindow(QDialog):
    # 결제 성공 시그널 (main_window에 알리기 위함)
    payment_success = pyqtSignal()

    def __init__(self, client_key, order_id, order_name, amount, parent=None):
        super().__init__(parent)
        self.setWindowTitle("S2B 크레딧 결제")
        self.setModal(True)
        self.setGeometry(300, 300, 500, 700)  # 결제창 크기

        self.webview = QWebEngineView()
        layout = QVBoxLayout(self)
        layout.addWidget(self.webview)
        self.setLayout(layout)

        # 1. 로컬 payment.html 파일 로드
        html_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "payment.html"))
        self.webview.setUrl(QUrl.fromLocalFile(html_path))

        # 2. HTML 로드가 완료되면, Python -> JavaScript 함수 호출
        self.webview.loadFinished.connect(
            lambda ok: self.inject_payment_data(client_key, order_id, order_name, amount)
        )

        # 3. (중요) 결제 위젯이 successUrl/failUrl로 이동하는 것을 감지
        self.webview.urlChanged.connect(self.check_url)

    def inject_payment_data(self, client_key, order_id, order_name, amount):
        # payment.html 내부의 startPayment() JavaScript 함수를 호출
        js_code = f"startPayment('{client_key}', '{order_id}', '{order_name}', {amount});"
        self.webview.page().runJavaScript(js_code)

    def check_url(self, url):
        url_str = url.toString()

        # 4. 백엔드의 successUrl이 감지되면 (결제 최종 승인 완료)
        if "api/v1/payments/success" in url_str:
            print("결제 성공 감지! (백엔드 승인 완료)")
            self.payment_success.emit()  # main_window에 성공 알림
            self.accept()  # 창 닫기

        # 5. 백엔드의 failUrl이 감지되면
        elif "api/v1/payments/fail" in url_str:
            print("결제 실패 감지!")
            self.reject()  # 창 닫기