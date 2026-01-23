import sys
import time
import os
import pyautogui
import pyperclip
import keyboard

class AutoInputManager:
    def __init__(self):
        self.is_mac = sys.platform == 'darwin'
        self.ctrl_key = 'command' if self.is_mac else 'ctrl'

        self.image_dir = os.path.join(os.path.dirname(__file__), 'images')

        # 클릭 위치 오프셋
        self.click_offset_x = 180
        self.click_offset_y = -30

        # 주 모니터 해상도
        self.primary_width, self.primary_height = pyautogui.size()

        # 기본 딜레이
        pyautogui.PAUSE = 0.005

    def start_input(self, data_dict, status_callback=None):
        if status_callback:
            status_callback("🖱️ 3초 뒤 입력을 시작합니다. (멈추려면 ESC)")

        # 시작 전 대기
        for _ in range(30):
            if keyboard.is_pressed('esc'):
                if status_callback: status_callback("🛑 시작 전 취소됨")
                return
            time.sleep(0.1)

        # 브라우저 활성화
        pyautogui.click(self.primary_width // 2, self.primary_height // 2)
        time.sleep(0.1)

        # 최상단 이동
        pyautogui.press('home')
        time.sleep(0.3)

        target_keys = [
            "productName",
            "specification",
            "modelName",
            "price",
            "manufacturer",
            "countryOfOrigin",
            "katsCertificationNumber",
            "kcCertificationNumber",
            "g2bClassificationNumber"
        ]

        if status_callback:
            status_callback("🚀 초고속 입력 시작...")

        last_successful_key = None

        for key in target_keys:
            if keyboard.is_pressed('esc'):
                if status_callback: status_callback("🛑 정지됨")
                return

            value = data_dict.get(key, "")

            # 전기용품 인증정보
            if key == "katsCertificationNumber":
                if value and value.strip():
                    # 인증번호 있음
                    target_img = "kats_radio_regist.png"
                    img_path = os.path.join(self.image_dir, target_img)

                    if status_callback: status_callback(f"⚡ '인증번호등록' 처리")

                    if self._locate_and_click(img_path, dx=-80):
                        # Tab x2 (입력창으로 이동)
                        pyautogui.press('tab', presses=2, interval=0.01)

                        self._overwrite_text(value)

                        pyautogui.press('tab')
                        time.sleep(0.1)
                        pyautogui.press('enter')

                        if status_callback: status_callback(f"⚡ 등록 완료")
                        last_successful_key = key
                    else:
                        if status_callback: status_callback(f"❌ 실패: '{target_img}'")
                        last_successful_key = None

                else:
                    # 인증번호 없음
                    target_img = "kats_radio_none.png"
                    img_path = os.path.join(self.image_dir, target_img)

                    if status_callback: status_callback(f"⚡ '대상 아님' 처리")

                    if self._locate_and_click(img_path, dx=-100):
                        last_successful_key = key
                    else:
                        if status_callback: status_callback(f"❌ 실패: '{target_img}'")
                        last_successful_key = None

                time.sleep(0.1)
                continue

            # 제조사
            if key == "manufacturer" and last_successful_key == "price":
                if status_callback: status_callback(f"⌨️ '{key}'")
                pyautogui.press('tab', presses=5, interval=0.01)
                if value: self._overwrite_text(value)
                last_successful_key = key
                time.sleep(0.1)
                continue

            # 제시금액
            if key == "price" and last_successful_key == "modelName":
                if status_callback: status_callback(f"⌨️ '{key}'")
                pyautogui.press('tab')
                if value: self._overwrite_text(value)
                last_successful_key = key
                time.sleep(0.1)
                continue

            # 모델명
            if key == "modelName" and last_successful_key == "specification":
                if value:
                    if status_callback: status_callback(f"⌨️ '{key}'")
                    pyautogui.press('tab', presses=2, interval=0.01)
                    self._overwrite_text(value)
                else:
                    if status_callback: status_callback(f"⌨️ '{key}' (없음)")
                    pyautogui.press('tab')
                last_successful_key = key
                time.sleep(0.1)
                continue

            # 규격
            if key == "specification" and last_successful_key == "productName":
                if status_callback: status_callback(f"⌨️ '{key}'")
                pyautogui.press('tab')
                time.sleep(0.1)
                self._overwrite_text(value)
                last_successful_key = key
                time.sleep(0.1)
                continue

            if not value or "가격비교" in value:
                last_successful_key = None
                continue

            img_path = os.path.join(self.image_dir, f"{key}.png")
            if not os.path.exists(img_path):
                last_successful_key = None
                continue

            if self._find_scroll_and_type(img_path, value):
                last_successful_key = key
                time.sleep(0.1)
            else:
                if status_callback: status_callback(f"❌ 실패: '{key}'")
                last_successful_key = None

        if status_callback:
            status_callback("✅ 완료")

    def _locate_and_click(self, img_path, dx=0, dy=0):
        max_attempts = 5
        scroll_amount = -1000
        primary_region = (0, 0, self.primary_width, self.primary_height)

        for attempt in range(max_attempts):
            if keyboard.is_pressed('esc'): return False
            try:
                location = pyautogui.locateCenterOnScreen(
                    img_path, confidence=0.7, region=primary_region
                )
                if location:
                    pyautogui.click(location.x + dx, location.y + dy)
                    return True
                else:
                    pyautogui.scroll(scroll_amount)
                    time.sleep(0.3)
            except Exception:
                pyautogui.scroll(scroll_amount)
                time.sleep(0.3)
        return False

    def _find_scroll_and_type(self, img_path, text):
        max_attempts = 5
        scroll_amount = -1000
        primary_region = (0, 0, self.primary_width, self.primary_height)

        for attempt in range(max_attempts):
            if keyboard.is_pressed('esc'): return False
            try:
                location = pyautogui.locateCenterOnScreen(
                    img_path, confidence=0.7, region=primary_region
                )
                if location:
                    target_x = location.x + self.click_offset_x
                    target_y = location.y + self.click_offset_y
                    pyautogui.click(target_x, target_y)
                    time.sleep(0.1)
                    self._overwrite_text(text)
                    return True
                else:
                    pyautogui.scroll(scroll_amount)
                    time.sleep(0.3)
            except Exception:
                pyautogui.scroll(scroll_amount)
                time.sleep(0.3)
        return False

    def _overwrite_text(self, text):
        if keyboard.is_pressed('esc'): return
        pyautogui.hotkey(self.ctrl_key, 'a')
        pyautogui.press('backspace')
        pyperclip.copy(text)
        pyautogui.hotkey(self.ctrl_key, 'v')