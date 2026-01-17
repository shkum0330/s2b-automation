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

    def start_input(self, data_dict, status_callback=None):
        if status_callback:
            status_callback("🖱️ 3초 뒤 입력을 시작합니다. (멈추려면 ESC를 누르세요)")

        # 3초 대기 중에도 ESC 체크
        for _ in range(30):
            if keyboard.is_pressed('esc'):
                if status_callback: status_callback("🛑 시작 전 취소됨")
                return
            time.sleep(0.1)

        target_keys = [
            "productName", "specification", "modelName",
            "manufacturer", "countryOfOrigin",
            "katsCertificationNumber", "kcCertificationNumber",
            "g2bClassificationNumber"
        ]

        if status_callback:
            status_callback("🚀 이미지 인식 시작...")

        for key in target_keys:
            if keyboard.is_pressed('esc'):
                if status_callback: status_callback("🛑 사용자 요청으로 정지됨")
                return

            value = data_dict.get(key, "")
            if not value or "가격비교" in value:
                continue

            img_path = os.path.join(self.image_dir, f"{key}.png")
            if not os.path.exists(img_path):
                print(f"⚠️ 이미지 없음: {img_path}")
                continue

            # 찾기 및 입력 시도 (status_callback 전달)
            if self._find_scroll_and_type(img_path, value):
                time.sleep(0.5)
            else:
                if status_callback:
                    status_callback(f"❌ 실패: '{key}' (못 찾음)")

        if status_callback:
            status_callback("✅ 모든 작업 완료")

    def _find_scroll_and_type(self, img_path, text):
        max_attempts = 5
        scroll_amount = -400
        primary_region = (0, 0, self.primary_width, self.primary_height)

        for attempt in range(max_attempts):
            if keyboard.is_pressed('esc'):
                return False

            try:
                # 인식률 설정
                location = pyautogui.locateCenterOnScreen(
                    img_path,
                    confidence=0.7,
                    region=primary_region
                )

                if location:
                    target_x = location.x + self.click_offset_x
                    target_y = location.y + self.click_offset_y

                    self._click_and_paste(target_x, target_y, text)
                    return True
                else:
                    pyautogui.scroll(scroll_amount)
                    time.sleep(0.8)

            except Exception:
                pyautogui.scroll(scroll_amount)
                time.sleep(0.8)

        return False

    def _click_and_paste(self, x, y, text):
        # 클릭 전에도 ESC 체크
        if keyboard.is_pressed('esc'): return

        pyautogui.click(x, y)
        time.sleep(0.2)

        pyautogui.hotkey(self.ctrl_key, 'a')
        time.sleep(0.1)
        pyautogui.press('backspace')
        time.sleep(0.1)

        pyperclip.copy(text)
        pyautogui.hotkey(self.ctrl_key, 'v')