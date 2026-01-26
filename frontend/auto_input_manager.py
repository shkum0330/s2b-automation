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

        # 텍스트 입력창 클릭 오프셋
        self.click_offset_x = 180
        self.click_offset_y = -30

        self.primary_width, self.primary_height = pyautogui.size()
        pyautogui.PAUSE = 0.005

    def start_input(self, data_dict, status_callback=None):
        if status_callback:
            status_callback("🖱️ 3초 뒤 입력을 시작합니다. (멈추려면 ESC)")

        for _ in range(30):
            if keyboard.is_pressed('esc'):
                if status_callback: status_callback("🛑 취소됨")
                return
            time.sleep(0.1)

        pyautogui.click(self.primary_width // 2, self.primary_height // 2)
        time.sleep(0.1)
        pyautogui.press('home')
        time.sleep(0.5)

        target_keys = [
            "productName",
            "specification",
            "modelName",
            "price",
            "manufacturer",
            "countryOfOrigin",
            "katsCertificationNumber"
        ]

        if status_callback: status_callback("🚀 입력 시작...")

        last_successful_key = None

        for key in target_keys:
            if keyboard.is_pressed('esc'):
                if status_callback: status_callback("🛑 정지됨")
                return

            value = data_dict.get(key, "")
            print(f"[DEBUG] 처리 중: {key}")

            if key == "katsCertificationNumber":
                step_name = "전기용품"

                header_img = "katsCertificationNumber.png"
                header_path = os.path.join(self.image_dir, header_img)

                header_loc = self._scroll_and_find_header(header_path)

                if header_loc:
                    print(f"[DEBUG] {step_name} 헤더 발견: {header_loc}")

                    region_y = max(0, int(header_loc.y - 185))
                    search_region = (0, region_y, self.primary_width, 100)

                    target_x = None
                    target_y = None
                    is_regist = False

                    if value and value.strip():
                        target_img = "kats_radio_regist.png"
                        is_regist = True

                        btn_loc = self._locate_center(target_img, region=search_region, confidence=0.6, grayscale=True)

                        if btn_loc:
                            target_x = btn_loc.x - 80
                            target_y = btn_loc.y
                        else:
                            target_x = header_loc.x + 130
                            target_y = header_loc.y - 185
                    else:
                        target_img = "kats_radio_none.png"

                        btn_loc = self._locate_center(target_img, region=search_region, confidence=0.6, grayscale=True)

                        if btn_loc:
                            target_x = btn_loc.x - 115
                            target_y = btn_loc.y
                        else:
                            target_x = header_loc.x + 270
                            target_y = header_loc.y - 185

                    if target_x and target_y:
                        if status_callback: status_callback(f"⚡ {step_name} 클릭 수행")
                        pyautogui.click(target_x, target_y)

                        if is_regist:
                            self._perform_input_sequence(value)
                            if status_callback: status_callback(f"✅ {step_name} 등록 완료")
                        else:
                            if status_callback: status_callback(f"✅ {step_name} 대상 아님 선택")

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

    # 입력 시퀀스 (Tab -> 지우기 -> 입력 -> 등록)
    def _perform_input_sequence(self, text):
        pyautogui.press('tab', presses=2, interval=0.1)

        time.sleep(0.2)

        self._overwrite_text(text)
        pyautogui.press('tab')
        time.sleep(0.1)
        pyautogui.press('enter')

    def _scroll_and_find_header(self, img_path):
        if not os.path.exists(img_path): return None
        for i in range(4):
            loc = self._locate_center(img_path, grayscale=False, confidence=0.7)
            if loc: return loc
            loc = self._locate_center(img_path, grayscale=True, confidence=0.7)
            if loc: return loc
            pyautogui.scroll(-400)
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

        # 1. 전체 선택 (Ctrl+A)
        pyautogui.hotkey(self.ctrl_key, 'a')
        time.sleep(0.1)  # 씹힘 방지 딜레이

        # 2. 삭제 (Backspace) - 기존 값 제거
        pyautogui.press('backspace')
        time.sleep(0.1)

        # 3. 붙여넣기 (Ctrl+V)
        pyperclip.copy(text)
        pyautogui.hotkey(self.ctrl_key, 'v')