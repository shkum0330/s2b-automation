import os
from PyQt5.QtWidgets import QDialog, QVBoxLayout
from PyQt5.QtCore import QUrl, pyqtSlot, QTimer, pyqtSignal
from PyQt5.QtWebEngineWidgets import QWebEngineView
from PyQt5.QtWebChannel import QWebChannel


class PaymentWindow(QDialog):
    # 결제 성공 시그널
    payment_success = pyqtSignal()

    def __init__(self, client_key, order_id, order_name, amount, parent=None):
        super().__init__(parent)
        self.setWindowTitle("크레딧 충전")
        self.setModal(True)
        self.setGeometry(300, 300, 500, 700)

        self.client_key = client_key
        self.order_id = order_id
        self.order_name = order_name
        self.amount = amount

        self.webview = QWebEngineView()

        # 1. HTML 파일 로드
        html_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "payment.html"))
        self.webview.setUrl(QUrl.fromLocalFile(html_path))

        layout = QVBoxLayout(self)
        layout.addWidget(self.webview)
        self.setLayout(layout)

        # 2. HTML 로드가 완료되면 JavaScript 함수 호출
        self.webview.loadFinished.connect(self.on_load_finished)

        # 3. URL 변경 감지 (결제 성공/실패 확인)
        self.webview.urlChanged.connect(self.on_url_changed)

    def on_load_finished(self, ok):
        if ok:
            # 4. HTML 로드 후, Python -> JavaScript로 결제 정보 전달
            js_code = f"startPayment('{self.client_key}', '{self.order_id}', '{self.order_name}', {self.amount});"
            self.webview.page().runJavaScript(js_code)

    def on_url_changed(self, url):
        url_str = url.toString()

        # 5. 백엔드가 successUrl로 리다이렉트하면 감지
        if "api/v1/payments/success" in url_str:
            print("결제 성공 감지! 창을 닫습니다.")
            self.payment_success.emit()  # 성공 시그널 발생
            self.accept()  # 대화상자 닫기

        # 6. 백엔드가 failUrl로 리다이렉트하면 감지
        elif "api/v1/payments/fail" in url_str:
            print("결제 실패 감지! 창을 닫습니다.")
            self.reject()  # 대화상자 닫기