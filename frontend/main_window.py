# main_window.py

import pyperclip
import configparser
import os
from PyQt5.QtWidgets import (QWidget, QLabel, QLineEdit, QTextEdit,
                             QPushButton, QVBoxLayout, QGroupBox, QGridLayout,
                             QMessageBox, QHBoxLayout, QRadioButton, QFrame,
                             QComboBox)
from PyQt5.QtCore import Qt, QTimer
from PyQt5.QtGui import QFont
from api_worker import ApiWorker
from payment_window import PaymentWindow


class MainWindow(QWidget):
    def __init__(self, access_token=None):
        super().__init__()
        self.access_token = access_token
        self.payment_worker = None  # 결제용 API 워커
        self.worker = None  # 일반 API 워커 (AI 생성용)
        self.current_task_id = None
        self.polling_timer = QTimer(self)
        self.polling_timer.timeout.connect(self.check_task_status)

        # config.ini에서 키 로드
        self.toss_client_key = self.load_client_key()

        self.input_widgets = {}
        self.output_widgets = {}

        self.initUI()
        self.update_credit_display()
        self._update_ui_for_product_type()

    # --- [설정] config.ini 로드 ---
    def load_client_key(self):
        try:
            config = configparser.ConfigParser()
            config_path = os.path.join(os.path.dirname(__file__), 'config.ini')
            config.read(config_path)
            return config['keys']['toss_client_key']
        except Exception:
            return None

    # --- [UI] 초기화 ---
    def initUI(self):
        default_font = QFont("Apple SD Gothic Neo", 13)

        # 1. 상단 레이아웃 (유형 선택, 크레딧, 결제)
        top_layout = QHBoxLayout()

        # 제품 유형 라디오 버튼
        self.radio_electronic = QRadioButton("전자제품")
        self.radio_general = QRadioButton("비전자제품")
        self.radio_electronic.setChecked(True)
        self.radio_electronic.setFont(default_font)
        self.radio_general.setFont(default_font)
        self.radio_electronic.toggled.connect(self._update_ui_for_product_type)

        top_layout.addWidget(QLabel("제품 유형:", font=default_font))
        top_layout.addWidget(self.radio_electronic)
        top_layout.addWidget(self.radio_general)
        top_layout.addStretch(1)  # 빈 공간

        # 구분선 1
        line1 = QFrame()
        line1.setFrameShape(QFrame.VLine)
        line1.setFrameShadow(QFrame.Sunken)
        top_layout.addWidget(line1)

        # 크레딧 정보
        self.credit_label = QLabel("남은 횟수: -")
        self.credit_label.setFont(default_font)
        self.refresh_btn = QPushButton("새로고침")
        self.refresh_btn.setFont(default_font)
        self.refresh_btn.clicked.connect(self.update_credit_display)

        top_layout.addWidget(self.credit_label)
        top_layout.addWidget(self.refresh_btn)

        # 구분선 2
        line2 = QFrame()
        line2.setFrameShape(QFrame.VLine)
        line2.setFrameShadow(QFrame.Sunken)
        top_layout.addWidget(line2)

        # 결제 UI
        self.plan_combo = QComboBox()
        self.plan_combo.setFont(default_font)
        self.plan_combo.addItem("플랜 선택", 0)
        self.plan_combo.addItem("30일 10개 (29,900원)", 29900)
        self.plan_combo.addItem("30일 20개 (49,900원)", 49900)
        self.plan_combo.addItem("30일 50개 (100,000원)", 100000)

        self.pay_btn = QPushButton("🚀 크레딧 충전")
        self.pay_btn.setFont(default_font)
        self.pay_btn.clicked.connect(self.start_payment_request)

        top_layout.addWidget(self.plan_combo)
        top_layout.addWidget(self.pay_btn)

        # 2. 입력 그룹 (기존 코드 유지)
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

        for w in self.input_widgets.values(): w.setFont(default_font)

        req_layout.addWidget(self.input_widgets['product_name_example_label'], 0, 0)
        req_layout.addWidget(self.input_widgets['product_name_example_input'], 0, 1)
        req_layout.addWidget(self.input_widgets['product_name_label'], 0, 0)
        req_layout.addWidget(self.input_widgets['product_name_input'], 0, 1)
        req_layout.addWidget(self.input_widgets['spec_example_label'], 1, 0, Qt.AlignTop)
        req_layout.addWidget(self.input_widgets['spec_example_input'], 1, 1)
        req_layout.addWidget(self.input_widgets['model_label'], 2, 0)
        req_layout.addWidget(self.input_widgets['model_input'], 2, 1)
        request_group.setLayout(req_layout)

        # 3. 실행 그룹
        action_group = QGroupBox("2. 실행")
        action_group.setFont(default_font)
        self.run_button = QPushButton("🚀 AI로 결과 생성하기")
        self.cancel_button = QPushButton("❌ 취소")
        self.run_button.setFont(default_font)
        self.cancel_button.setFont(default_font)
        self.cancel_button.setEnabled(False)
        self.status_label = QLabel("상태: 대기 중...")
        self.status_label.setFont(default_font)

        btn_layout = QHBoxLayout()
        btn_layout.addWidget(self.run_button)
        btn_layout.addWidget(self.cancel_button)
        act_layout = QVBoxLayout()
        act_layout.addLayout(btn_layout)
        act_layout.addWidget(self.status_label)
        action_group.setLayout(act_layout)

        # 4. 결과 그룹 (기존 코드 유지)
        response_group = QGroupBox("서버로부터 받은 결과")
        response_group.setFont(default_font)
        res_layout = QGridLayout()

        output_info = [
            ("productName", "1. 물품명:"), ("specification", "2. 규격:"),
            ("modelName", "3. 모델명:"), ("manufacturer", "4. 제조사:"),
            ("countryOfOrigin", "5. 원산지:"), ("katsCertificationNumber", "6. 전기인증:"),
            ("kcCertificationNumber", "7. 전파인증:"), ("g2bClassificationNumber", "8. G2B번호:")
        ]

        for i, (key, label_text) in enumerate(output_info):
            label = QLabel(label_text, font=default_font)
            field = QLineEdit() if key != "specification" else QTextEdit()
            if isinstance(field, QTextEdit): field.setFixedHeight(80)
            field.setFont(default_font)
            field.setReadOnly(True)
            copy_btn = QPushButton("복사", font=default_font)
            copy_btn.setFixedWidth(80)
            copy_btn.clicked.connect(lambda _, w=field: self.copy_text(w))

            self.output_widgets[key] = {'field': field}

            res_layout.addWidget(label, i, 0, Qt.AlignTop if key == "specification" else Qt.AlignVCenter)
            res_layout.addWidget(field, i, 1)
            res_layout.addWidget(copy_btn, i, 2)

        response_group.setLayout(res_layout)

        # 메인 레이아웃 조합
        main_layout = QVBoxLayout(self)
        main_layout.addLayout(top_layout)
        main_layout.addWidget(request_group)
        main_layout.addWidget(action_group)
        main_layout.addWidget(response_group)

        self.run_button.clicked.connect(self.start_api_call)
        self.cancel_button.clicked.connect(self.cancel_api_call)
        self.setWindowTitle("S2B 상품 정보 AI 생성기")
        self.setGeometry(300, 300, 840, 850)

    # --- [결제] 1. 결제 요청 시작 ---
    def start_payment_request(self):
        amount = self.plan_combo.currentData()
        if amount == 0:
            QMessageBox.warning(self, "알림", "충전할 플랜을 선택해주세요.")
            return

        if not self.toss_client_key:
            QMessageBox.critical(self, "설정 오류", "config.ini에 Toss 클라이언트 키가 없습니다.")
            return

        self.pay_btn.setEnabled(False)
        self.pay_btn.setText("주문 생성중...")

        # 주문 생성 API 호출
        url = 'http://localhost:8080/api/v1/payments/request'
        payload = {
            "amount": amount,
            "orderName": self.plan_combo.currentText().split('(')[0].strip()
        }
        headers = {"Content-Type": "application/json", "Authorization": self.access_token}

        self.payment_worker = ApiWorker('POST', url, payload=payload, headers=headers)
        self.payment_worker.finished.connect(self.handle_payment_response)
        self.payment_worker.start()

    # --- [결제] 2. 주문 생성 완료 -> 팝업 열기 ---
    def handle_payment_response(self, result):
        self.pay_btn.setEnabled(True)
        self.pay_btn.setText("🚀 크레딧 충전")

        if not result.get('ok'):
            err = result.get('json', {}).get('message', '주문 생성 실패')
            QMessageBox.warning(self, "오류", str(err))
            return

        data = result.get('json', {})
        order_id = data.get('orderId')
        amount = data.get('amount')
        order_name = data.get('orderName', '크레딧 충전')

        if not order_id:
            QMessageBox.critical(self, "오류", "주문 ID를 받지 못했습니다.")
            return

        # 팝업 열기 (부모 = None 설정으로 충돌 방지)
        dialog = PaymentWindow(
            self.toss_client_key,
            order_id,
            order_name,
            amount,
            parent=None
        )
        # 윈도우가 항상 위에 뜨도록 설정
        dialog.setWindowFlag(Qt.WindowStaysOnTopHint)

        dialog.payment_success.connect(self.handle_payment_success)
        dialog.exec_()

    # --- [결제] 3. 결제 성공 후 처리 ---
    def handle_payment_success(self):
        QMessageBox.information(self, "성공", "결제가 완료되었습니다! 크레딧을 갱신합니다.")
        self.update_credit_display()

    # --- [기타] 유틸리티 메서드 ---
    def copy_text(self, widget):
        text = widget.toPlainText() if isinstance(widget, QTextEdit) else widget.text()
        if text: pyperclip.copy(text)

    def _update_ui_for_product_type(self):
        is_elec = self.radio_electronic.isChecked()
        # 입력 필드 보이기/숨기기
        self.input_widgets['product_name_example_label'].setVisible(is_elec)
        self.input_widgets['product_name_example_input'].setVisible(is_elec)
        self.input_widgets['model_label'].setVisible(is_elec)
        self.input_widgets['model_input'].setVisible(is_elec)
        self.input_widgets['product_name_label'].setVisible(not is_elec)
        self.input_widgets['product_name_input'].setVisible(not is_elec)

        # 제조사/원산지 라벨 변경
        self.output_widgets['manufacturer']['field'].parent().findChild(QLabel).setText(
            "4. 제조사:" if is_elec else "3. 제조사:")

    def update_credit_display(self):
        self.credit_label.setText("갱신 중...")
        url = "http://localhost:8080/api/v1/members/me"
        headers = {"Authorization": self.access_token}
        self.credit_worker = ApiWorker('GET', url, headers=headers)
        self.credit_worker.finished.connect(self._handle_credit_res)
        self.credit_worker.start()

    def _handle_credit_res(self, res):
        if res.get('ok'):
            data = res.get('json', {})
            role = data.get('role', 'FREE')
            used = data.get('dailyRequestCount', 0)
            limit_map = {'FREE_USER': 5, 'PLAN_30K': 10, 'PLAN_50K': 20, 'PLAN_100K': 50}
            limit = limit_map.get(role, 5)
            self.credit_label.setText(f"오늘 남은 횟수: {limit - used} / {limit}")
        else:
            self.credit_label.setText("조회 실패")

    # API 호출 로직 (start_api_call 등)은 기존 로직 유지
    def start_api_call(self):
        is_elec = self.radio_electronic.isChecked()
        # ... (이전 코드와 동일하게 페이로드 구성) ...
        # 여기서는 지면 관계상 생략하나, 기존 로직 그대로 두시면 됩니다.
        pass

    def cancel_api_call(self):
        # ... (기존 로직) ...
        pass

    def check_task_status(self):
        # ... (기존 로직) ...
        pass

    def _handle_generation_start_response(self, res):
        pass