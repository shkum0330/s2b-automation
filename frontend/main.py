import sys
from PyQt5.QtWidgets import QApplication, QMessageBox

from login_window import LoginWindow
from main_window import MainWindow
from api_worker import ApiWorker
from config import BASE_URL

class MainController:
    # 컨트롤러 초기화
    def __init__(self):
        self.login_win = LoginWindow()
        self.main_win = None  # main_win을 None으로 초기화
        self.api_worker = None
        self.access_token = None

        self.login_win.login_success.connect(self.process_login)

    # 로그인 윈도우를 화면에 표시
    def show_login_window(self):
        self.login_win.show()

    # LoginWindow에서 받은 auth_code로 백엔드에 로그인 요청
    def process_login(self, auth_code):
        url = f"{BASE_URL}/api/v1/auth/callback/kakao?code={auth_code}"
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

            # 토큰을 성공적으로 받은 후에 MainWindow를 생성하고 표시
            self.show_main_window(self.access_token)
        else:
            QMessageBox.critical(self.login_win, "로그인 실패", "Access Token을 받지 못했습니다.")

    # 로그인 창을 닫고 메인 윈도우를 표시
    def show_main_window(self, access_token):
        # MainWindow를 새로 생성하며 access token 전달
        if self.main_win is None:
            self.main_win = MainWindow(access_token=access_token)

        self.login_win.close()
        self.main_win.show()

if __name__ == '__main__':
    app = QApplication(sys.argv)
    controller = MainController()
    controller.show_login_window()
    sys.exit(app.exec_())