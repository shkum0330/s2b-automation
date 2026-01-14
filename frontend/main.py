import sys
import os
import pickle  # 쿠키 저장을 위한 모듈
import requests
from PyQt5.QtWidgets import QApplication, QMessageBox

from login_window import LoginWindow
from main_window import MainWindow
from api_worker import ApiWorker
from config import BASE_URL

# 쿠키 저장 파일명
COOKIE_FILE = "cookies.pkl"


class MainController:
    def __init__(self):
        self.login_win = None
        self.main_win = None
        self.api_worker = None
        self.access_token = None

        # 쿠키 유지를 위한 세션 객체 생성
        self.session = requests.Session()

    def start(self):
        """애플리케이션 진입점: 자동 로그인 시도 후 실패 시 로그인 창 표시"""
        self.load_cookies()  # 1. 저장된 쿠키 불러오기

        if self.try_auto_login():  # 2. 불러온 쿠키로 토큰 갱신 시도
            print("🚀 자동 로그인 성공!")
        else:
            print("🔑 자동 로그인 실패 (로그인 필요)")
            self.show_login_window()

    def try_auto_login(self):
        """저장된 쿠키(Refresh Token)를 사용하여 액세스 토큰 갱신 시도"""
        # 쿠키가 없으면 자동 로그인 시도 불가
        if not self.session.cookies:
            return False

        try:
            url = f"{BASE_URL}/api/v1/auth/token"
            print(f"🔄 자동 로그인 시도 중... (URL: {url})")

            # session에 쿠키가 들어있으므로 자동으로 헤더에 포함되어 전송됨
            response = self.session.post(url, timeout=5)

            print(f"   -> 응답 코드: {response.status_code}")

            if response.status_code == 200:
                new_token = response.headers.get("Authorization")
                if new_token:
                    self.access_token = new_token
                    self.show_main_window(self.access_token)
                    self.save_cookies()  # 갱신된 정보가 있을 수 있으므로 저장
                    return True

            # [수정] 인증 실패(401) 또는 권한 없음(403)인 경우에만 저장된 쿠키 삭제
            elif response.status_code in [401, 403]:
                print(f"   -> 자동 로그인 실패 (상태 코드: {response.status_code}) - 인증 만료/실패")
                print("   -> 🗑️ 유효하지 않은 쿠키 파일을 삭제하고 재로그인을 유도합니다.")

                if os.path.exists(COOKIE_FILE):
                    try:
                        os.remove(COOKIE_FILE)
                    except Exception as e:
                        print(f"   -> 쿠키 파일 삭제 중 오류: {e}")

                self.session.cookies.clear()  # 메모리에서도 삭제


            else:
                print(f"   -> 서버 응답 오류 또는 점검 중 (상태 코드: {response.status_code})")
                print("   -> ⚠️ 쿠키 파일을 유지합니다.")

        except Exception as e:
            print(f"⚠️ 자동 로그인 중 오류 발생: {e}")
            # 네트워크 오류 등 예외 발생 시에는 파일을 삭제하지 않고 유지함

        return False

    def show_login_window(self):
        if self.login_win is None:
            self.login_win = LoginWindow()
            self.login_win.login_success.connect(self.process_login)
        self.login_win.show()

    def process_login(self, auth_code):
        url = f"{BASE_URL}/api/v1/auth/callback/kakao?code={auth_code}"

        # 로그인 요청 시에도 session을 사용하여 쿠키를 받아옴
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

        print("로그인 성공! 쿠키 상태:", self.session.cookies.get_dict())

        # [중요] 로그인 성공 시 쿠키 파일 저장
        self.save_cookies()

        if self.access_token:
            self.show_main_window(self.access_token)
        else:
            QMessageBox.critical(self.login_win, "로그인 실패", "Access Token을 받지 못했습니다.")

    def show_main_window(self, access_token):
        if self.main_win is None:
            # Main Window에 세션 전달
            self.main_win = MainWindow(access_token=access_token, session=self.session)
            # 로그아웃 및 토큰 갱신 시그널 연결
            self.main_win.logout_requested.connect(self.process_logout)
            self.main_win.token_refreshed_signal.connect(self.save_cookies)

        if self.login_win:
            self.login_win.close()
        self.main_win.show()

    def process_logout(self):
        print("🚪 로그아웃 처리 중...")

        # 1. 메인 윈도우 닫기
        if self.main_win:
            self.main_win.close()
            self.main_win = None

        # 2. 세션 초기화
        self.session = requests.Session()
        self.access_token = None

        # 3. 로컬 쿠키 파일 삭제
        if os.path.exists(COOKIE_FILE):
            try:
                os.remove(COOKIE_FILE)
                print("🗑️ 쿠키 파일 삭제 완료")
            except Exception as e:
                print(f"쿠키 파일 삭제 실패: {e}")

        # 4. 로그인 윈도우 다시 열기
        self.show_login_window()

    def save_cookies(self):
        """현재 세션의 쿠키를 파일로 저장 (직렬화)"""
        try:
            with open(COOKIE_FILE, 'wb') as f:
                pickle.dump(self.session.cookies, f)
            print("💾 쿠키 저장 완료")
        except Exception as e:
            print(f"❌ 쿠키 저장 실패: {e}")

    def load_cookies(self):
        """파일에서 쿠키 불러오기 및 도메인 보정"""
        if not os.path.exists(COOKIE_FILE):
            return

        try:
            with open(COOKIE_FILE, 'rb') as f:
                loaded_cookies = pickle.load(f)

                # [핵심 수정] 쿠키 도메인(localhost.local 등)이 현재 요청(localhost)과 다르면
                # requests가 쿠키를 전송하지 않으므로, 도메인을 강제로 비워줍니다.
                for cookie in loaded_cookies:
                    cookie.domain = ""

                self.session.cookies.update(loaded_cookies)

            print(f"📂 쿠키 로드 완료 (개수: {len(self.session.cookies)})")
        except Exception as e:
            print(f"❌ 쿠키 로드 실패: {e}")
            # 로드 실패 시 깨진 파일일 수 있으므로 삭제 시도
            if os.path.exists(COOKIE_FILE):
                os.remove(COOKIE_FILE)


if __name__ == '__main__':
    app = QApplication(sys.argv)
    controller = MainController()
    controller.start()
    sys.exit(app.exec_())