import sys
import time
import os
import pyautogui
import pyperclip


class AutoInputManager:
    def __init__(self):
        self.is_mac = sys.platform == 'darwin'
        self.ctrl_key = 'command' if self.is_mac else 'ctrl'

        # 이미지 파일이 저장된 폴더 경로
        self.image_dir = os.path.join(os.path.dirname(__file__), 'images')

        # 라벨 이미지 발견 시, 클릭할 입력칸의 위치 오프셋 (오른쪽으로 x 픽셀 이동)
        # 웹사이트 디자인에 따라 조절이 필요할 수 있음
        self.click_offset_x = 120
        self.click_offset_y = 0

    def start_input(self, data_dict, status_callback=None):
        """
        데이터 딕셔너리를 받아 순차적으로 이미지 서치 -> 입력 수행
        """
        if status_callback:
            status_callback("🖱️ 3초 뒤 이미지 인식을 시작합니다.")

        time.sleep(3)

        # 입력 순서
        target_keys = [
            "productName",  # 1. 물품명
            "specification",  # 2. 규격
            "modelName",  # 3. 모델명
            "manufacturer",  # 4. 제조사
            "countryOfOrigin",  # 5. 원산지
            # todo: 나머지 필드 추가
        ]

        # 작업 시작 전 스크롤을 맨 위로 올림 (선택 사항)
        pyautogui.press('home')
        time.sleep(1)

        for key in target_keys:
            value = data_dict.get(key, "")
            if not value: continue  # 값 없으면 패스

            if "가격비교" in value:  # 안전 장치
                if status_callback: status_callback(f"⚠️ '{key}' 건너뜀 (가격비교 문구 포함)")
                continue

            # 이미지 파일 경로 확인
            img_path = os.path.join(self.image_dir, f"{key}.png")
            if not os.path.exists(img_path):
                print(f"이미지 파일 없음: {img_path}")
                continue

            if status_callback:
                status_callback(f"🔍 '{key}' 위치 찾는 중...")

            # 이미지 서치 및 입력 시도
            if self._find_scroll_and_type(img_path, value):
                time.sleep(0.5)  # 다음 항목 진행 전 대기
            else:
                if status_callback:
                    status_callback(f"❌ 실패: '{key}' 입력창을 찾을 수 없습니다.")

        if status_callback:
            status_callback("✅ 모든 작업 완료")

    def _find_scroll_and_type(self, img_path, text):
        """
        이미지를 찾고, 없으면 스크롤하며 찾음. 찾으면 클릭 후 입력.
        성공 시 True, 실패 시 False 반환
        """
        max_attempts = 5  # 스크롤 시도 횟수
        scroll_amount = -400  # 한 번에 내릴 스크롤 양 (음수가 아래로)

        for attempt in range(max_attempts):
            try:
                # 1. 화면에서 이미지 찾기 (confidence: 정확도 0.8~0.9 추천)
                # grayscale=True로 하면 색상 무시하고 모양만 봐서 더 빠르고 정확함
                location = pyautogui.locateCenterOnScreen(img_path, confidence=0.9, grayscale=True)

                if location:
                    # 2. 찾으면? -> 입력칸 클릭 (오프셋 적용)
                    target_x = location.x + self.click_offset_x
                    target_y = location.y + self.click_offset_y

                    self._click_and_paste(target_x, target_y, text)
                    return True

                else:
                    # 3. 못 찾으면? -> 스크롤 조금 내리고 재시도
                    # print(f"못 찾음.. 스크롤 다운 (시도 {attempt+1}/{max_attempts})")
                    pyautogui.scroll(scroll_amount)
                    time.sleep(0.8)  # 스크롤 후 화면 렌더링 대기

            except Exception as e:
                # locateCenterOnScreen은 못 찾으면 에러를 낼 수도 있음 (버전에 따라 다름)
                # print(f"이미지 인식 오류: {e}")
                pyautogui.scroll(scroll_amount)
                time.sleep(0.8)

        return False

    def _click_and_paste(self, x, y, text):
        # 클릭
        pyautogui.click(x, y)
        time.sleep(0.2)

        # 기존 내용 삭제 (Ctrl+A -> Del)
        pyautogui.hotkey(self.ctrl_key, 'a')
        time.sleep(0.1)
        pyautogui.press('backspace')
        time.sleep(0.1)

        # 붙여넣기
        pyperclip.copy(text)
        pyautogui.hotkey(self.ctrl_key, 'v')