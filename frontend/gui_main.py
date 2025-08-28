import sys
import requests
import pyperclip
from PyQt5.QtWidgets import (QApplication, QWidget, QLabel, QLineEdit,
                             QTextEdit, QPushButton, QVBoxLayout, QGroupBox,
                             QGridLayout, QMessageBox, QTableWidget, QTableWidgetItem, QHeaderView)
from PyQt5.QtCore import Qt, QThread, pyqtSignal


class ApiWorker(QThread):
    task_finished = pyqtSignal(object)

    def __init__(self, model, spec_example, product_name_example):
        super().__init__()
        self.model = model
        self.spec_example = spec_example
        self.product_name_example = product_name_example

    def run(self):
        api_url = "http://localhost:8080/api/generate-spec"
        payload = {
            "model": self.model,
            "specExample": self.spec_example,
            "productNameExample": self.product_name_example
        }
        headers = {"Content-Type": "application/json"}
        try:
            response = requests.post(api_url, json=payload, headers=headers, timeout=120)
            response.raise_for_status()
            self.task_finished.emit(response.json())
        except requests.exceptions.Timeout:
            self.task_finished.emit("서버 응답 시간 초과 (Timeout)")
        except requests.exceptions.RequestException as e:
            self.task_finished.emit(f"서버 통신 오류: {e}")


class S2BApp(QWidget):
    def __init__(self):
        super().__init__()
        self.worker = None
        self.initUI()

    def initUI(self):
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
        self.status_label = QLabel("상태: 대기 중...")
        self.status_label.setStyleSheet("color: gray;")

        response_group = QGroupBox("3. 서버로부터 받은 결과 (GenerateResponse)")

        # 출력 필드와 복사 버튼들을 딕셔너리로 관리
        self.output_fields = {}
        self.copy_buttons = {}

        # 필드 정의 (라벨, 출력 위젯 타입)
        fields_info = [
            ("productName", QLineEdit),
            ("specification", QTextEdit),
            ("modelName", QLineEdit),
            ("manufacturer", QLineEdit),
            ("countryOfOrigin", QLineEdit),
            ("g2bClassificationNumber", QLineEdit),
            ("katsCertificationNumber", QLineEdit),
            ("kcCertificationNumber", QLineEdit),
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
            copy_button.setFixedWidth(50)  # 버튼 너비 조정

            self.output_fields[field_name] = output_widget
            self.copy_buttons[field_name] = copy_button

            res_layout.addWidget(label, i, 0, Qt.AlignTop if widget_type == QTextEdit else Qt.AlignLeft)
            res_layout.addWidget(output_widget, i, 1)
            res_layout.addWidget(copy_button, i, 2)

        response_group.setLayout(res_layout)

        req_layout = QGridLayout()
        req_layout.addWidget(model_label, 0, 0)
        req_layout.addWidget(self.model_input, 0, 1)
        req_layout.addWidget(product_name_example_label, 1, 0)
        req_layout.addWidget(self.product_name_example_input, 1, 1)
        req_layout.addWidget(spec_example_label, 2, 0, Qt.AlignTop)
        req_layout.addWidget(self.spec_example_input, 2, 1)
        request_group.setLayout(req_layout)

        action_layout = QVBoxLayout()
        action_layout.addWidget(self.run_button)
        action_layout.addWidget(self.status_label)
        action_group.setLayout(action_layout)

        main_layout = QVBoxLayout(self)
        main_layout.addWidget(request_group)
        main_layout.addWidget(action_group)
        main_layout.addWidget(response_group)

        self.run_button.clicked.connect(self.start_api_call)
        self.setWindowTitle("S2B 상품 정보 AI 생성기")
        self.setGeometry(300, 300, 700, 700)  # 창 크기 조정 (너비 늘림)
        self.show()

    def start_api_call(self):
        model = self.model_input.text()
        spec_example = self.spec_example_input.toPlainText()
        product_name_example = self.product_name_example_input.text()

        if not model or not spec_example:
            QMessageBox.warning(self, "입력 오류", "model과 specExample은 반드시 입력해야 합니다.")
            return

        self.run_button.setEnabled(False)
        self.status_label.setText("상태: 🤖 Spring Boot 서버에 AI 생성을 요청합니다...")
        self.status_label.setStyleSheet("color: blue;")
        self.clear_outputs()

        self.worker = ApiWorker(model, spec_example, product_name_example)
        self.worker.task_finished.connect(self.handle_api_result)
        self.worker.start()

    def handle_api_result(self, result):
        if isinstance(result, dict):
            self.status_label.setText("상태: ✅ AI 생성 완료!")
            self.status_label.setStyleSheet("color: green;")

            for field_name, output_widget in self.output_fields.items():
                text_value = str(result.get(field_name, ''))
                if isinstance(output_widget, QLineEdit):
                    output_widget.setText(text_value)
                elif isinstance(output_widget, QTextEdit):
                    output_widget.setText(text_value)
        else:
            self.status_label.setText(f"상태: ❌ {result}")
            self.status_label.setStyleSheet("color: red;")
            QMessageBox.critical(self, "API 오류", str(result))

        self.run_button.setEnabled(True)

    def clear_outputs(self):
        for output_widget in self.output_fields.values():
            if isinstance(output_widget, QLineEdit):
                output_widget.clear()
            elif isinstance(output_widget, QTextEdit):
                output_widget.clear()

    def copy_to_clipboard(self, text_widget):
        text_to_copy = ""
        if isinstance(text_widget, QLineEdit):
            text_to_copy = text_widget.text()
        elif isinstance(text_widget, QTextEdit):
            text_to_copy = text_widget.toPlainText()

        # 내용이 있을 때만 pyperclip을 사용하여 클립보드에 복사합니다.
        if text_to_copy:
            pyperclip.copy(text_to_copy)


if __name__ == '__main__':
    app = QApplication(sys.argv)
    ex = S2BApp()
    sys.exit(app.exec_())