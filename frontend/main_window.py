import pyperclip
from PyQt5.QtWidgets import (QWidget, QLabel, QLineEdit, QTextEdit,
                             QPushButton, QVBoxLayout, QGroupBox, QGridLayout,
                             QMessageBox, QHBoxLayout, QSpacerItem, QSizePolicy)
from PyQt5.QtCore import Qt, QTimer
from api_worker import ApiWorker


class MainWindow(QWidget):
    def __init__(self, access_token=None):
        super().__init__()
        self.access_token = access_token
        self.worker = None
        self.current_task_id = None
        self.polling_timer = QTimer(self)
        self.polling_timer.timeout.connect(self.check_task_status)
        self.output_fields = {}
        self.copy_buttons = {}
        self.initUI()
        self.update_credit_display()

    def initUI(self):
        self.credit_label = QLabel("남은 크레딧: -")
        self.refresh_button = QPushButton("새로고침")
        self.refresh_button.setFixedWidth(80)
        self.refresh_button.clicked.connect(self.update_credit_display)

        credit_layout = QHBoxLayout()
        credit_layout.addStretch(1)
        credit_layout.addWidget(self.credit_label)
        credit_layout.addWidget(self.refresh_button)

        request_group = QGroupBox("서버에 보낼 정보")
        product_name_example_label = QLabel("1. 물품(용역)명:")
        self.product_name_example_input = QLineEdit()
        spec_example_label = QLabel("2. 규격 예시:")
        self.spec_example_input = QTextEdit()
        self.spec_example_input.setFixedHeight(80)
        model_label = QLabel("3. 모델명:")
        self.model_input = QLineEdit()

        req_layout = QGridLayout()
        req_layout.addWidget(product_name_example_label, 0, 0)
        req_layout.addWidget(self.product_name_example_input, 0, 1)
        req_layout.addWidget(spec_example_label, 1, 0, Qt.AlignTop)
        req_layout.addWidget(self.spec_example_input, 1, 1)
        req_layout.addWidget(model_label, 2, 0)
        req_layout.addWidget(self.model_input, 2, 1)
        request_group.setLayout(req_layout)

        action_group = QGroupBox("2. 실행")
        self.run_button = QPushButton("🚀 AI로 결과 생성하기")
        self.cancel_button = QPushButton("❌ 취소")
        self.cancel_button.setEnabled(False)
        self.status_label = QLabel("상태: 대기 중...")

        button_layout = QHBoxLayout()
        button_layout.addWidget(self.run_button)
        button_layout.addWidget(self.cancel_button)

        action_layout = QVBoxLayout()
        action_layout.addLayout(button_layout)
        action_layout.addWidget(self.status_label)
        action_group.setLayout(action_layout)

        response_group = QGroupBox("서버로부터 받은 결과")
        ordered_fields_info = [
            ("productName", "1. 물품(용역)명:", QLineEdit),
            ("specification", "2. 규격(사양, 용량, 색상, 판매개수 등):", QTextEdit),
            ("modelName", "3. 모델명:", QLineEdit),
            ("manufacturer", "4. 제조사:", QLineEdit),
            ("katsCertificationNumber", "5. 전기용품 인증정보:", QLineEdit),
            ("kcCertificationNumber", "6. 방송통신기자재 인증정보:", QLineEdit),
            ("g2bClassificationNumber", "7. G2B 물품목록번호:", QLineEdit)
        ]
        res_layout = QGridLayout()
        for i, (field_name, korean_label, widget_type) in enumerate(ordered_fields_info):
            label = QLabel(korean_label)
            output_widget = widget_type()
            output_widget.setReadOnly(True)
            if widget_type == QTextEdit:
                output_widget.setFixedHeight(80)

            copy_button = QPushButton("복사")
            copy_button.clicked.connect(lambda _, text_widget=output_widget: self.copy_to_clipboard(text_widget))
            copy_button.setFixedWidth(50)

            self.output_fields[field_name] = output_widget
            res_layout.addWidget(label, i, 0, Qt.AlignTop if widget_type == QTextEdit else Qt.AlignLeft)
            res_layout.addWidget(output_widget, i, 1)
            res_layout.addWidget(copy_button, i, 2)
        response_group.setLayout(res_layout)

        main_layout = QVBoxLayout(self)
        main_layout.addLayout(credit_layout)
        main_layout.addWidget(request_group)
        main_layout.addWidget(action_group)
        main_layout.addWidget(response_group)
        self.run_button.clicked.connect(self.start_api_call)
        self.cancel_button.clicked.connect(self.cancel_api_call)
        self.setWindowTitle("S2B 상품 정보 AI 생성기")
        self.setGeometry(300, 300, 700, 800)

    def update_credit_display(self):
        self.credit_label.setText("...새로고침 중...")
        url = "http://localhost:8080/api/v1/members/me"
        headers = {"Authorization": self.access_token}
        self.credit_worker = ApiWorker('GET', url, headers=headers)
        self.credit_worker.finished.connect(self.handle_credit_response)
        self.credit_worker.start()

    def handle_credit_response(self, result):
        # --- [MODIFIED] ---
        # 사용자 역할(role)에 관계없이 모든 사용자에게 남은 크레딧을 표시
        if result.get('ok'):
            json_body = result.get('json', {})
            credit = json_body.get('credit', 'N/A')
            self.credit_label.setText(f"남은 크레딧: {credit}")
        else:
            self.credit_label.setText("크레딧 조회 실패")
        # ------------------

    def handle_api_result(self, result):
        self.status_label.setText("상태: ✅ AI 생성 완료!")
        for field_name, output_widget in self.output_fields.items():
            self.set_widget_text(output_widget, str(result.get(field_name, '')))
        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)
        self.current_task_id = None
        self.update_credit_display()

    def start_api_call(self):
        model = self.model_input.text()
        spec_example = self.spec_example_input.toPlainText()
        product_name_example = self.product_name_example_input.text()

        if not model or not spec_example:
            QMessageBox.warning(self, "입력 오류", "모델명과 규격 예시는 반드시 입력해야 합니다.")
            return

        self.run_button.setEnabled(False)
        self.cancel_button.setEnabled(True)
        self.status_label.setText("상태: 🤖 작업 시작 요청 중...")
        self.clear_outputs()

        headers = {
            "Content-Type": "application/json",
            "Authorization": self.access_token
        }

        payload = {"model": model, "specExample": spec_example, "productNameExample": product_name_example}

        self.worker = ApiWorker('POST', 'http://localhost:8080/api/v1/generation/generate-spec', payload=payload,
                                headers=headers, timeout=65)
        self.worker.finished.connect(self.handle_task_start_response)
        self.worker.start()

    def handle_task_start_response(self, result):
        if not result.get('ok'):
            self.update_credit_display()
            self.handle_error(result.get('json', {}).get('message', result.get('error', '알 수 없는 오류')))
            return

        json_body = result.get('json', {})
        if "taskId" in json_body:
            self.current_task_id = json_body["taskId"]
            self.status_label.setText(f"상태: ⏳ 폴링 시작...")
            self.polling_timer.start(3000)
        elif "productName" in json_body or json_body.get("status") == "COMPLETED":
            self.handle_api_result(json_body.get("result", json_body))
        else:
            self.handle_error(json_body.get("error") or json_body.get("message", "알 수 없는 응답"))

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
            self.status_label.setText(f"상태: ⏳ 작업 진행 중...")

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

    def clear_outputs(self):
        for output_widget in self.output_fields.values():
            self.set_widget_text(output_widget, "")

    def set_widget_text(self, widget, text):
        if isinstance(widget, QLineEdit):
            widget.setText(text)
        elif isinstance(widget, QTextEdit):
            widget.setText(text)

    def copy_to_clipboard(self, text_widget):
        text = text_widget.text() if isinstance(text_widget, QLineEdit) else text_widget.toPlainText()
        if text:
            pyperclip.copy(text)