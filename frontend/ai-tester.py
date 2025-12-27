import requests
import json
import time
import sys


TARGET_SERVER_URL = "http://localhost:8080/api/generate-spec"
OLLAMA_API_URL = "http://localhost:11434/api/generate"
MODEL_NAME = "llama3"

def get_virtual_user_data():
    """
    로컬 GPU(AI)에게 테스트 데이터를 생성하도록 요청
    """
    prompt = """
    You are a QA Tester. Create a JSON object for testing an electronics product API.
    Fields required:
    1. "model": A realistic model number (e.g., "XY-1004", "TV-QLED-55").
    2. "specExample": Specifications in Korean (e.g., "220V / 60Hz / 소비전력 50W").
    3. "productNameExample": A product name in Korean (e.g., "삼성전자 스마트 TV", "LG 공기청정기").

    Output ONLY the JSON object. No explanation.
    """

    payload = {
        "model": MODEL_NAME,
        "prompt": prompt,
        "format": "json",
        "stream": False
    }

    try:
        # AI에게 데이터 생성 요청 (GPU 사용)
        print("[AI] 가상의 유저가 데이터를 생성 중입니다...")
        response = requests.post(OLLAMA_API_URL, json=payload)
        response.raise_for_status()

        result_text = response.json()['response']
        return json.loads(result_text)

    except Exception as e:
        print(f"AI 통신 오류: {e}")
        return None


def run_test(token):
    """
    입력받은 토큰을 사용하여 서버에 요청 전송
    """
    # AI가 데이터 생성
    test_data = get_virtual_user_data()
    if not test_data:
        return

    print(f"[생성된 데이터] {json.dumps(test_data, ensure_ascii=False)}")

    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    }

    # 서버로 전송
    try:
        print(f"[전송] 서버로 요청을 보냅니다...")
        start_time = time.time()

        res = requests.post(TARGET_SERVER_URL, json=test_data, headers=headers)

        duration = time.time() - start_time

        if res.status_code == 200:
            print(f"[성공] 서버 응답 ({duration:.2f}초): {res.json()}")
        elif res.status_code == 401:
            print(f"[인증 실패] 토큰이 만료되었거나 잘못되었습니다.")
            print("   >> 프로그램을 종료하고 올바른 토큰으로 다시 시작해주세요.")
            sys.exit(0)  # 인증 실패 시 프로그램 종료
        else:
            print(f"[실패] 상태 코드: {res.status_code}")
            print(f"   에러 내용: {res.text}")

    except requests.exceptions.ConnectionError:
        print("[접속 불가] 스프링 부트 서버가 켜져 있는지 확인해주세요.")
    except Exception as e:
        print(f"[에러] {e}")


if __name__ == "__main__":
    print(f"=== AI 가상 유저 테스트 ===")

    # 실행 시 토큰 입력받기
    print("\n🔑 테스트를 수행할 JWT Access Token을 입력해주세요.")
    access_token = input("Token 입력 >> ").strip()

    if not access_token:
        print("토큰이 입력되지 않았습니다. 프로그램을 종료합니다.")
        sys.exit(0)

    print("\n🚀 테스트를 시작합니다! (중단하려면 Ctrl+C를 누르세요)")
    print("------------------------------------------------")

    count = 1
    # 무한 루프로 연속 실행
    while True:
        try:
            print(f"\n[Test Case #{count}]")
            run_test(access_token)
            count += 1
            print("------------------------------------------------")
            time.sleep(3)
        except KeyboardInterrupt:
            print("\n테스트를 중단했습니다.")
            break