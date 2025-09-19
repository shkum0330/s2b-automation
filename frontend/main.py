# main.py

import sys
from PyQt5.QtWidgets import QApplication, QMessageBox

# 각 창 클래스들을 import
from login_window import LoginWindow
from main_window import MainWindow
from api_worker import ApiWorker


class MainController:
    def __init__(self):
        self.login_win = LoginWindow()
        self.main_win = None
        self.api_worker = None

        # 로그인 성공 후 받은 토큰을 저장할 변수
        self.access_token = None

        self.login_win.login_success.connect(self.process_login)

    def show_login_window(self):
        self.login_win.show()

    def process_login(self, auth_code):
        """
        LoginWindow로부터 auth_code를 받아 백엔드에 최종 로그인 요청을 보냅니다.
        """
        print(f"MainController가 받은 인증 코드: {auth_code}")

        # 백엔드의 카카오 로그인 콜백 API 주소
        url = f"http://localhost:8080/api/v1/auth/callback/kakao?code={auth_code}"

        # GET 요청 시작
        self.api_worker = ApiWorker('GET', url)
        self.api_worker.finished.connect(self.handle_login_response)
        self.api_worker.start()

    def handle_login_response(self, response):
        """
        백엔드 로그인 요청의 응답을 처리합니다.
        """
        if not response.get('ok'):
            error_msg = response.get('json', {}).get('message', response.get('error', '알 수 없는 로그인 오류'))
            QMessageBox.critical(self.login_win, "로그인 실패", error_msg)
            return

        # --- 백엔드로부터 받은 토큰을 추출하고 로그로 출력 ---
        headers = response.get('headers', {})
        self.access_token = headers.get('Authorization')
        set_cookie_header = headers.get('Set-Cookie')  # Refresh Token이 담긴 쿠키

        print("\n" + "=" * 50)
        print("백엔드로부터 성공적으로 토큰을 수신했습니다.")
        print(f"  - Access Token: {self.access_token}")
        print(f"  - Refresh Token (in Cookie): {set_cookie_header}")
        print("=" * 50 + "\n")
        # ----------------------------------------------------

        if self.access_token:
            # Access Token을 성공적으로 받았다면 메인 윈도우를 보여줍니다.
            self.show_main_window()
        else:
            QMessageBox.critical(self.login_win, "로그인 실패", "백엔드로부터 Access Token을 받지 못했습니다.")

    def show_main_window(self):
        if not self.main_win:
            # MainWindow를 생성할 때 Access Token을 전달합니다.
            self.main_win = MainWindow(access_token=self.access_token)

        self.login_win.close()
        self.main_win.show()


if __name__ == '__main__':
    app = QApplication(sys.argv)
    controller = MainController()
    controller.show_login_window()
    sys.exit(app.exec_())