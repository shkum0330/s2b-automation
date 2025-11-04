# main_window.py

import pyperclip
from PyQt5.QtWidgets import (QWidget, QLabel, QLineEdit, QTextEdit,
                             QPushButton, QVBoxLayout, QGroupBox, QGridLayout,
                             QMessageBox, QHBoxLayout, QRadioButton, QFrame,
                             QComboBox)  # QComboBox 추가
from PyQt5.QtCore import Qt, QTimer
from PyQt5.QtGui import QFont
from api_worker import ApiWorker
from payment_window import PaymentWindow  # [새로 추가] payment_window 임포트


class MainWindow(QWidget):
    def __init__(self, access_token=None):
        super().__init__()
        self.access_token = access_token
        self.worker = None
        self.payment_worker = None  # [새로 추가] 결제용 API 워커
        self.current_task_id = None
        self.polling_timer = QTimer(self)
        self.polling_timer.timeout.connect(self.check_task_status)

        # [새로 추가] 토스페이먼츠 샌드박스(테스트) 클라이언트 키
        # (주의: 실제 운영 시에는 이 키를 안전한 곳에서 불러와야 합니다)
        self.toss_client_key = "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq"

        self.input_widgets = {}
        self.output_widgets = {}

        self.initUI()
        self.update_credit_display()
        self._update_ui_for_product_type()

    def initUI(self):
        default_font = QFont("Apple SD Gothic Neo", 13)

        # ... (기존 product_type_layout 코드 생략) ...
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

        self.credit_label = QLabel("남은 크레딧: -")
        self.credit_label.setFont(default_font)
        self.refresh_button = QPushButton("새로고침")
        self.refresh_button.setFixedWidth(150)
        self.refresh_button.setFont(default_font)
        self.refresh_button.clicked.connect(self.update_credit_display)

        # --- [수정] 결제 UI 추가 ---
        self.plan_combo = QComboBox()
        self.plan_combo.setFont(default_font)
        self.plan_combo.addItem("플랜 선택", 0)  # data=0
        self.plan_combo.addItem("30일 10개 플랜 (29,900원)", 29900)
        self.plan_combo.addItem("30일 20개 플랜 (49,900원)", 49900)
        self.plan_combo.addItem("30일 50개 플랜 (100,000원)", 100000)

        self.payment_button = QPushButton("🚀 크레딧 충전")
        self.payment_button.setFont(default_font)
        self.payment_button.clicked.connect(self.start_payment_request)  # [새로 추가] 클릭 시그널 연결
        # --- [수정 끝] ---

        separator = QFrame()
        separator.setFrameShape(QFrame.VLine)
        separator.setFrameShadow(QFrame.Sunken)

        separator_2 = QFrame()  # 두 번째 구분선
        separator_2.setFrameShape(QFrame.VLine)
        separator_2.setFrameShadow(QFrame.Sunken)

        top_layout = QHBoxLayout()
        top_layout.addLayout(product_type_layout)
        top_layout.addWidget(separator)
        top_layout.addWidget(self.credit_label)
        top_layout.addWidget(self.refresh_button)
        top_layout.addWidget(separator_2)  # [새로 추가]
        top_layout.addWidget(self.plan_combo)  # [새로 추가]
        top_layout.addWidget(self.payment_button)  # [새로 추가]

        # ... (기존 request_group, response_group, action_group 등 UI 코드 생략) ...
        # (main_layout에 top_layout 추가하는 부분은 이미 있으므로 수정 불필요)
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

    # --- [새로 추가] 결제 요청 시작 메서드 ---
    def start_payment_request(self):
        amount = self.plan_combo.currentData()  # 콤보박스에 저장된 'data' (금액)를 가져옴
        if amount == 0:
            QMessageBox.warning(self, "플랜 선택", "먼저 충전할 플랜을 선택해주세요.")
            return

        self.payment_button.setEnabled(False)
        self.payment_button.setText("주문 생성중...")

        # 백엔드의 /api/v1/payments/request API 호출
        url = 'http://localhost:8080/api/v1/payments/request'
        payload = {"amount": amount}
        headers = {"Content-Type": "application/json", "Authorization": self.access_token}

        self.payment_worker = ApiWorker('POST', url, payload=payload, headers=headers)
        self.payment_worker.finished.connect(self.handle_payment_request_response)
        self.payment_worker.start()

    # --- [새로 추가] 결제 요청 응답 처리 메서드 ---
    def handle_payment_request_response(self, result):
        self.payment_button.setEnabled(True)
        self.payment_button.setText("🚀 크레딧 충전")

        if not result.get('ok'):
            self._handle_error(result, "결제 주문 생성에 실패했습니다.")
            return

        json_body = result.get('json', {})
        order_id = json_body.get('orderId')
        amount = json_body.get('amount')

        if not order_id or not amount:
            QMessageBox.critical(self, "오류", "백엔드로부터 주문 정보를 받아오지 못했습니다.")
            return

        # 백엔드에서 검증된 정보로 결제 창 열기
        order_name = self.plan_combo.currentText().split('(')[0].strip()  # 예: "30일 10개 플랜"

        self.open_payment_window(order_id, order_name, amount)

    # --- [새로 추가] PaymentWindow 팝업 실행 메서드 ---
    def open_payment_window(self, order_id, order_name, amount):
        # QWebEngineView가 포함된 PaymentWindow 대화상자 생성
        dialog = PaymentWindow(
            self.toss_client_key,
            order_id,
            order_name,
            amount,
            self  # 부모 창으로 self 지정
        )

        # [중요] 결제창이 성공 시그널을 보내면, 크레딧 정보를 새로고침
        dialog.payment_success.connect(self.handle_payment_success)

        dialog.exec_()  # 대화상자를 '모달(Modal)'로 실행 (이 창이 닫히기 전까지 main_window 제어 불가)

    # --- [새로 추가] 결제 성공 시그널 처리 슬롯 ---
    def handle_payment_success(self):
        QMessageBox.information(self, "결제 성공", "결제가 성공적으로 완료되었습니다. 크레딧을 새로고침합니다.")
        self.update_credit_display()  # 기존의 크레딧 새로고침 메서드 호출

    # ... (기존의 _update_ui_for_product_type, start_api_call 등 모든 메서드) ...
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
            payload = {"modelName": model, "specExample": spec_example, "productNameExample": product_name_example}
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
        self.worker.finished.connect(self._handle_generation_start_response)
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
        self.credit_worker.finished.connect(self._handle_credit_response)
        self.credit_worker.start()

    def _handle_credit_response(self, result):
        if result.get('ok'):
            json_body = result.get('json', {})
            credit = json_body.get('credit', 'N/A')
            # [수정] Role에 따른 일일 크레딧 표시
            role = json_body.get('role', 'FREE_USER')
            daily_count = json_body.get('dailyRequestCount', 0)

            if 'PLAN' in role:
                limit_map = {'PLAN_30K': 10, 'PLAN_50K': 20, 'PLAN_100K': 50}
                limit = limit_map.get(role, 0)
                self.credit_label.setText(f"오늘 남은 횟수: {limit - daily_count} / {limit}")
            else:  # FREE_USER 또는 ADMIN
                self.credit_label.setText(f"오늘 남은 횟수: 5 / 5")  # (임시로 5/5)
        else:
            self.credit_label.setText("크레딧 조회 실패")

    def _handle_generation_start_response(self, result):
        if not result.get('ok'):
            self.update_credit_display()
            self._handle_error(result)
            return

        json_body = result.get('json', {})
        task_id = json_body.get("taskId")
        if task_id:
            self.current_task_id = task_id
            self.status_label.setText("상태: ⏳ 폴링 시작...")
            self.polling_timer.start(3000)
        else:
            self._handle_api_result(json_body.get("result", json_body))

    def check_task_status(self):
        if not self.current_task_id:
            return
        url = f"http://localhost:8080/api/v1/generation/result/{self.current_task_id}"
        headers = {"Authorization": self.access_token}
        self.worker = ApiWorker('GET', url, headers=headers, timeout=5)
        self.worker.finished.connect(self._handle_polling_response)
        self.worker.start()

    def _handle_polling_response(self, result):
        if not result.get('ok'):
            self.polling_timer.stop()
            self._handle_error(result)
            return
        json_body = result.get('json', {})
        status = json_body.get("status")
        if status == "COMPLETED":
            self.polling_timer.stop()
            self.handle_api_result(json_body.get("result"))
        elif status in ["FAILED", "CANCELLED", "NOT_FOUND"]:
            self.polling_timer.stop()
            self._handle_error(result, f"작업 실패 또는 취소됨 (상태: {status})")
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
        self.worker.finished.connect(self._handle_cancel_response)
        self.worker.start()

    def _handle_cancel_response(self, result):
        if result.get('ok') and result.get('json', {}).get("success"):
            self.status_label.setText("상태: ❌ 작업이 성공적으로 취소되었습니다.")
        else:
            self.status_label.setText("상태: ❌ 작업 취소에 실패했습니다.")
        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)
        self.current_task_id = None

    def _handle_error(self, result, default_message="알 수 없는 오류"):
        error_json = result.get('json', {})
        error_message = error_json.get('message', result.get('error', default_message))
        self.status_label.setText("상태: ❌ 오류 발생")
        QMessageBox.critical(self, "오류", str(error_message))
        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)
        self.current_task_id = None
        self.polling_timer.stop()

    def set_widget_text(self, widget, text):
        if isinstance(widget, QLineEdit):
            widget.setText(text)
        elif isinstance(widget, QTextEdit):
            widget.setText(text)

    def copy_to_clipboard(self, text_widget):
        text = text_widget.text() if isinstance(text_widget, QLineEdit) else text_widget.toPlainText()
        if text:
            pyperclip.copy(text)