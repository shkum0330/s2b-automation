import sys
import requests
import pyperclip
from PyQt5.QtWidgets import (QApplication, QWidget, QLabel, QLineEdit,
                             QTextEdit, QPushButton, QVBoxLayout, QGroupBox,
                             QGridLayout, QMessageBox, QHBoxLayout)
from PyQt5.QtCore import Qt, QThread, pyqtSignal, QTimer


# 범용 ApiWorker는 짧은 요청들을 처리
class ApiWorker(QThread):
    finished = pyqtSignal(object)

    def __init__(self, method, url, payload=None, headers=None, timeout=65):
        super().__init__()
        self.method = method
        self.url = url
        self.payload = payload
        self.headers = headers
        self.timeout = timeout

    def run(self):
        try:
            if self.method.upper() == 'POST':
                response = requests.post(self.url, json=self.payload, headers=self.headers, timeout=self.timeout)
            else:  # GET
                response = requests.get(self.url, timeout=self.timeout)

            response.raise_for_status()
            self.finished.emit(response.json())
        except requests.exceptions.RequestException as e:
            try:
                error_body = e.response.json()
                self.finished.emit(error_body)
            except:
                self.finished.emit({"error": str(e)})


class S2BApp(QWidget):
    def __init__(self):
        super().__init__()
        self.worker = None
        self.current_task_id = None
        self.polling_timer = QTimer(self)
        self.polling_timer.timeout.connect(self.check_task_status)
        self.output_fields = {}
        self.copy_buttons = {}
        self.initUI()

    def initUI(self):
        # --- UI 구성 코드는 변경 없습니다 ---
        request_group = QGroupBox("1. 서버에 보낼 정보 (GenerateRequest)")
        model_label = QLabel("model:")
        self.model_input = QLineEdit()
        self.model_input.setPlaceholderText("API 요청에 사용할 모델명을 입력하세요")

        product_name_example_label = QLabel("productNameExample:")
        self.product_name_example_input = QLineEdit()
        self.product_name_example_input.setPlaceholderText("물품명 예시를 입력하세요 (선택 사항)")

        spec_example_label = QLabel("specExample:")
        self.spec_example_input = QTextEdit()
        self.spec_example_input.setPlaceholderText("API 요청에 사용할 규격 예시를 입력하세요")
        self.spec_example_input.setFixedHeight(80)

        action_group = QGroupBox("2. 실행")
        self.run_button = QPushButton("🚀 AI로 결과 생성하기")
        self.cancel_button = QPushButton("❌ 취소")
        self.cancel_button.setEnabled(False)

        self.status_label = QLabel("상태: 대기 중...")
        self.status_label.setStyleSheet("color: gray;")

        button_layout = QHBoxLayout()
        button_layout.addWidget(self.run_button)
        button_layout.addWidget(self.cancel_button)

        action_layout = QVBoxLayout()
        action_layout.addLayout(button_layout)
        action_layout.addWidget(self.status_label)
        action_group.setLayout(action_layout)

        response_group = QGroupBox("3. 서버로부터 받은 결과 (GenerateResponse)")
        fields_info = [
            ("productName", QLineEdit), ("specification", QTextEdit),
            ("modelName", QLineEdit), ("manufacturer", QLineEdit),
            ("countryOfOrigin", QLineEdit), ("g2bClassificationNumber", QLineEdit)
        ]
        cert_fields_info = [
            ("katsCertificationNumber", QLineEdit), ("kcCertificationNumber", QLineEdit)
        ]
        res_layout = QGridLayout()
        for i, (field_name, widget_type) in enumerate(fields_info):
            label = QLabel(f"{field_name}:")
            output_widget = widget_type()
            output_widget.setReadOnly(True)
            if widget_type == QTextEdit:
                output_widget.setFixedHeight(80)
            copy_button = QPushButton("복사")
            copy_button.clicked.connect(lambda _, text_widget=output_widget: self.copy_to_clipboard(text_widget))
            copy_button.setFixedWidth(50)
            self.output_fields[field_name] = output_widget
            self.copy_buttons[field_name] = copy_button
            res_layout.addWidget(label, i, 0, Qt.AlignTop if widget_type == QTextEdit else Qt.AlignLeft)
            res_layout.addWidget(output_widget, i, 1)
            res_layout.addWidget(copy_button, i, 2)
        cert_group = QGroupBox("Certification")
        cert_layout = QGridLayout(cert_group)
        for i, (field_name, widget_type) in enumerate(cert_fields_info):
            label = QLabel(f"{field_name}:")
            output_widget = widget_type()
            output_widget.setReadOnly(True)
            copy_button = QPushButton("복사")
            copy_button.clicked.connect(lambda _, text_widget=output_widget: self.copy_to_clipboard(text_widget))
            copy_button.setFixedWidth(50)
            self.output_fields[field_name] = output_widget
            self.copy_buttons[field_name] = copy_button
            cert_layout.addWidget(label, i, 0)
            cert_layout.addWidget(output_widget, i, 1)
            cert_layout.addWidget(copy_button, i, 2)
        res_layout.addWidget(cert_group, len(fields_info), 0, 1, 3)
        response_group.setLayout(res_layout)

        req_layout = QGridLayout()
        req_layout.addWidget(model_label, 0, 0)
        req_layout.addWidget(self.model_input, 0, 1)
        req_layout.addWidget(product_name_example_label, 1, 0)
        req_layout.addWidget(self.product_name_example_input, 1, 1)
        req_layout.addWidget(spec_example_label, 2, 0, Qt.AlignTop)
        req_layout.addWidget(self.spec_example_input, 2, 1)
        request_group.setLayout(req_layout)

        main_layout = QVBoxLayout(self)
        main_layout.addWidget(request_group)
        main_layout.addWidget(action_group)
        main_layout.addWidget(response_group)

        self.run_button.clicked.connect(self.start_api_call)
        self.cancel_button.clicked.connect(self.cancel_api_call)

        self.setWindowTitle("S2B 상품 정보 AI 생성기")
        self.setGeometry(300, 300, 700, 800)
        self.show()

    def start_api_call(self):
        model = self.model_input.text()
        spec_example = self.spec_example_input.toPlainText()
        product_name_example = self.product_name_example_input.text()

        if not model or not spec_example:
            QMessageBox.warning(self, "입력 오류", "model과 specExample은 반드시 입력해야 합니다.")
            return

        self.run_button.setEnabled(False)
        self.cancel_button.setEnabled(True)
        self.status_label.setText("상태: 🤖 작업 시작 요청 중 (최대 65초 대기)...")
        self.status_label.setStyleSheet("color: blue;")
        self.clear_outputs()

        payload = {"model": model, "specExample": spec_example, "productNameExample": product_name_example}
        headers = {"Content-Type": "application/json"}
        self.worker = ApiWorker('POST', 'http://localhost:8080/api/generate-spec', payload=payload, headers=headers,
                                timeout=65)
        self.worker.finished.connect(self.handle_task_start_response)
        self.worker.start()

    def handle_task_start_response(self, result):
        if "taskId" in result:
            self.current_task_id = result["taskId"]
            self.status_label.setText(f"상태: ⏳ 작업이 오래 걸려 폴링을 시작합니다 (ID: ...{self.current_task_id[-6:]}).")
            self.polling_timer.start(3000)
        elif "productName" in result or result.get("status") == "COMPLETED":
            result_data = result.get("result", result)
            self.handle_api_result(result_data)
        else:
            self.handle_error(result.get("error") or result.get("message", "알 수 없는 응답 형식"))

    def check_task_status(self):
        if not self.current_task_id:
            return

        url = f"http://localhost:8080/api/result/{self.current_task_id}"
        self.worker = ApiWorker('GET', url, timeout=5)
        self.worker.finished.connect(self.handle_polling_response)
        self.worker.start()

    def handle_polling_response(self, result):
        if "error" in result:
            self.handle_error(result.get("error"))
            self.polling_timer.stop()
            return

        status = result.get("status")
        if status == "COMPLETED":
            self.polling_timer.stop()
            self.handle_api_result(result.get("result"))
        elif status in ["FAILED", "CANCELLED", "NOT_FOUND"]:
            self.polling_timer.stop()
            self.handle_error(f"작업 실패 또는 취소됨 (상태: {status})")
        else:
            self.status_label.setText(f"상태: ⏳ 작업 진행 중... (ID: ...{self.current_task_id[-6:]}).")

    # --- cancel_api_call과 handle_cancel_response 수정 ---
    def cancel_api_call(self):
        if not self.current_task_id:
            # 아직 taskId를 받기 전에 취소 버튼을 누른 경우
            self.handle_error("작업 시작 전에 취소되었습니다.")
            return

        self.polling_timer.stop()  # 가장 먼저 폴링 중단
        self.status_label.setText("상태: ❌ 작업 취소 요청 중...")
        self.status_label.setStyleSheet("color: orange;")
        self.cancel_button.setEnabled(False)  # 중복 클릭 방지

        url = f"http://localhost:8080/api/cancel/{self.current_task_id}"
        self.worker = ApiWorker('POST', url, timeout=10)
        # 취소 요청의 응답은 새로운 핸들러에서 처리
        self.worker.finished.connect(self.handle_cancel_response)
        self.worker.start()

    def handle_cancel_response(self, result):
        """취소 요청에 대한 서버 응답을 처리하고 UI 상태를 최종 정리합니다."""
        if result.get("success"):
            self.status_label.setText("상태: ❌ 작업이 사용자에 의해 성공적으로 취소되었습니다.")
            self.status_label.setStyleSheet("color: red;")
        else:
            self.status_label.setText("상태: ❌ 작업 취소에 실패했습니다.")
            self.status_label.setStyleSheet("color: red;")
            QMessageBox.warning(self, "취소 실패", f"작업 취소에 실패했습니다: {result.get('error', '')}")

        self.current_task_id = None
        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)

    # --- 수정 완료 ---

    def handle_api_result(self, result):
        self.status_label.setText("상태: ✅ AI 생성 완료!")
        self.status_label.setStyleSheet("color: green;")

        for field_name, output_widget in self.output_fields.items():
            if field_name in ["katsCertificationNumber", "kcCertificationNumber"]:
                cert_data = result.get("certification", {})
                text_value = str(cert_data.get(field_name, '') if cert_data else '')
            else:
                text_value = str(result.get(field_name, ''))

            self.set_widget_text(output_widget, text_value)

        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)
        self.current_task_id = None

    def handle_error(self, error_message):
        self.status_label.setText(f"상태: ❌ 오류 발생")
        self.status_label.setStyleSheet("color: red;")
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
        text_to_copy = ""
        if isinstance(text_widget, QLineEdit):
            text_to_copy = text_widget.text()
        elif isinstance(text_widget, QTextEdit):
            text_to_copy = text_widget.toPlainText()

        if text_to_copy:
            pyperclip.copy(text_to_copy)


if __name__ == '__main__':
    app = QApplication(sys.argv)
    ex = S2BApp()
    sys.exit(app.exec_())