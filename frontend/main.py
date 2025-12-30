import sys
import requests
from PyQt5.QtWidgets import QApplication, QMessageBox

from login_window import LoginWindow
from main_window import MainWindow
from api_worker import ApiWorker
from config import BASE_URL


class MainController:
    def __init__(self):
        self.login_win = LoginWindow()
        self.main_win = None
        self.api_worker = None
        self.access_token = None

        # 쿠키 유지를 위한 세션 객체
        self.session = requests.Session()

        self.login_win.login_success.connect(self.process_login)

    def show_login_window(self):
        self.login_win.show()

    def process_login(self, auth_code):
        url = f"{BASE_URL}/api/v1/auth/callback/kakao?code={auth_code}"

        # session 객체 전달
        self.api_worker = ApiWorker('GET', url, session=self.session)
        self.api_worker.finished.connect(self.handle_login_response)
        self.api_worker.start()

    def handle_login_response(self, response):
        if not response.get('ok'):
            error_msg = response.get('json', {}).get('message', '알 수 없는 로그인 오류')
            QMessageBox.critical(self.login_win, "로그인 실패", error_msg)
            return

        headers = response.get('headers', {})
        self.access_token = headers.get('Authorization')

        print("로그인 후 저장된 쿠키:", self.session.cookies.get_dict())

        if self.access_token:
            self.show_main_window(self.access_token)
        else:
            QMessageBox.critical(self.login_win, "로그인 실패", "Access Token을 받지 못했습니다.")

    def show_main_window(self, access_token):
        if self.main_win is None:
            # session 전달 및 로그아웃 시그널 연결
            self.main_win = MainWindow(access_token=access_token, session=self.session)
            self.main_win.logout_requested.connect(self.process_logout)

        self.login_win.close()
        self.main_win.show()

    # 로그아웃 처리 메서드
    def process_logout(self):
        print("🚪 로그아웃 처리 중...")
        if self.main_win:
            self.main_win.close()
            self.main_win = None  # 메인 윈도우 초기화

        # 세션 초기화 (쿠키 삭제 효과)
        self.session = requests.Session()
        self.access_token = None

        # 로그인 윈도우 다시 열기
        self.show_login_window()


if __name__ == '__main__':
    app = QApplication(sys.argv)
    controller = MainController()
    controller.show_login_window()
    sys.exit(app.exec_())