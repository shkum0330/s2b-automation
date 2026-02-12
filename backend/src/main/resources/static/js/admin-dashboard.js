let currentPage = 0;
const pageSize = 20;

document.addEventListener("DOMContentLoaded", () => {
    loadLogs(0);
});

async function loadLogs(page) {
    currentPage = page;

    const email = document.getElementById("searchEmail").value;
    const model = document.getElementById("searchModel").value;
    const success = document.getElementById("searchSuccess").value;

    const params = {
        page: page,
        size: pageSize,
        memberEmail: email,
        modelName: model,
        success: success === "ALL" ? null : success
    };

    try {
        const response = await axios.get("/api/v1/admin/log", { params });
        renderTable(response.data.content);
        renderPagination(response.data);
    } catch (error) {
        console.error("로그 조회 실패", error);
        if (!error.response || (error.response.status !== 401 && error.response.status !== 403)) {
            alert("데이터를 불러오지 못했습니다.");
        }
    }
}

function renderTable(logs) {
    const tbody = document.getElementById("logTableBody");
    tbody.replaceChildren();

    if (!logs || logs.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 5;
        cell.className = "text-center py-5";
        cell.textContent = "데이터가 없습니다.";
        row.appendChild(cell);
        tbody.appendChild(row);
        return;
    }

    logs.forEach((log) => {
        const row = document.createElement("tr");
        row.style.cursor = "pointer";
        row.addEventListener("click", () => showDetail(log.generationLogId));

        const idCell = document.createElement("td");
        idCell.textContent = String(log.generationLogId ?? "");

        const emailCell = document.createElement("td");
        emailCell.textContent = log.memberEmail ?? "";

        const modelCell = document.createElement("td");
        modelCell.classList.add("fw-bold");
        modelCell.textContent = log.modelName || "(없음)";

        const statusCell = document.createElement("td");
        const statusBadge = document.createElement("span");
        statusBadge.className = `badge ${log.success ? "text-bg-success" : "text-bg-danger"}`;
        statusBadge.textContent = log.success ? "성공" : "실패";
        statusCell.appendChild(statusBadge);

        const createdAtCell = document.createElement("td");
        createdAtCell.textContent = formatDate(log.createdAt);

        row.appendChild(idCell);
        row.appendChild(emailCell);
        row.appendChild(modelCell);
        row.appendChild(statusCell);
        row.appendChild(createdAtCell);
        tbody.appendChild(row);
    });
}

async function showDetail(id) {
    try {
        const response = await axios.get(`/api/v1/admin/log/${id}`);
        const log = response.data;

        document.getElementById("detailId").textContent = log.generationLogId;

        const detailResult = document.getElementById("detailResult");
        detailResult.replaceChildren();
        const statusBadge = document.createElement("span");
        statusBadge.className = !log.errorMessage ? "badge text-bg-success" : "badge text-bg-danger";
        statusBadge.textContent = !log.errorMessage ? "성공" : "실패";
        detailResult.appendChild(statusBadge);

        document.getElementById("detailRequest").textContent = tryFormatJson(log.requestBody);
        document.getElementById("detailResponse").textContent = log.responseBody ? tryFormatJson(log.responseBody) : "(응답 없음)";

        const errorBox = document.getElementById("detailErrorBox");
        if (log.errorMessage) {
            errorBox.classList.remove("d-none");
            document.getElementById("detailError").textContent = log.errorMessage;
        } else {
            errorBox.classList.add("d-none");
            document.getElementById("detailError").textContent = "";
        }

        new bootstrap.Modal(document.getElementById("detailModal")).show();
    } catch (error) {
        console.error(error);
        alert("상세 정보를 불러오지 못했습니다.");
    }
}

function tryFormatJson(str) {
    try {
        return JSON.stringify(JSON.parse(str), null, 2);
    } catch (e) {
        return str;
    }
}

function formatDate(isoString) {
    if (!isoString) {
        return "-";
    }
    const date = new Date(isoString);
    return date.toLocaleString("ko-KR", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
    });
}

function createPageItem(label, page, disabled = false, active = false) {
    const li = document.createElement("li");
    li.className = `page-item${disabled ? " disabled" : ""}${active ? " active" : ""}`;

    const a = document.createElement("a");
    a.className = "page-link";
    a.href = "#";
    a.textContent = label;
    a.addEventListener("click", (event) => {
        event.preventDefault();
        if (!disabled) {
            loadLogs(page);
        }
    });

    li.appendChild(a);
    return li;
}

function renderPagination(pageData) {
    const pagination = document.getElementById("pagination");
    pagination.replaceChildren();

    const totalPages = pageData.totalPages;
    const curr = pageData.number;

    if (totalPages <= 1) {
        return;
    }

    pagination.appendChild(createPageItem("이전", curr - 1, pageData.first));

    for (let i = 0; i < totalPages; i++) {
        if (i >= curr - 2 && i <= curr + 2) {
            pagination.appendChild(createPageItem(String(i + 1), i, false, i === curr));
        }
    }

    pagination.appendChild(createPageItem("다음", curr + 1, pageData.last));
}
