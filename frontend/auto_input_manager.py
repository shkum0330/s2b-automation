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
            status_callback("🖱️ 3초 뒤 입력을 시작합니다. (멈추려면 ESC)")

        # 시작 전 대기 및 ESC 체크
        for _ in range(30):
            if keyboard.is_pressed('esc'):
                if status_callback: status_callback("🛑 시작 전 취소됨")
                return
            time.sleep(0.1)


        target_keys = [
            "productName",  # 1. 물품명
            "specification",  # 2. 규격
            "modelName",  # 3. 모델명
            "price",  # 4. 제시금액
            "manufacturer",  # 5. 제조사
            "countryOfOrigin",  # 6. 원산지
            "katsCertificationNumber",  # 7. 전기용품 인증
            "kcCertificationNumber",  # 8. 방송통신 인증
            "g2bClassificationNumber"  # 9. 물품목록번호
        ]

        if status_callback:
            status_callback("🚀 입력 시작...")

        # 마지막으로 성공한 키 (체인 연결용)
        last_successful_key = None

        for key in target_keys:
            # 1. 중단 체크
            if keyboard.is_pressed('esc'):
                if status_callback: status_callback("🛑 정지됨")
                return

            value = data_dict.get(key, "")

            # 제시금액 처리
            if key == "price" and last_successful_key == "modelName":
                if status_callback: status_callback(f"⌨️ '{key}' (Tab 이동)")

                pyautogui.press('tab')
                time.sleep(0.1)

                if value:
                    self._overwrite_text(value)

                # 값이 있든 없든 위치는 이동했으므로 체인 유지
                last_successful_key = key
                time.sleep(0.5)
                continue

            #  모델명 처리
            if key == "modelName" and last_successful_key == "specification":
                # 모델명 값이 있는 경우
                if value:
                    if status_callback: status_callback(f"⌨️ '{key}' (Tab x2 이동)")
                    pyautogui.press('tab', presses=2, interval=0.1)
                    time.sleep(0.1)
                    self._overwrite_text(value)

                #  모델명 값이 없는 경우
                else:
                    if status_callback: status_callback(f"⌨️ '{key}' 없음 (Tab x1 이동)")
                    pyautogui.press('tab')
                    time.sleep(0.1)

                # 성공 처리
                last_successful_key = key
                time.sleep(0.5)
                continue

            # 일반적인 빈 값 체크
            if not value or "가격비교" in value:
                last_successful_key = None  # 체인 끊김
                continue

            if key == "specification" and last_successful_key == "productName":
                if status_callback: status_callback(f"⌨️ '{key}' (Tab 이동)")

                pyautogui.press('tab')
                time.sleep(0.2)
                self._overwrite_text(value)

                last_successful_key = key
                time.sleep(0.5)
                continue

            # 이미지 인식
            img_path = os.path.join(self.image_dir, f"{key}.png")
            if not os.path.exists(img_path):
                # 이미지가 없으면 로그만 남기고 체인 끊음
                last_successful_key = None
                continue

            if self._find_scroll_and_type(img_path, value):
                last_successful_key = key
                time.sleep(0.5)
            else:
                if status_callback:
                    status_callback(f"❌ 실패: '{key}' (이미지 못 찾음)")
                last_successful_key = None

        if status_callback:
            status_callback("✅ 모든 작업 완료")

    def _find_scroll_and_type(self, img_path, text):
        max_attempts = 5
        scroll_amount = -400
        primary_region = (0, 0, self.primary_width, self.primary_height)

        for attempt in range(max_attempts):
            if keyboard.is_pressed('esc'): return False

            try:
                location = pyautogui.locateCenterOnScreen(
                    img_path,
                    confidence=0.7,
                    region=primary_region
                )

                if location:
                    target_x = location.x + self.click_offset_x
                    target_y = location.y + self.click_offset_y

                    pyautogui.click(target_x, target_y)
                    time.sleep(0.2)
                    self._overwrite_text(text)
                    return True
                else:
                    pyautogui.scroll(scroll_amount)
                    time.sleep(0.8)

            except Exception:
                pyautogui.scroll(scroll_amount)
                time.sleep(0.8)

        return False

    def _overwrite_text(self, text):
        if keyboard.is_pressed('esc'): return

        pyautogui.hotkey(self.ctrl_key, 'a')
        time.sleep(0.1)
        pyautogui.press('backspace')
        time.sleep(0.1)

        pyperclip.copy(text)
        pyautogui.hotkey(self.ctrl_key, 'v')