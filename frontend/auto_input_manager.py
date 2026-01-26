import sys
import time
import os
import pyautogui
import pyperclip
import keyboard
import traceback


class AutoInputManager:
    def __init__(self):
        self.is_mac = sys.platform == 'darwin'
        self.ctrl_key = 'command' if self.is_mac else 'ctrl'
        self.image_dir = os.path.join(os.path.dirname(__file__), 'images')

        # 텍스트 입력창 클릭 오프셋
        self.click_offset_x = 180
        self.click_offset_y = -30

        self.primary_width, self.primary_height = pyautogui.size()
        pyautogui.PAUSE = 0.01

    def start_input(self, data_dict, status_callback=None):
        if status_callback:
            status_callback("🖱️ 3초 뒤 입력을 시작합니다. (멈추려면 ESC)")

        for _ in range(30):
            if keyboard.is_pressed('esc'):
                if status_callback: status_callback("🛑 취소됨")
                return
            time.sleep(0.1)

        center_x = self.primary_width // 2
        safe_x = center_x + 1100
        safe_y = self.primary_height // 2

        if safe_x >= self.primary_width:
            safe_x = self.primary_width - 100

        pyautogui.click(safe_x, safe_y)
        time.sleep(0.1)
        pyautogui.press('home')
        time.sleep(0.5)

        # 입력 순서 리스트
        target_keys = [
            "productName",
            "specification",
            "modelName",
            "price",
            "manufacturer",
            "countryOfOrigin",
            "katsCertificationNumber",
            "kcCertificationNumber"
        ]

        if status_callback: status_callback("🚀 입력 시작...")

        last_successful_key = None

        for key in target_keys:
            if keyboard.is_pressed('esc'):
                if status_callback: status_callback("🛑 정지됨")
                return

            value = data_dict.get(key, "")

            if key in ["katsCertificationNumber", "kcCertificationNumber"]:

                if key == "katsCertificationNumber":
                    step_name = "전기용품"
                    anchor_img = "header_child.png"
                else:
                    step_name = "방송통신"
                    anchor_img = "header_living.png"

                anchor_path = os.path.join(self.image_dir, anchor_img)

                if status_callback: status_callback(f"🔍 {step_name} 위치 찾는 중...")
                anchor_loc = self._scroll_and_find_header(anchor_path)

                if anchor_loc:
                    target_x = None
                    target_y = None
                    is_regist = False

                    # 좌표 계산
                    if value and value.strip():
                        is_regist = True
                        target_x = anchor_loc.x + 145
                        target_y = anchor_loc.y + 115
                    else:
                        if key == "kcCertificationNumber":
                            target_x = anchor_loc.x + 733
                            target_y = anchor_loc.y + 118
                        else:
                            target_x = anchor_loc.x + 735
                            target_y = anchor_loc.y + 120

                    # 4. 클릭 및 입력 수행
                    if target_x and target_y:
                        if status_callback: status_callback(f"⚡ {step_name} 클릭")
                        pyautogui.click(target_x, target_y)
                        time.sleep(1.0)

                        if is_regist:
                            self._perform_input_sequence(value)
                            if status_callback: status_callback(f"✅ {step_name} 등록 완료")
                        else:
                            if status_callback: status_callback(f"✅ {step_name} 대상 아님")

                        last_successful_key = key
                else:
                    if status_callback: status_callback(f"⚠️ {step_name} 헤더 없음")

                time.sleep(0.1)
                continue

            # 제조사
            if key == "manufacturer" and last_successful_key == "price":
                pyautogui.press('tab', presses=5, interval=0.01)
                if value: self._overwrite_text(value)
                last_successful_key = key
                time.sleep(0.1)
                continue

            # 금액
            if key == "price" and last_successful_key == "modelName":
                pyautogui.press('tab')
                if value: self._overwrite_text(value)
                last_successful_key = key
                time.sleep(0.1)
                continue

            # 모델명
            if key == "modelName" and last_successful_key == "specification":
                if value:
                    pyautogui.press('tab', presses=2, interval=0.01)
                    self._overwrite_text(value)
                else:
                    pyautogui.press('tab')
                last_successful_key = key
                time.sleep(0.1)
                continue

            # 규격
            if key == "specification" and last_successful_key == "productName":
                pyautogui.press('tab')
                time.sleep(0.1)
                self._overwrite_text(value)
                last_successful_key = key
                time.sleep(0.1)
                continue

            # 물품명 등 일반 이미지 인식 입력
            if not value or "가격비교" in value:
                last_successful_key = None
                continue

            img_path = f"{key}.png"
            if self._find_scroll_and_type(img_path, value):
                last_successful_key = key
                time.sleep(0.1)
            else:
                last_successful_key = None

        if status_callback:
            status_callback("✅ 완료")

    # 입력 시퀀스
    def _perform_input_sequence(self, text):
        pyautogui.press('tab', presses=2, interval=0.2)
        self._overwrite_text(text)
        pyautogui.press('tab')
        time.sleep(0.2)
        pyautogui.press('enter')

    # 스마트 스크롤 헤더 탐색
    def _scroll_and_find_header(self, img_path):
        if not os.path.exists(img_path):
            return None

        for i in range(4):
            loc = self._locate_center(img_path, grayscale=False, confidence=0.7)
            if loc: return loc
            loc = self._locate_center(img_path, grayscale=True, confidence=0.7)
            if loc: return loc

            pyautogui.scroll(-1000)
            time.sleep(0.5)
        return None

    def _locate_center(self, img_name_or_path, region=None, confidence=0.7, grayscale=False):
        if not os.path.isabs(img_name_or_path):
            img_path = os.path.join(self.image_dir, img_name_or_path)
        else:
            img_path = img_name_or_path

        if not os.path.exists(img_path): return None

        try:
            return pyautogui.locateCenterOnScreen(
                img_path, confidence=confidence, region=region, grayscale=grayscale
            )
        except Exception:
            return None

    def _find_scroll_and_type(self, img_name, text):
        img_path = os.path.join(self.image_dir, img_name)
        if not os.path.exists(img_path): return False
        max_attempts = 5
        scroll_amount = -1000
        primary_region = (0, 0, self.primary_width, self.primary_height)
        for _ in range(max_attempts):
            if keyboard.is_pressed('esc'): return False
            loc = self._locate_center(img_path, region=primary_region, confidence=0.7, grayscale=True)
            if loc:
                target_x = loc.x + self.click_offset_x
                target_y = loc.y + self.click_offset_y
                pyautogui.click(target_x, target_y)
                time.sleep(0.1)
                self._overwrite_text(text)
                return True
            else:
                pyautogui.scroll(scroll_amount)
                time.sleep(0.3)
        return False

    def _overwrite_text(self, text):
        if keyboard.is_pressed('esc'): return
        pyautogui.hotkey(self.ctrl_key, 'a')
        time.sleep(0.1)
        pyautogui.press('backspace')
        time.sleep(0.1)
        pyperclip.copy(text)
        pyautogui.hotkey(self.ctrl_key, 'v')