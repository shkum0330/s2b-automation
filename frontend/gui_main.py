import sys
import requests
from PyQt5.QtWidgets import (QApplication, QWidget, QLabel, QLineEdit,
                             QTextEdit, QPushButton, QVBoxLayout, QGroupBox, QGridLayout, QMessageBox)
from PyQt5.QtCore import Qt, QThread, pyqtSignal


# -------------------------------------------------------------------
# 1. 백엔드 API와 통신하는 백그라운드 작업자(Worker) 클래스
# -------------------------------------------------------------------
class ApiWorker(QThread):
    # GUI를 업데이트하기 위한 신호: 성공 시 dict, 실패 시 str
    task_finished = pyqtSignal(object)

    def __init__(self, model, example):
        super().__init__()
        self.model = model
        self.example = example

    def run(self):
        """백그라운드 스레드에서 Spring Boot API를 호출합니다."""
        api_url = "http://localhost:8080/api/generate-spec"
        # GenerateRequest.java에 명시된 대로 model, example을 JSON으로 구성
        payload = {"model": self.model, "example": self.example}
        headers = {"Content-Type": "application/json"}

        try:
            response = requests.post(api_url, json=payload, headers=headers, timeout=30)
            response.raise_for_status()  # HTTP 오류가 2xx가 아니면 예외 발생
            self.task_finished.emit(response.json())
        except requests.exceptions.RequestException as e:
            self.task_finished.emit(f"서버 통신 오류: {e}")


# -------------------------------------------------------------------
# 2. 메인 GUI 윈도우 클래스
# -------------------------------------------------------------------
class S2BApp(QWidget):
    def __init__(self):
        super().__init__()
        self.worker = None
        self.initUI()

    def initUI(self):
        # --- 1. 요청(Request) UI 그룹 ---
        request_group = QGroupBox("1. 서버에 보낼 정보 (GenerateRequest)")

        # 'model' 필드
        model_label = QLabel("model:")
        self.model_input = QLineEdit()
        self.model_input.setPlaceholderText("API 요청에 사용할 모델명을 입력하세요")

        # 'example' 필드
        spec_label = QLabel("example:")
        self.spec_input = QTextEdit()
        self.spec_input.setPlaceholderText("API 요청에 사용할 규격 예시를 입력하세요")
        self.spec_input.setFixedHeight(80)

        # --- 2. 실행 및 상태 UI 그룹 ---
        action_group = QGroupBox("2. 실행")
        self.run_button = QPushButton("🚀 AI로 결과 생성하기")
        self.status_label = QLabel("상태: 대기 중...")
        self.status_label.setStyleSheet("color: gray;")

        # --- 3. 응답(Response) UI 그룹 ---
        response_group = QGroupBox("3. 서버로부터 받은 결과 (GenerateResponse)")

        # 5개의 응답 필드를 모두 읽기 전용(ReadOnly)으로 생성
        self.product_name_output = QLineEdit()
        self.product_name_output.setReadOnly(True)
        self.spec_output = QTextEdit()
        self.spec_output.setReadOnly(True)
        self.spec_output.setFixedHeight(80)
        self.model_name_output = QLineEdit()
        self.model_name_output.setReadOnly(True)
        self.kats_cert_output = QLineEdit()
        self.kats_cert_output.setReadOnly(True)
        self.kc_cert_output = QLineEdit()
        self.kc_cert_output.setReadOnly(True)

        # --- 레이아웃 설정 ---
        req_layout = QGridLayout()
        req_layout.addWidget(model_label, 0, 0)
        req_layout.addWidget(self.model_input, 0, 1)
        req_layout.addWidget(spec_label, 1, 0, Qt.AlignTop)
        req_layout.addWidget(self.spec_input, 1, 1)
        request_group.setLayout(req_layout)

        action_layout = QVBoxLayout()
        action_layout.addWidget(self.run_button)
        action_layout.addWidget(self.status_label)
        action_group.setLayout(action_layout)

        res_layout = QGridLayout()
        res_layout.addWidget(QLabel("productName:"), 0, 0)
        res_layout.addWidget(self.product_name_output, 0, 1)
        res_layout.addWidget(QLabel("specification:"), 1, 0, Qt.AlignTop)
        res_layout.addWidget(self.spec_output, 1, 1)
        res_layout.addWidget(QLabel("modelName:"), 2, 0)
        res_layout.addWidget(self.model_name_output, 2, 1)
        res_layout.addWidget(QLabel("katsCertificationNumber:"), 3, 0)
        res_layout.addWidget(self.kats_cert_output, 3, 1)
        res_layout.addWidget(QLabel("kcCertificationNumber:"), 4, 0)
        res_layout.addWidget(self.kc_cert_output, 4, 1)
        response_group.setLayout(res_layout)

        main_layout = QVBoxLayout(self)
        main_layout.addWidget(request_group)
        main_layout.addWidget(action_group)
        main_layout.addWidget(response_group)

        # --- 버튼 클릭 이벤트 연결 ---
        self.run_button.clicked.connect(self.start_api_call)

        self.setWindowTitle("S2B 상품 정보 AI 생성기")
        self.setGeometry(300, 300, 500, 550)  # 창 크기 조정
        self.show()

    def start_api_call(self):
        model = self.model_input.text()
        example = self.spec_input.toPlainText()

        if not model or not example:
            QMessageBox.warning(self, "입력 오류", "model과 example을 모두 입력해야 합니다.")
            return

        self.run_button.setEnabled(False)
        self.status_label.setText("상태: 🤖 Spring Boot 서버에 AI 생성을 요청합니다...")
        self.status_label.setStyleSheet("color: blue;")

        # 백그라운드 스레드 생성 및 시작
        self.worker = ApiWorker(model, example)
        self.worker.task_finished.connect(self.handle_api_result)
        self.worker.start()

    def handle_api_result(self, result):
        # 서버로부터 받은 결과를 GUI에 업데이트
        if isinstance(result, dict):  # 결과가 dict(JSON) 형태이면 성공
            self.status_label.setText("상태: ✅ AI 생성 완료!")
            self.status_label.setStyleSheet("color: green;")

            # GenerateResponse.java의 필드 이름과 정확히 일치시켜 값을 채움
            self.product_name_output.setText(result.get('productName', ''))
            self.spec_output.setText(result.get('specification', ''))
            self.model_name_output.setText(result.get('modelName', ''))
            self.kats_cert_output.setText(result.get('katsCertificationNumber', ''))
            self.kc_cert_output.setText(result.get('kcCertificationNumber', ''))

        else:  # 그 외 (문자열)이면 실패
            self.status_label.setText(f"상태: ❌ {result}")
            self.status_label.setStyleSheet("color: red;")
            QMessageBox.critical(self, "API 오류", str(result))

        self.run_button.setEnabled(True)

    # --- 메인 프로그램 실행 부분 ---


if __name__ == '__main__':
    app = QApplication(sys.argv)
    ex = S2BApp()
    sys.exit(app.exec_())