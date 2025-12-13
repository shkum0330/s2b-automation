import pyperclip
from PyQt5.QtWidgets import (QWidget, QLabel, QLineEdit, QTextEdit,
                             QPushButton, QVBoxLayout, QGroupBox, QGridLayout,
                             QMessageBox, QHBoxLayout, QRadioButton, QFrame, QComboBox)
from PyQt5.QtCore import Qt, QTimer
from PyQt5.QtGui import QFont
from api_worker import ApiWorker
from payment_window import PaymentWindow

class MainWindow(QWidget):
    def __init__(self, access_token=None):
        super().__init__()
        self.access_token = access_token
        self.worker = None
        self.current_task_id = None
        self.toss_client_key = None
        self.load_client_key()  # 키 로드 시도
        self.polling_timer = QTimer(self)
        self.polling_timer.timeout.connect(self.check_task_status)

        self.input_widgets = {}
        self.output_widgets = {}

        self.initUI()
        self.update_credit_display()
        self._update_ui_for_product_type()

    def initUI(self):
        default_font = QFont("Apple SD Gothic Neo", 13)

        product_type_label = QLabel("제품 유형:")
        product_type_label.setFont(default_font)
        self.radio_electronic = QRadioButton("전자제품")
        self.radio_general = QRadioButton("비전자제품")
        self.radio_electronic.setChecked(True)
        self.radio_electronic.setFont(default_font)
        self.radio_general.setFont(default_font)
        self.radio_electronic.toggled.connect(self._update_ui_for_product_type)

        product_type_layout = QHBoxLayout()
        product_type_layout.addWidget(product_type_label)
        product_type_layout.addWidget(self.radio_electronic)
        product_type_layout.addWidget(self.radio_general)
        product_type_layout.addStretch(1)

        self.plan_combo = QComboBox()
        self.plan_combo.setFont(default_font)
        self.plan_combo.setFixedWidth(230)
        self.plan_combo.addItem("플랜 선택", 0)
        self.plan_combo.addItem("30일 10개 플랜 (29,900원)", 29900)
        self.plan_combo.addItem("30일 20개 플랜 (49,900원)", 49900)
        self.plan_combo.addItem("30일 50개 플랜 (100,000원)", 100000)

        self.payment_button = QPushButton("🚀 크레딧 충전")
        self.payment_button.setFont(default_font)
        self.payment_button.setFixedWidth(120)
        self.payment_button.clicked.connect(self.start_payment_request)

        self.credit_label = QLabel("남은 크레딧: -")
        self.credit_label.setFont(default_font)
        self.refresh_button = QPushButton("새로고침")
        self.refresh_button.setFixedWidth(150)
        self.refresh_button.setFont(default_font)
        self.refresh_button.clicked.connect(self.update_credit_display)

        # --- [NEW] 수직 구분선 생성 ---
        separator = QFrame()
        separator.setFrameShape(QFrame.VLine)  # 수직선 모양
        separator.setFrameShadow(QFrame.Sunken)  # 약간의 음영 효과

        # 상단 레이아웃 구성
        top_layout = QHBoxLayout()
        top_layout.addLayout(product_type_layout)
        top_layout.addWidget(separator)

        # 결제 UI 배치
        top_layout.addWidget(self.plan_combo)
        top_layout.addWidget(self.payment_button)

        top_layout.addStretch(1)
        top_layout.addWidget(self.credit_label)
        top_layout.addWidget(self.refresh_button)

        request_group = QGroupBox("서버에 보낼 정보")
        request_group.setFont(default_font)
        req_layout = QGridLayout()

        self.input_widgets['product_name_example_label'] = QLabel("1. 물품(용역)명 예시:")
        self.input_widgets['product_name_example_input'] = QLineEdit()
        self.input_widgets['product_name_label'] = QLabel("1. 물품(용역)명:")
        self.input_widgets['product_name_input'] = QLineEdit()
        self.input_widgets['spec_example_label'] = QLabel("2. 규격 예시:")
        self.input_widgets['spec_example_input'] = QTextEdit()
        self.input_widgets['spec_example_input'].setFixedHeight(80)
        self.input_widgets['model_label'] = QLabel("3. 모델명:")
        self.input_widgets['model_input'] = QLineEdit()

        for widget in self.input_widgets.values():
            widget.setFont(default_font)

        req_layout.addWidget(self.input_widgets['product_name_example_label'], 0, 0)
        req_layout.addWidget(self.input_widgets['product_name_example_input'], 0, 1)
        req_layout.addWidget(self.input_widgets['product_name_label'], 0, 0)
        req_layout.addWidget(self.input_widgets['product_name_input'], 0, 1)
        req_layout.addWidget(self.input_widgets['spec_example_label'], 1, 0, Qt.AlignTop)
        req_layout.addWidget(self.input_widgets['spec_example_input'], 1, 1)
        req_layout.addWidget(self.input_widgets['model_label'], 2, 0)
        req_layout.addWidget(self.input_widgets['model_input'], 2, 1)

        request_group.setLayout(req_layout)

        response_group = QGroupBox("서버로부터 받은 결과")
        response_group.setFont(default_font)
        res_layout = QGridLayout()

        output_widget_info = [
            ("productName", "1. 물품(용역)명:"), ("specification", "2. 규격(사양, 용량 등):"),
            ("modelName", "3. 모델명:"), ("manufacturer", "4. 제조사:"),
            ("countryOfOrigin", "5. 원산지:"), ("katsCertificationNumber", "6. 전기용품 인증정보:"),
            ("kcCertificationNumber", "7. 방송통신기자재 인증정보:"), ("g2bClassificationNumber", "8. G2B 물품목록번호:")
        ]

        for key, label_text in output_widget_info:
            label = QLabel(label_text)
            output_field = QLineEdit() if key != "specification" else QTextEdit()
            if isinstance(output_field, QTextEdit):
                output_field.setFixedHeight(80)
            output_field.setReadOnly(True)
            copy_button = QPushButton("복사")
            copy_button.setFixedWidth(100)
            copy_button.clicked.connect(lambda _, w=output_field: self.copy_to_clipboard(w))

            label.setFont(default_font)
            output_field.setFont(default_font)
            copy_button.setFont(default_font)

            self.output_widgets[key] = {'label': label, 'field': output_field, 'button': copy_button}

        row = 0
        for key in self.output_widgets.keys():
            widgets = self.output_widgets[key]
            align = Qt.AlignTop if isinstance(widgets['field'], QTextEdit) else Qt.AlignLeft
            res_layout.addWidget(widgets['label'], row, 0, align)
            res_layout.addWidget(widgets['field'], row, 1)
            res_layout.addWidget(widgets['button'], row, 2)
            row += 1

        response_group.setLayout(res_layout)

        action_group = QGroupBox("2. 실행")
        action_group.setFont(default_font)
        self.run_button = QPushButton("🚀 AI로 결과 생성하기")
        self.cancel_button = QPushButton("❌ 취소")
        self.run_button.setFont(default_font)
        self.cancel_button.setFont(default_font)
        self.cancel_button.setEnabled(False)
        self.status_label = QLabel("상태: 대기 중...")
        self.status_label.setFont(default_font)

        button_layout = QHBoxLayout()
        button_layout.addWidget(self.run_button)
        button_layout.addWidget(self.cancel_button)

        action_layout = QVBoxLayout()
        action_layout.addLayout(button_layout)
        action_layout.addWidget(self.status_label)
        action_group.setLayout(action_layout)

        main_layout = QVBoxLayout(self)
        main_layout.addLayout(top_layout)
        main_layout.addWidget(request_group)
        main_layout.addWidget(action_group)
        main_layout.addWidget(response_group)
        self.run_button.clicked.connect(self.start_api_call)
        self.cancel_button.clicked.connect(self.cancel_api_call)
        self.setWindowTitle("S2B 상품 정보 AI 생성기")

        self.setGeometry(300, 300, 840, 800)

    def _update_ui_for_product_type(self):
        is_electronic = self.radio_electronic.isChecked()

        self.input_widgets['product_name_example_label'].setVisible(is_electronic)
        self.input_widgets['product_name_example_input'].setVisible(is_electronic)
        self.input_widgets['model_label'].setVisible(is_electronic)
        self.input_widgets['model_input'].setVisible(is_electronic)
        self.input_widgets['product_name_label'].setVisible(not is_electronic)
        self.input_widgets['product_name_input'].setVisible(not is_electronic)

        self.output_widgets['modelName']['label'].setVisible(is_electronic)
        self.output_widgets['modelName']['field'].setVisible(is_electronic)
        self.output_widgets['modelName']['button'].setVisible(is_electronic)
        self.output_widgets['katsCertificationNumber']['label'].setVisible(is_electronic)
        self.output_widgets['katsCertificationNumber']['field'].setVisible(is_electronic)
        self.output_widgets['katsCertificationNumber']['button'].setVisible(is_electronic)
        self.output_widgets['kcCertificationNumber']['label'].setVisible(is_electronic)
        self.output_widgets['kcCertificationNumber']['field'].setVisible(is_electronic)
        self.output_widgets['kcCertificationNumber']['button'].setVisible(is_electronic)
        self.output_widgets['g2bClassificationNumber']['label'].setVisible(is_electronic)
        self.output_widgets['g2bClassificationNumber']['field'].setVisible(is_electronic)
        self.output_widgets['g2bClassificationNumber']['button'].setVisible(is_electronic)

        self.output_widgets['manufacturer']['label'].setText("4. 제조사:" if is_electronic else "3. 제조사:")
        self.output_widgets['countryOfOrigin']['label'].setText("5. 원산지:" if is_electronic else "4. 원산지:")

    def start_api_call(self):
        is_electronic = self.radio_electronic.isChecked()
        if is_electronic:
            model = self.input_widgets['model_input'].text()
            spec_example = self.input_widgets['spec_example_input'].toPlainText()
            product_name_example = self.input_widgets['product_name_example_input'].text()
            if not model or not spec_example:
                QMessageBox.warning(self, "입력 오류", "모델명과 규격 예시는 반드시 입력해야 합니다.")
                return
            url = 'http://localhost:8080/api/v1/generation/generate-spec'
            payload = {"model": model, "specExample": spec_example, "productNameExample": product_name_example}
        else:
            product_name = self.input_widgets['product_name_input'].text()
            spec_example = self.input_widgets['spec_example_input'].toPlainText()
            if not product_name or not spec_example:
                QMessageBox.warning(self, "입력 오류", "물품명과 규격 예시는 반드시 입력해야 합니다.")
                return
            url = 'http://localhost:8080/api/v1/generation/generate-general-spec'
            payload = {"productName": product_name, "specExample": spec_example}

        self.run_button.setEnabled(False)
        self.cancel_button.setEnabled(True)
        self.status_label.setText("상태: 🤖 작업 시작 요청 중...")
        self.clear_outputs()

        headers = {"Content-Type": "application/json", "Authorization": self.access_token}
        self.worker = ApiWorker('POST', url, payload=payload, headers=headers, timeout=65)
        self.worker.finished.connect(self.handle_task_start_response)
        self.worker.start()

    def handle_api_result(self, result):
        self.status_label.setText("상태: ✅ AI 생성 완료!")
        for key, widgets in self.output_widgets.items():
            if widgets['field'].isVisible():
                self.set_widget_text(widgets['field'], str(result.get(key, '')))
        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)
        self.current_task_id = None
        self.update_credit_display()

    def clear_outputs(self):
        for widgets in self.output_widgets.values():
            self.set_widget_text(widgets['field'], "")

    def update_credit_display(self):
        self.credit_label.setText("...새로고침 중...")
        url = "http://localhost:8080/api/v1/members/me"
        headers = {"Authorization": self.access_token}
        self.credit_worker = ApiWorker('GET', url, headers=headers)
        self.credit_worker.finished.connect(self.handle_credit_response)
        self.credit_worker.start()

    def handle_credit_response(self, result):
        if result.get('ok'):
            json_body = result.get('json', {})
            credit = json_body.get('credit', 'N/A')
            self.credit_label.setText(f"남은 크레딧: {credit}")
        else:
            self.credit_label.setText("크레딧 조회 실패")

    def handle_task_start_response(self, result):
        if not result.get('ok'):
            self.update_credit_display()
            self.handle_error(result.get('json', {}).get('message', result.get('error', '알 수 없는 오류')))
            return

        json_body = result.get('json', {})
        is_electronic = self.radio_electronic.isChecked()
        if is_electronic and "taskId" in json_body:
            self.current_task_id = json_body["taskId"]
            self.status_label.setText("상태: ⏳ 폴링 시작...")
            self.polling_timer.start(3000)
        else:
            self.handle_api_result(json_body.get("result", json_body))

    def check_task_status(self):
        if not self.current_task_id:
            return
        url = f"http://localhost:8080/api/v1/generation/result/{self.current_task_id}"
        headers = {"Authorization": self.access_token}
        self.worker = ApiWorker('GET', url, headers=headers, timeout=5)
        self.worker.finished.connect(self.handle_polling_response)
        self.worker.start()

    def handle_polling_response(self, result):
        if not result.get('ok'):
            self.polling_timer.stop()
            self.handle_error(result.get('json', {}).get('message', result.get('error', '알 수 없는 오류')))
            return
        json_body = result.get('json', {})
        status = json_body.get("status")
        if status == "COMPLETED":
            self.polling_timer.stop()
            self.handle_api_result(json_body.get("result"))
        elif status in ["FAILED", "CANCELLED", "NOT_FOUND"]:
            self.polling_timer.stop()
            self.handle_error(f"작업 실패 또는 취소됨 (상태: {status})")
        else:
            self.status_label.setText("상태: ⏳ 작업 진행 중...")

    def cancel_api_call(self):
        if not self.current_task_id:
            return
        self.polling_timer.stop()
        self.status_label.setText("상태: ❌ 작업 취소 요청 중...")
        url = f"http://localhost:8080/api/v1/generation/cancel/{self.current_task_id}"
        headers = {"Authorization": self.access_token}
        self.worker = ApiWorker('POST', url, headers=headers, timeout=10)
        self.worker.finished.connect(self.handle_cancel_response)
        self.worker.start()

    def handle_cancel_response(self, result):
        if result.get('ok') and result.get('json', {}).get("success"):
            self.status_label.setText("상태: ❌ 작업이 성공적으로 취소되었습니다.")
        else:
            self.status_label.setText("상태: ❌ 작업 취소에 실패했습니다.")
        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)
        self.current_task_id = None

    def handle_error(self, error_message):
        self.status_label.setText(f"상태: ❌ 오류 발생")
        QMessageBox.critical(self, "오류", str(error_message))
        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)
        self.current_task_id = None

    def set_widget_text(self, widget, text):
        if isinstance(widget, QLineEdit):
            widget.setText(text)
        elif isinstance(widget, QTextEdit):
            widget.setText(text)

    def copy_to_clipboard(self, text_widget):
        text = text_widget.text() if isinstance(text_widget, QLineEdit) else text_widget.toPlainText()
        if text:
            pyperclip.copy(text)

    def start_payment_request(self):
        amount = self.plan_combo.currentData()
        if amount == 0:
            QMessageBox.warning(self, "플랜 선택", "먼저 충전할 플랜을 선택해주세요.")
            return

        if not self.toss_client_key:
            QMessageBox.warning(self, "설정 오류", "Toss 클라이언트 키가 로드되지 않았습니다. config.ini 파일을 확인하세요.")
            return

        order_name = self.plan_combo.currentText().split('(')[0].strip()

        self.payment_button.setEnabled(False)
        self.payment_button.setText("주문 생성중...")

        # 백엔드에 주문 생성 요청 (PaymentController: /api/v1/payments/request)
        url = 'http://localhost:8080/api/v1/payments/request'
        headers = {"Authorization": self.access_token, "Content-Type": "application/json"}
        payload = {"amount": amount, "orderName": order_name}

        self.payment_worker = ApiWorker('POST', url, payload=payload, headers=headers)
        self.payment_worker.finished.connect(self.handle_payment_request_response)
        self.payment_worker.start()

    # [추가] 주문 생성 응답 처리
    def handle_payment_request_response(self, response):
        self.payment_button.setEnabled(True)
        self.payment_button.setText("🚀 크레딧 충전")

        if not response.get('ok'):
            QMessageBox.critical(self, "오류", response.get('json', {}).get('message', '결제 주문 생성에 실패했습니다.'))
            return

        # 백엔드 응답 데이터 (orderId, customerKey 등)
        data = response.get('json', {})
        order_id = data.get('orderId')
        order_name = data.get('orderName')

        if not order_id or not order_name:
            QMessageBox.critical(self, "오류", "백엔드로부터 주문 정보를 받아오지 못했습니다.")
            return

        # 결제창 열기 (데이터 전달)
        self.open_payment_window(data)

    def open_payment_window(self, payment_data):
        # PaymentWindow에 백엔드 데이터와 클라이언트 키 전달
        self.pay_win = PaymentWindow(payment_data, self.toss_client_key)
        self.pay_win.payment_success.connect(self.handle_payment_success)
        self.pay_win.show()

    def handle_payment_success(self):
        QMessageBox.information(self, "결제 성공", "결제가 성공적으로 완료되었습니다. 크레딧을 새로고침합니다.")
        self.update_credit_display()

    def load_client_key(self):
        try:
            import configparser
            import os

            config = configparser.ConfigParser()
            # 현재 파일(main_window.py)과 같은 폴더의 config.ini를 찾음
            config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'config.ini')

            if not os.path.exists(config_path):
                print(f"설정 파일 없음: {config_path}")
                return

            config.read(config_path, encoding='utf-8')

            if 'keys' in config and 'toss_client_key' in config['keys']:
                self.toss_client_key = config['keys']['toss_client_key']
                print(f"Client Key 로드 성공: {self.toss_client_key[:5]}***")
            else:
                print("config.ini에 [keys] 섹션이나 toss_client_key가 없습니다.")

        except Exception as e:
            print(f"키 로드 중 오류 발생: {e}")