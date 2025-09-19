# main_window.py

import pyperclip
from PyQt5.QtWidgets import (QWidget, QLabel, QLineEdit, QTextEdit,
                             QPushButton, QVBoxLayout, QGroupBox, QGridLayout,
                             QMessageBox, QHBoxLayout)
from PyQt5.QtCore import Qt, QTimer
from api_worker import ApiWorker


# 로그인 후의 메인 UI를 담당하는 윈도우
class MainWindow(QWidget):
    # 메인 윈도우 초기화, MainController로부터 Access Token 전달 받음
    def __init__(self, access_token=None):
        """
        Initialize the main window.
        
        Sets up initial state (access token, worker and task tracking), creates and connects a QTimer for polling task status, prepares containers for output widgets and copy buttons, and builds the UI by calling initUI.
        
        Parameters:
            access_token (str | None): Optional bearer token used to authorize API requests. If None, requests will be unauthenticated.
        """
        super().__init__()
        self.access_token = access_token
        self.worker = None
        self.current_task_id = None
        self.polling_timer = QTimer(self)
        self.polling_timer.timeout.connect(self.check_task_status)
        self.output_fields = {}
        self.copy_buttons = {}
        self.initUI()

    # 메인 윈도우의 모든 UI 요소 설정
    def initUI(self):
        """
        Initialize and lay out the main window's user interface.
        
        Creates three grouped sections:
        - Request group: input widgets for product name example (QLineEdit), specification example (QTextEdit, fixed height 80), and model (QLineEdit).
        - Action group: "Run" and "Cancel" buttons and a status QLabel. The cancel button is disabled by default.
        - Response group: seven read-only output widgets (QLineEdit or QTextEdit) for API results and a per-field "복사" (copy) QPushButton that copies the corresponding field to the clipboard.
        
        Side effects / attributes set on self:
        - product_name_example_input (QLineEdit)
        - spec_example_input (QTextEdit)
        - model_input (QLineEdit)
        - run_button (QPushButton)
        - cancel_button (QPushButton)
        - status_label (QLabel)
        - output_fields (dict): maps the following result keys to their widgets:
            "productName" -> QLineEdit
            "specification" -> QTextEdit (height 80)
            "modelName" -> QLineEdit
            "manufacturer" -> QLineEdit
            "katsCertificationNumber" -> QLineEdit
            "kcCertificationNumber" -> QLineEdit
            "g2bClassificationNumber" -> QLineEdit
        
        Also connects:
        - run_button.clicked -> self.start_api_call
        - cancel_button.clicked -> self.cancel_api_call
        
        Finally sets the window title to "S2B 상품 정보 AI 생성기" and the initial geometry (x=300, y=300, width=700, height=800).
        """
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
        main_layout.addWidget(request_group)
        main_layout.addWidget(action_group)
        main_layout.addWidget(response_group)

        self.run_button.clicked.connect(self.start_api_call)
        self.cancel_button.clicked.connect(self.cancel_api_call)
        self.setWindowTitle("S2B 상품 정보 AI 생성기")
        self.setGeometry(300, 300, 700, 800)

    # 'AI로 결과 생성하기' 버튼 클릭 시 호출
    def start_api_call(self):
        """
        Start an async generation request to the backend API and update the UI for polling.
        
        Validates that the model and specification example inputs are provided (shows a warning dialog and returns if not), disables the Run button and enables Cancel, clears previous outputs, and updates the status label. Creates and starts an ApiWorker to POST a JSON payload (model, specExample, productNameExample) to /api/v1/generation/generate-spec with an Authorization header taken from self.access_token. The worker's finished signal is connected to handle_task_start_response to continue processing the started task.
        """
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

    # '/generate-spec' API의 초기 응답 처리
    def handle_task_start_response(self, result):
        """
        Handle the initial response from the generation-start API and either begin polling, process an immediate result, or surface an error.
        
        This method expects `result` to be an envelope dict with keys:
        - 'ok' (bool): whether the HTTP request succeeded,
        - 'json' (dict): parsed JSON body when present,
        - optionally 'error' (str) for transport-level errors.
        
        Behavior:
        - If `ok` is False, extract an error message from result['json']['message'] or result['error'] and call handle_error.
        - If the JSON body contains 'taskId', store it in `self.current_task_id`, update the status label, and start the polling timer (3s interval).
        - If the JSON body contains 'productName' or has status 'COMPLETED', pass the result payload (either json['result'] or the json body itself) to handle_api_result for immediate processing.
        - Otherwise, call handle_error with any 'error' or 'message' field from the JSON or a generic unknown-response message.
        
        Side effects: may update `self.current_task_id`, `self.status_label`, start the polling timer, and call handle_api_result or handle_error.
        """
        if not result.get('ok'):
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

    # 3초마다 AI 작업의 현재 상태를 서버에 확인(폴링)
    def check_task_status(self):
        """
        Poll the server for the current task's status and start an asynchronous worker to handle the response.
        
        If no task is active (self.current_task_id is falsy) this is a no-op. Otherwise this creates an ApiWorker to send a GET request to /api/v1/generation/result/{task_id} with the instance's access token in the Authorization header, connects the worker's finished signal to self.handle_polling_response, stores the worker on self.worker, and starts it. The worker uses a 5-second timeout.
        """
        if not self.current_task_id:
            return

        url = f"http://localhost:8080/api/v1/generation/result/{self.current_task_id}"
        headers = {"Authorization": self.access_token}
        self.worker = ApiWorker('GET', url, headers=headers, timeout=5)
        self.worker.finished.connect(self.handle_polling_response)
        self.worker.start()

    # 폴링 요청의 응답 처리
    def handle_polling_response(self, result):
        """
        Handle a polling HTTP worker response for a generation task and update UI/state accordingly.
        
        Expects `result` to be an envelope dict produced by the ApiWorker: at minimum it may contain
        - 'ok' (bool): whether the request succeeded,
        - 'json' (dict): parsed JSON body when available,
        - 'error' (str): error message when not ok.
        
        Behavior:
        - If `ok` is False, stops the polling timer and reports the error message (from json.message or error) via handle_error.
        - If `ok` is True, inspects `json['status']`:
          - "COMPLETED": stops polling and passes `json['result']` to handle_api_result.
          - "FAILED", "CANCELLED", "NOT_FOUND": stops polling and reports a failure/cancellation error.
          - any other status: leaves polling active and updates the status_label to indicate work in progress.
        
        Side effects: may stop the polling timer, call handle_api_result or handle_error, and update status_label.
        """
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

    # '취소' 버튼 클릭 시 호출
    def cancel_api_call(self):
        """
        Request cancellation of the currently-running generation task.
        
        Stops the polling timer, updates the status label, and issues a POST to
        /api/v1/generation/cancel/{task_id} using an ApiWorker. If no task is active
        (self.current_task_id is falsy) the method returns immediately. The worker's
        finished signal is connected to handle_cancel_response which handles the result.
        """
        if not self.current_task_id:
            return

        self.polling_timer.stop()
        self.status_label.setText("상태: ❌ 작업 취소 요청 중...")
        url = f"http://localhost:8080/api/v1/generation/cancel/{self.current_task_id}"
        headers = {"Authorization": self.access_token}
        self.worker = ApiWorker('POST', url, headers=headers, timeout=10)
        self.worker.finished.connect(self.handle_cancel_response)
        self.worker.start()

    # 취소 요청의 응답 처리
    def handle_cancel_response(self, result):
        """
        Handle the response from a cancel API request and update UI state accordingly.
        
        Checks the worker result for success (expects result to be a dict with 'ok' and a nested 'json' containing a boolean 'success'). If cancellation succeeded, updates the status label to indicate success; otherwise shows a failure message. Always re-enables the run button, disables the cancel button, and clears the stored current task id.
        
        Parameters:
            result (dict): Worker response envelope with keys like 'ok' (bool) and 'json' (dict).
        """
        if result.get('ok') and result.get('json', {}).get("success"):
            self.status_label.setText("상태: ❌ 작업이 성공적으로 취소되었습니다.")
        else:
            self.status_label.setText("상태: ❌ 작업 취소에 실패했습니다.")
        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)
        self.current_task_id = None

    # 최종 API 결과를 UI 결과창에 채워 넣음
    def handle_api_result(self, result):
        """
        Handle a successful API generation result and update the UI accordingly.
        
        Updates the status label to indicate completion, writes values from `result` into the window's output fields (missing keys are treated as empty strings), re-enables the run button, disables the cancel button, and clears the current task id.
        
        Parameters:
            result (Mapping): Mapping from output field names to their generated values (e.g., {'productName': '...', 'specification': '...'}). Values are converted to strings before being written to widgets.
        
        Returns:
            None
        """
        self.status_label.setText("상태: ✅ AI 생성 완료!")
        for field_name, output_widget in self.output_fields.items():
            self.set_widget_text(output_widget, str(result.get(field_name, '')))
        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)
        self.current_task_id = None

    # API 요청 중 발생한 모든 에러 처리
    def handle_error(self, error_message):
        """
        Handle an error from an API operation by updating the UI, showing an error dialog, and resetting internal state.
        
        Displays a critical message box with the provided error message, sets the status label to indicate an error, re-enables the run button, disables the cancel button, and clears the current task id.
        
        Parameters:
            error_message: The error text or exception to display to the user.
        """
        self.status_label.setText(f"상태: ❌ 오류 발생")
        QMessageBox.critical(self, "오류", str(error_message))
        self.run_button.setEnabled(True)
        self.cancel_button.setEnabled(False)
        self.current_task_id = None

    # 새로운 요청 전 기존 결과창의 내용을 모두 지움
    def clear_outputs(self):
        """
        Clear all response output fields in the UI.
        
        Sets the text of every widget in self.output_fields to an empty string so the previous generation results are removed. No return value.
        """
        for output_widget in self.output_fields.values():
            self.set_widget_text(output_widget, "")

    # 위젯 종류에 따라 텍스트를 설정
    def set_widget_text(self, widget, text):
        """
        Set the given text into a Qt text widget.
        
        This accepts either a QLineEdit or QTextEdit and sets its contents to the provided string.
        If the widget is not one of these types, the function performs no action.
        
        Parameters:
            widget: QLineEdit or QTextEdit — the target widget to update.
            text (str): The text to place into the widget.
        """
        if isinstance(widget, QLineEdit):
            widget.setText(text)
        elif isinstance(widget, QTextEdit):
            widget.setText(text)

    # '복사' 버튼 클릭 시 해당 라인 텍스트를 클립보드에 복사
    def copy_to_clipboard(self, text_widget):
        """
        Copy the visible text from a Qt text widget to the system clipboard.
        
        If text_widget is a QLineEdit this reads text() ; otherwise it reads toPlainText() (e.g., QTextEdit).
        If the text is non-empty, it is copied to the system clipboard via pyperclip; if empty, no action is taken.
        """
        text = text_widget.text() if isinstance(text_widget, QLineEdit) else text_widget.toPlainText()
        if text:
            pyperclip.copy(text)