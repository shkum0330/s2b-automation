let currentPage = 0;
const pageSize = 20;

document.addEventListener("DOMContentLoaded", () => {
    loadLogs(0); // 페이지 로드 시 첫 페이지 조회
});

// 로그 목록 조회
async function loadLogs(page) {
    currentPage = page;

    // 검색 조건 수집
    const email = document.getElementById("searchEmail").value;
    const model = document.getElementById("searchModel").value;
    const success = document.getElementById("searchSuccess").value;
    // (날짜 검색이 필요하다면 여기에 추가)

    const params = {
        page: page,
        size: pageSize,
        memberEmail: email,
        modelName: model,
        success: success === "ALL" ? null : success
    };

    try {
        // [수정됨] 변경된 컨트롤러 경로 반영 (/api/v1/admin/log)
        const response = await axios.get('/api/v1/admin/log', { params });
        renderTable(response.data.content);
        renderPagination(response.data);
    } catch (error) {
        console.error("로그 조회 실패", error);
        // 토큰 만료 등의 에러는 admin-common.js의 인터셉터가 처리함
        if (error.response && error.response.status !== 401 && error.response.status !== 403) {
            alert("데이터를 불러오지 못했습니다.");
        }
    }
}

// 테이블 렌더링
function renderTable(logs) {
    const tbody = document.getElementById("logTableBody");
    tbody.innerHTML = "";

    if (!logs || logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center py-4">데이터가 없습니다.</td></tr>';
        return;
    }

    logs.forEach(log => {
        // 성공/실패 배지 스타일
        const badgeClass = log.success ? 'text-bg-success' : 'text-bg-danger';
        const badgeText = log.success ? '성공' : '실패';
        const errorMsg = log.errorMessage ? log.errorMessage : '-';
        const modelName = log.modelName || '-';

        const row = `
            <tr style="cursor: pointer;" onclick="showDetail(${log.generationLogId})">
                <td>${log.generationLogId}</td>
                <td>${log.memberEmail}</td>
                <td>${modelName}</td>
                <td><span class="badge ${badgeClass}">${badgeText}</span></td>
                <td class="text-truncate" style="max-width: 200px;">${errorMsg}</td>
                <td>${formatDate(log.createdAt)}</td>
            </tr>
        `;
        tbody.innerHTML += row;
    });
}

// 상세 조회 (모달)
async function showDetail(id) {
    try {
        // [수정됨] 변경된 컨트롤러 경로 반영
        const response = await axios.get(`/api/v1/admin/log/${id}`);
        const log = response.data;

        // 모달 내용 채우기
        document.getElementById("detailId").textContent = log.generationLogId;
        document.getElementById("detailResult").innerHTML = log.errorMessage === null
            ? '<span class="badge text-bg-success">성공</span>'
            : '<span class="badge text-bg-danger">실패</span>';

        // JSON 데이터 예쁘게 출력
        document.getElementById("detailRequest").textContent = tryFormatJson(log.requestBody);
        document.getElementById("detailResponse").textContent = log.responseBody ? tryFormatJson(log.responseBody) : "(응답 없음)";

        // 에러 메시지 영역
        const errorBox = document.getElementById("detailErrorBox");
        if (log.errorMessage) {
            errorBox.classList.remove("d-none");
            document.getElementById("detailError").textContent = log.errorMessage;
        } else {
            errorBox.classList.add("d-none");
        }

        // 모달 띄우기
        const modal = new bootstrap.Modal(document.getElementById('detailModal'));
        modal.show();

    } catch (error) {
        console.error(error);
        alert("상세 정보를 불러오지 못했습니다.");
    }
}

// JSON 파싱 및 포맷팅 헬퍼
function tryFormatJson(str) {
    try {
        return JSON.stringify(JSON.parse(str), null, 2);
    } catch (e) {
        return str; // 파싱 실패 시 원본 문자열 반환
    }
}

// 날짜 포맷팅
function formatDate(isoString) {
    if (!isoString) return '-';
    const date = new Date(isoString);
    return date.toLocaleString('ko-KR', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
}

// 페이징 렌더링
function renderPagination(pageData) {
    const pagination = document.getElementById("pagination");
    pagination.innerHTML = "";

    const totalPages = pageData.totalPages;
    const curr = pageData.number; // 0-based index

    if (totalPages <= 1) return;

    // 이전 버튼
    const prevDisabled = pageData.first ? "disabled" : "";
    pagination.innerHTML += `
        <li class="page-item ${prevDisabled}">
            <a class="page-link" href="#" onclick="loadLogs(${curr - 1})">이전</a>
        </li>
    `;

    // 페이지 번호 (현재 페이지 기준 앞뒤 2개씩 표시)
    for (let i = 0; i < totalPages; i++) {
        if (i >= curr - 2 && i <= curr + 2) {
            const active = i === curr ? "active" : "";
            pagination.innerHTML += `
                <li class="page-item ${active}">
                    <a class="page-link" href="#" onclick="loadLogs(${i})">${i + 1}</a>
                </li>
            `;
        }
    }

    // 다음 버튼
    const nextDisabled = pageData.last ? "disabled" : "";
    pagination.innerHTML += `
        <li class="page-item ${nextDisabled}">
            <a class="page-link" href="#" onclick="loadLogs(${curr + 1})">다음</a>
        </li>
    `;
}