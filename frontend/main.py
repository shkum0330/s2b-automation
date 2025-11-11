# main.py

import sys
from PyQt5.QtWidgets import QApplication, QMessageBox
from PyQt5.QtCore import Qt

# QWebEngineView 충돌 방지를 위한 임포트
from PyQt5 import QtWebEngineWidgets

from login_window import LoginWindow
from main_window import MainWindow
from api_worker import ApiWorker

# --- QWebEngineView 전역 초기화 ---

# 1. QApplication 생성 *전에* 속성을 설정합니다.
QApplication.setAttribute(Qt.AA_ShareOpenGLContexts)

# 2. QApplication을 *먼저* 생성합니다.
app = QApplication(sys.argv)

# 3. (가장 중요) QApplication 생성 *후에* 전역 웹 엔진 설정을 주입합니다.
global_settings = QtWebEngineWidgets.QWebEngineSettings.globalSettings()

# [수정] JavaScriptEnabled -> JavascriptEnabled (소문자 s)
global_settings.setAttribute(QtWebEngineWidgets.QWebEngineSettings.JavascriptEnabled, True)

global_settings.setAttribute(QtWebEngineWidgets.QWebEngineSettings.LocalContentCanAccessRemoteUrls, True)

# 4. 웹 엔진 프로필을 초기화합니다.
QtWebEngineWidgets.QWebEngineProfile.defaultProfile()
# --- [초기화 완료] ---


# 전체 애플리케이션의 흐름을 제어하는 메인 컨트롤러
class MainController:
    # 컨트롤러 초기화
    def __init__(self):
        self.login_win = LoginWindow()
        self.main_win = None
        self.api_worker = None
        self.access_token = None
        self.login_win.login_success.connect(self.process_login)

    # 로그인 윈도우를 화면에 표시
    def show_login_window(self):
        self.login_win.show()

    # LoginWindow에서 받은 auth_code로 백엔드에 최종 로그인 요청
    def process_login(self, auth_code):
        url = f"http://localhost:8080/api/v1/auth/callback/kakao?code={auth_code}"
        self.api_worker = ApiWorker('GET', url)
        self.api_worker.finished.connect(self.handle_login_response)
        self.api_worker.start()

    # 백엔드 로그인 요청의 응답 처리
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

    # 로그인 창을 닫고 메인 윈도우를 표시
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