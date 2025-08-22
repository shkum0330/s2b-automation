import sys
import requests
from PyQt5.QtWidgets import (QApplication, QWidget, QLabel, QLineEdit,
                             QTextEdit, QPushButton, QVBoxLayout, QGroupBox,
                             QGridLayout, QMessageBox, QTableWidget, QTableWidgetItem, QHeaderView)
from PyQt5.QtCore import Qt, QThread, pyqtSignal


# -------------------------------------------------------------------
# 1. 백엔드 API와 통신하는 백그라운드 작업자(Worker) 클래스
# -------------------------------------------------------------------
class ApiWorker(QThread):
    # GUI를 업데이트하기 위한 신호: 성공 시 dict, 실패 시 str
    task_finished = pyqtSignal(object)

    def __init__(self, model, spec_example, product_name_example):
        super().__init__()
        self.model = model
        self.spec_example = spec_example
        self.product_name_example = product_name_example

    def run(self):
        """백그라운드 스레드에서 Spring Boot API를 호출합니다."""
        api_url = "http://localhost:8080/api/generate-spec"

        # GenerateRequest.java DTO 스펙과 정확히 일치하는 payload 구성
        payload = {
            "model": self.model,
            "specExample": self.spec_example,
            "productNameExample": self.product_name_example
        }
        headers = {"Content-Type": "application/json"}

        try:
            # 타임아웃을 120초(2분)으로 늘려 AI의 긴 응답 시간 대기
            response = requests.post(api_url, json=payload, headers=headers, timeout=120)
            response.raise_for_status()  # HTTP 오류가 2xx가 아니면 예외 발생
            self.task_finished.emit(response.json())
        except requests.exceptions.Timeout:
            self.task_finished.emit("서버 응답 시간 초과 (Timeout)")
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

        model_label = QLabel("model:")
        self.model_input = QLineEdit()
        self.model_input.setPlaceholderText("API 요청에 사용할 모델명을 입력하세요")

        # DTO 필드명인 productNameExample에 맞춰 UI 추가
        product_name_example_label = QLabel("productNameExample:")
        self.product_name_example_input = QLineEdit()
        self.product_name_example_input.setPlaceholderText("물품명 예시를 입력하세요 (선택 사항)")

        # DTO 필드명인 specExample에 맞춰 변수명 변경
        spec_example_label = QLabel("specExample:")
        self.spec_example_input = QTextEdit()
        self.spec_example_input.setPlaceholderText("API 요청에 사용할 규격 예시를 입력하세요")
        self.spec_example_input.setFixedHeight(80)

        # --- 2. 실행 및 상태 UI 그룹 ---
        action_group = QGroupBox("2. 실행")
        self.run_button = QPushButton("🚀 AI로 결과 생성하기")
        self.status_label = QLabel("상태: 대기 중...")
        self.status_label.setStyleSheet("color: gray;")

        # --- 3. 응답(Response) UI 그룹 ---
        response_group = QGroupBox("3. 서버로부터 받은 결과 (GenerateResponse)")

        # GenerateResponse DTO의 모든 필드를 표시하도록 UI 추가
        self.product_name_output = QLineEdit()
        self.product_name_output.setReadOnly(True)
        self.spec_output = QTextEdit()
        self.spec_output.setReadOnly(True)
        self.spec_output.setFixedHeight(80)
        self.model_name_output = QLineEdit()
        self.model_name_output.setReadOnly(True)
        self.manufacturer_output = QLineEdit()
        self.manufacturer_output.setReadOnly(True)
        self.origin_output = QLineEdit()
        self.origin_output.setReadOnly(True)
        self.g2b_output = QLineEdit()
        self.g2b_output.setReadOnly(True)
        self.kats_cert_output = QLineEdit()
        self.kats_cert_output.setReadOnly(True)
        self.kc_cert_output = QLineEdit()
        self.kc_cert_output.setReadOnly(True)

        # 가격 정보(List<PriceInfo>)를 표시할 테이블 위젯 추가
        self.price_table = QTableWidget()
        self.price_table.setColumnCount(3)
        self.price_table.setHorizontalHeaderLabels(["판매처 (storeName)", "가격 (price)", "링크 (storeLink)"])
        self.price_table.setEditTriggers(QTableWidget.NoEditTriggers)  # 편집 불가
        self.price_table.setMinimumHeight(150)

        # --- 레이아웃 설정 ---
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

        res_layout = QGridLayout()
        res_layout.addWidget(QLabel("productName:"), 0, 0)
        res_layout.addWidget(self.product_name_output, 0, 1)
        res_layout.addWidget(QLabel("specification:"), 1, 0, Qt.AlignTop)
        res_layout.addWidget(self.spec_output, 1, 1)
        res_layout.addWidget(QLabel("modelName:"), 2, 0)
        res_layout.addWidget(self.model_name_output, 2, 1)
        res_layout.addWidget(QLabel("manufacturer:"), 3, 0)
        res_layout.addWidget(self.manufacturer_output, 3, 1)
        res_layout.addWidget(QLabel("countryOfOrigin:"), 4, 0)
        res_layout.addWidget(self.origin_output, 4, 1)
        res_layout.addWidget(QLabel("g2bClassificationNumber:"), 5, 0)
        res_layout.addWidget(self.g2b_output, 5, 1)
        res_layout.addWidget(QLabel("katsCertificationNumber:"), 6, 0)
        res_layout.addWidget(self.kats_cert_output, 6, 1)
        res_layout.addWidget(QLabel("kcCertificationNumber:"), 7, 0)
        res_layout.addWidget(self.kc_cert_output, 7, 1)
        res_layout.addWidget(QLabel("priceList:"), 8, 0, Qt.AlignTop)
        res_layout.addWidget(self.price_table, 8, 1)
        response_group.setLayout(res_layout)

        main_layout = QVBoxLayout(self)
        main_layout.addWidget(request_group)
        main_layout.addWidget(action_group)
        main_layout.addWidget(response_group)

        self.run_button.clicked.connect(self.start_api_call)
        self.setWindowTitle("S2B 상품 정보 AI 생성기")
        self.setGeometry(300, 200, 600, 800)  # 창 크기 조정
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

        # 모든 입력 필드 초기화
        self.clear_outputs()

        # ApiWorker에 모든 DTO 필드를 전달
        self.worker = ApiWorker(model, spec_example, product_name_example)
        self.worker.task_finished.connect(self.handle_api_result)
        self.worker.start()

    def handle_api_result(self, result):
        if isinstance(result, dict):
            self.status_label.setText("상태: ✅ AI 생성 완료!")
            self.status_label.setStyleSheet("color: green;")

            # GenerateResponse DTO의 모든 필드에 대해 값 채우기
            self.product_name_output.setText(result.get('productName', ''))
            self.spec_output.setText(result.get('specification', ''))
            self.model_name_output.setText(result.get('modelName', ''))
            self.manufacturer_output.setText(result.get('manufacturer', ''))
            self.origin_output.setText(result.get('countryOfOrigin', ''))
            self.g2b_output.setText(result.get('g2bClassificationNumber', ''))
            self.kats_cert_output.setText(result.get('katsCertificationNumber', ''))
            self.kc_cert_output.setText(result.get('kcCertificationNumber', ''))

            # 가격 정보 리스트를 테이블에 채우기
            price_list = result.get('priceList', [])
            self.price_table.setRowCount(len(price_list))
            for row, item in enumerate(price_list):
                self.price_table.setItem(row, 0, QTableWidgetItem(item.get('storeName')))
                self.price_table.setItem(row, 1, QTableWidgetItem(str(item.get('price'))))
                self.price_table.setItem(row, 2, QTableWidgetItem(item.get('storeLink')))
            self.price_table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)

        else:
            self.status_label.setText(f"상태: ❌ {result}")
            self.status_label.setStyleSheet("color: red;")
            QMessageBox.critical(self, "API 오류", str(result))

        self.run_button.setEnabled(True)

    def clear_outputs(self):
        """이전 실행 결과를 지우는 헬퍼 함수"""
        self.product_name_output.clear()
        self.spec_output.clear()
        self.model_name_output.clear()
        self.manufacturer_output.clear()
        self.origin_output.clear()
        self.g2b_output.clear()
        self.kats_cert_output.clear()
        self.kc_cert_output.clear()
        self.price_table.setRowCount(0)


# --- 메인 프로그램 실행 부분 ---
if __name__ == '__main__':
    app = QApplication(sys.argv)
    ex = S2BApp()
    sys.exit(app.exec_())