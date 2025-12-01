let currentPage = 0;
const pageSize = 20;

// 페이지 로드 시 자동으로 로그 조회 시작
document.addEventListener("DOMContentLoaded", () => {
    loadLogs(0);
});

// 1. 로그 목록 조회 API 호출
async function loadLogs(page) {
    currentPage = page;

    // 검색 조건 가져오기
    const email = document.getElementById("searchEmail").value;
    const model = document.getElementById("searchModel").value;
    const success = document.getElementById("searchSuccess").value; // ALL, true, false

    const params = {
        page: page,
        size: pageSize,
        memberEmail: email,
        modelName: model,
        success: success === "ALL" ? null : success
    };

    try {
        // 백엔드 컨트롤러 경로와 일치해야 함
        const response = await axios.get('/api/v1/admin/log', { params });
        renderTable(response.data.content);
        renderPagination(response.data);
    } catch (error) {
        console.error("로그 조회 실패", error);
        if (!error.response || (error.response.status !== 401 && error.response.status !== 403)) {
            alert("데이터를 불러오지 못했습니다.");
        }
    }
}

// 2. 테이블 HTML 렌더링
function renderTable(logs) {
    const tbody = document.getElementById("logTableBody");
    tbody.innerHTML = "";

    if (!logs || logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center py-5">데이터가 없습니다.</td></tr>';
        return;
    }

    logs.forEach(log => {
        const badgeClass = log.success ? 'text-bg-success' : 'text-bg-danger';
        const badgeText = log.success ? '성공' : '실패';
        const errorMsg = log.errorMessage ? log.errorMessage : '-';
        const modelName = log.modelName || '(없음)';

        const row = `
            <tr style="cursor: pointer;" onclick="showDetail(${log.generationLogId})">
                <td>${log.generationLogId}</td>
                <td>${log.memberEmail}</td>
                <td class="fw-bold">${modelName}</td>
                <td><span class="badge ${badgeClass}">${badgeText}</span></td>
                <td class="text-truncate" style="max-width: 250px;">${errorMsg}</td>
                <td>${formatDate(log.createdAt)}</td>
            </tr>
        `;
        tbody.innerHTML += row;
    });
}

// 3. 상세 정보 모달 띄우기
async function showDetail(id) {
    try {
        const response = await axios.get(`/api/v1/admin/log/${id}`);
        const log = response.data;

        // 모달 데이터 채우기
        document.getElementById("detailId").textContent = log.generationLogId;
        document.getElementById("detailResult").innerHTML = !log.errorMessage
            ? '<span class="badge text-bg-success">성공</span>'
            : '<span class="badge text-bg-danger">실패</span>';

        // JSON 예쁘게 보여주기 (Pretty Print)
        document.getElementById("detailRequest").textContent = tryFormatJson(log.requestBody);
        document.getElementById("detailResponse").textContent = log.responseBody ? tryFormatJson(log.responseBody) : "(응답 없음)";

        // 에러 메시지
        const errorBox = document.getElementById("detailErrorBox");
        if (log.errorMessage) {
            errorBox.classList.remove("d-none");
            document.getElementById("detailError").textContent = log.errorMessage;
        } else {
            errorBox.classList.add("d-none");
        }

        // Bootstrap 모달 실행
        new bootstrap.Modal(document.getElementById('detailModal')).show();

    } catch (error) {
        console.error(error);
        alert("상세 정보를 불러오지 못했습니다.");
    }
}

// JSON 포맷팅 유틸리티
function tryFormatJson(str) {
    try {
        return JSON.stringify(JSON.parse(str), null, 2);
    } catch (e) {
        return str;
    }
}

// 날짜 포맷팅 유틸리티
function formatDate(isoString) {
    if (!isoString) return '-';
    const date = new Date(isoString);
    return date.toLocaleString('ko-KR', {
        month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
}

// 4. 페이징 버튼 렌더링
function renderPagination(pageData) {
    const pagination = document.getElementById("pagination");
    pagination.innerHTML = "";

    const totalPages = pageData.totalPages;
    const curr = pageData.number;

    if (totalPages <= 1) return;

    // [이전]
    const prevDisabled = pageData.first ? "disabled" : "";
    pagination.innerHTML += `
        <li class="page-item ${prevDisabled}">
            <a class="page-link" href="#" onclick="loadLogs(${curr - 1})">이전</a>
        </li>
    `;

    // [번호] (현재 페이지 앞뒤로 2개씩만 표시)
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

    // [다음]
    const nextDisabled = pageData.last ? "disabled" : "";
    pagination.innerHTML += `
        <li class="page-item ${nextDisabled}">
            <a class="page-link" href="#" onclick="loadLogs(${curr + 1})">다음</a>
        </li>
    `;
}