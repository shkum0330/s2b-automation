# frontend/payment_window.py
from PyQt5.QtWidgets import QDialog, QVBoxLayout, QMessageBox
from PyQt5.QtWebEngineWidgets import QWebEngineView
from PyQt5.QtCore import QUrl, pyqtSignal


class PaymentWindow(QDialog):
    payment_success = pyqtSignal()

    def __init__(self, payment_data, client_key):
        super().__init__()  # [중요] 부모 클래스 초기화 필수
        self.payment_data = payment_data
        self.client_key = client_key
        self.initUI()

    def initUI(self):
        self.setWindowTitle("S2B 크레딧 충전")
        self.resize(650, 750)
        self.setModal(True)

        layout = QVBoxLayout()
        self.webview = QWebEngineView()

        # 백엔드 결제 페이지 로드
        backend_url = "http://localhost:8080/payment/checkout.html"
        self.webview.setUrl(QUrl(backend_url))

        self.webview.loadFinished.connect(self.inject_payment_data)
        self.webview.urlChanged.connect(self.check_url)

        layout.addWidget(self.webview)
        self.setLayout(layout)

    def inject_payment_data(self, ok):
        if not ok:
            print("결제 페이지 로드 실패")
            return

        amount = self.payment_data.get('amount')
        order_id = self.payment_data.get('orderId')
        order_name = self.payment_data.get('orderName')
        customer_email = self.payment_data.get('customerEmail')
        customer_name = self.payment_data.get('customerName')
        customer_key = self.payment_data.get('customerKey')

        # 자바스크립트 실행 (오타 주의)
        js_code = f"""
        initPaymentWidget(
            '{self.client_key}', 
            '{customer_key}', 
            '{order_id}', 
            '{order_name}', 
            {amount}, 
            '{customer_email}', 
            '{customer_name}'
        );
        """
        self.webview.page().runJavaScript(js_code)

    def check_url(self, qurl):
        url_str = qurl.toString()
        if "api/v1/payments/success" in url_str:
            self.payment_success.emit()
            self.accept()
        elif "api/v1/payments/fail" in url_str:
            QMessageBox.warning(self, "결제 실패", "결제 처리에 실패했습니다.")
            self.reject()