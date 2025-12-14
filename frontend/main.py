# frontend/main.py
import sys
import os
import traceback

# 숨겨진 에러를 콘솔에 출력
def exception_hook(exctype, value, tb):
    traceback.print_exception(exctype, value, tb) # 에러 내용 출력
    sys.exit(1)

# 위에서 정의한 함수를 시스템 예외 처리기로 등록
sys.excepthook = exception_hook

from PyQt5.QtWidgets import QApplication, QMessageBox
from PyQt5 import QtWebEngineWidgets

from login_window import LoginWindow
from main_window import MainWindow
from api_worker import ApiWorker

# 설정 완료 후 앱 생성
app = QApplication(sys.argv)

# 전역 웹 엔진 설정
default_settings = QtWebEngineWidgets.QWebEngineSettings.globalSettings()
default_settings.setAttribute(QtWebEngineWidgets.QWebEngineSettings.JavascriptEnabled, True)
default_settings.setAttribute(QtWebEngineWidgets.QWebEngineSettings.LocalStorageEnabled, True)
default_settings.setAttribute(QtWebEngineWidgets.QWebEngineSettings.LocalContentCanAccessRemoteUrls, True)

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
            self.show_main_window(self.access_token)
        else:
            QMessageBox.critical(self.login_win, "로그인 실패", "Access Token을 받지 못했습니다.")

    def show_main_window(self, access_token):
        if self.main_win is None:
            self.main_win = MainWindow(access_token=access_token)
        self.login_win.close()
        self.main_win.show()

if __name__ == '__main__':
    controller = MainController()
    controller.show_login_window()
    sys.exit(app.exec_())