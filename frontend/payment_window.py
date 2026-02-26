import json

from PyQt5.QtWidgets import QDialog, QVBoxLayout, QMessageBox
from PyQt5.QtWebEngineWidgets import QWebEngineView
from PyQt5.QtCore import QUrl, pyqtSignal

from config import PAYMENT_CHECKOUT_URL


class PaymentWindow(QDialog):
    payment_success = pyqtSignal()

    def __init__(self, payment_data, client_key):
        super().__init__()  # 부모 클래스 초기화 필수
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
        self.webview.setUrl(QUrl(PAYMENT_CHECKOUT_URL))

        self.webview.loadFinished.connect(self.inject_payment_data)
        self.webview.urlChanged.connect(self.check_url)

        layout.addWidget(self.webview)
        self.setLayout(layout)

    def inject_payment_data(self, ok):
        if not ok:
            QMessageBox.critical(self, "결제 오류", "결제 페이지 로드에 실패했습니다.")
            self.reject()
            return

        amount = self.payment_data.get('amount')
        order_id = self.payment_data.get('orderId')
        order_name = self.payment_data.get('orderName')
        customer_email = self.payment_data.get('customerEmail')
        customer_name = self.payment_data.get('customerName')
        customer_key = self.payment_data.get('customerKey')

        if not order_id or not order_name:
            QMessageBox.warning(self, "결제 오류", "결제 요청 정보가 올바르지 않습니다.")
            self.reject()
            return

        try:
            safe_amount = float(amount)
            if safe_amount <= 0:
                raise ValueError("amount must be positive")
        except (TypeError, ValueError):
            QMessageBox.warning(self, "결제 오류", "결제 금액이 올바르지 않습니다.")
            self.reject()
            return

        if safe_amount.is_integer():
            safe_amount = int(safe_amount)

        payload = {
            "clientKey": self.client_key or "",
            "customerKey": customer_key or "",
            "orderId": order_id,
            "orderName": order_name,
            "amount": safe_amount,
            "customerEmail": customer_email or "",
            "customerName": customer_name or "",
        }

        # 자바스크립트 실행
        js_code = f"""
        const paymentPayload = {json.dumps(payload, ensure_ascii=False)};
        initPaymentWidget(
            paymentPayload.clientKey,
            paymentPayload.customerKey,
            paymentPayload.orderId,
            paymentPayload.orderName,
            paymentPayload.amount,
            paymentPayload.customerEmail,
            paymentPayload.customerName
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
