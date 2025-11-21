# main.py

import sys
from PyQt5.QtWidgets import QApplication, QMessageBox
from PyQt5.QtCore import Qt
from PyQt5 import QtWebEngineWidgets  # QtWebEngineWidgets 임포트 확인

from login_window import LoginWindow
from main_window import MainWindow
from api_worker import ApiWorker

# --- QWebEngineView 전역 초기화 ---

# 1. QApplication 생성 전에 속성 설정
QApplication.setAttribute(Qt.AA_ShareOpenGLContexts)

# 2. QApplication 생성
app = QApplication(sys.argv)

# 3. 전역 웹 엔진 설정 주입
global_settings = QtWebEngineWidgets.QWebEngineSettings.globalSettings()

# [기존 설정] 자바스크립트 활성화
global_settings.setAttribute(QtWebEngineWidgets.QWebEngineSettings.JavascriptEnabled, True)
# [기존 설정] 로컬 파일의 리모트 접근 허용
global_settings.setAttribute(QtWebEngineWidgets.QWebEngineSettings.LocalContentCanAccessRemoteUrls, True)

# ✅ [추가] 로컬 스토리지 활성화 (이것이 없으면 결제 위젯이 뜨지 않습니다)
global_settings.setAttribute(QtWebEngineWidgets.QWebEngineSettings.LocalStorageEnabled, True)

# 4. 웹 엔진 프로필 초기화
QtWebEngineWidgets.QWebEngineProfile.defaultProfile()
# -------------------------------


# (이하 MainController 및 실행 코드는 기존과 동일)
class MainController:
    def __init__(self):
        self.login_win = LoginWindow()
        self.main_win = None
        self.api_worker = None
        self.access_token = None
        self.login_win.login_success.connect(self.process_login)

    def show_login_window(self):
        self.login_win.show()

    def process_login(self, auth_code):
        url = f"http://localhost:8080/api/v1/auth/callback/kakao?code={auth_code}"
        self.api_worker = ApiWorker('GET', url)
        self.api_worker.finished.connect(self.handle_login_response)
        self.api_worker.start()

    def handle_login_response(self, response):
        if not response.get('ok'):
            error_msg = response.get('json', {}).get('message', '알 수 없는 로그인 오류')
            QMessageBox.critical(self.login_win, "로그인 실패", error_msg)
            return

        headers = response.get('headers', {})
        self.access_token = headers.get('Authorization')

        if self.access_token:
            print(f"Access Token 저장 성공: {self.access_token}")
            self.show_main_window(self.access_token)
        else:
            QMessageBox.critical(self.login_win, "로그인 실패", "Access Token을 받지 못했습니다.")

    def show_main_window(self, access_token):
        if self.main_win is None:
            self.main_win = MainWindow(access_token=access_token)
        self.login_win.close()
        self.main_win.show()


if __name__ == '__main__':
    # 앱과 설정은 이미 위에서 초기화되었습니다.
    controller = MainController()
    controller.show_login_window()
    sys.exit(app.exec_())