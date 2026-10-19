let currentExpenses = [];
let pendingDeleteId = null;

const formatINR = (val) => '₹' + Number(val || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

document.addEventListener('DOMContentLoaded', async () => {
    const now = new Date();
    const ym = now.toISOString().slice(0, 7);
    document.getElementById('globalMonthPicker').value = ym;
    document.getElementById('expDate').value = now.toISOString().slice(0, 10);

    await checkSession();
    setupNavigation();
    setupModalsAndForms();
    await loadDashboardData();
});

async function checkSession() {
    try {
        const res = await fetch('api?action=session');
        const data = await res.json();
        if (!data.authenticated) {
            window.location.href = 'index.html';
            return;
        }
        document.getElementById('activeUserDisplay').textContent = `Logged in as ${data.username}`;
    } catch (e) {
        console.error('Session check failed', e);
    }
}

function setupNavigation() {
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.view-section').forEach(v => v.classList.remove('active'));
            btn.classList.add('active');
            document.getElementById(btn.dataset.view).classList.add('active');
            document.getElementById('pageTitle').textContent = btn.textContent.replace(/^[^\s]+\s/, '');
        });
    });

    document.getElementById('logoutBtn').addEventListener('click', async () => {
        const params = new URLSearchParams({ action: 'logout' });
        await fetch('api', { method: 'POST', body: params });
        window.location.href = 'index.html';
    });

    document.getElementById('globalMonthPicker').addEventListener('change', loadDashboardData);
    ['searchInput', 'filterCategory', 'filterFromDate', 'filterToDate', 'sortSelect'].forEach(id => {
        document.getElementById(id).addEventListener('input', loadDashboardData);
    });
}

async function loadDashboardData() {
    const month = document.getElementById('globalMonthPicker').value;
    const search = document.getElementById('searchInput').value;
    const category = document.getElementById('filterCategory').value;
    const from = document.getElementById('filterFromDate').value;
    const to = document.getElementById('filterToDate').value;
    const sort = document.getElementById('sortSelect').value;

    const query = new URLSearchParams({ month, search, category, from, to, sort });
    try {
        const res = await fetch(`api?${query.toString()}`);
        if (res.status === 401) {
            window.location.href = 'index.html';
            return;
        }
        const data = await res.json();
        if (data.error) {
            showToast(data.error, true);
            return;
        }
        currentExpenses = data.expenses || [];
        renderMetrics(data);
        renderTables(currentExpenses);
        drawBarChart('dashCategoryChart', data.categoryTotals, 'category', 'total', '#3b82f6');
        drawBarChart('dashMonthlyChart', data.monthlyTotals, 'month', 'total', '#8b5cf6');
        drawBarChart('anaCategoryChart', data.categoryTotals, 'category', 'total', '#22d3ee');
        drawBarChart('anaMonthlyChart', data.monthlyTotals, 'month', 'total', '#10b981');
    } catch (e) {
        showToast('Failed to load expenses from database.', true);
    }
}

function renderMetrics(data) {
    const spent = Number(data.monthSpent || 0);
    const budget = Number(data.budget || 0);
    const remaining = budget - spent;
    const pct = budget > 0 ? Math.min(100, Math.round((spent / budget) * 100)) : 0;

    document.getElementById('kpiMonthSpent').textContent = formatINR(spent);
    document.getElementById('kpiBudgetLimit').textContent = formatINR(budget);
    document.getElementById('kpiRemaining').textContent = formatINR(remaining);
    document.getElementById('kpiRemaining').style.color = remaining < 0 ? 'var(--danger-red)' : 'var(--positive-green)';
    document.getElementById('kpiPercentUsed').textContent = `${pct}% of monthly budget used`;
    document.getElementById('kpiHighest').textContent = formatINR(data.highestExpense);
    document.getElementById('kpiTotalCount').textContent = `${data.expenseCount} recorded transactions`;

    document.getElementById('budgetInput').value = budget;
    document.getElementById('budgetProgressLabel').textContent = `${pct}% Utilized`;
    document.getElementById('budgetNumbersLabel').textContent = `${formatINR(spent)} /${formatINR(budget)}`;
    const bar = document.getElementById('budgetProgressBar');
    bar.style.width = `${pct}%`;
    bar.classList.toggle('warning', spent > budget);

    const warnBanner = document.getElementById('budgetAlertBanner');
    if (spent > budget) {
        warnBanner.textContent = `⚠️ Budget Alert: You have exceeded your ${data.month} budget by${formatINR(spent - budget)}!`;
        warnBanner.style.display = 'block';
    } else {
        warnBanner.style.display = 'none';
    }

    document.getElementById('anaTotalAllTime').textContent = formatINR(data.totalAllTime);
    document.getElementById('anaAverage').textContent = formatINR(data.averageExpense);
    document.getElementById('anaHighest').textContent = formatINR(data.highestExpense);
    document.getElementById('anaCount').textContent = data.expenseCount;
}

function renderTables(list) {
    const recentBody = document.getElementById('recentTableBody');
    const allBody = document.getElementById('allExpensesTableBody');

    const buildRows = (items) => {
        if (!items.length) {
            return `<tr><td colspan="6" style="text-align:center;color:var(--text-secondary);padding:26px;">No matching expenses found.</td></tr>`;
        }
        return items.map(item => `
            <tr>
                <td>${item.date}</td>
                <td><strong>${item.title}</strong><br><small style="color:var(--text-secondary)">${item.notes || ''}</small></td>
                <td><span class="cat-pill">${item.category}</span></td>
                <td>${item.paymentMethod}</td>
                <td style="font-weight:600;">${formatINR(item.amount)}</td>
                <td>
                    <button class="btn btn-secondary" style="padding:6px 10px;font-size:0.78rem;" onclick="openEditModal(${item.id})">Edit</button>
                    <button class="btn btn-danger" style="padding:6px 10px;font-size:0.78rem;" onclick="openDeleteModal(${item.id})">Delete</button>
                </td>
            </tr>
        `).join('');
    };

    recentBody.innerHTML = buildRows(list.slice(0, 6));
    allBody.innerHTML = buildRows(list);
}

function setupModalsAndForms() {
    const expModal = document.getElementById('expenseModal');
    document.getElementById('openAddModalBtn').addEventListener('click', () => {
        document.getElementById('modalTitle').textContent = 'Add New Expense';
        document.getElementById('expenseId').value = '';
        document.getElementById('expenseForm').reset();
        document.getElementById('expDate').value = new Date().toISOString().slice(0, 10);
        expModal.style.display = 'flex';
    });

    document.getElementById('closeModalBtn').addEventListener('click', () => expModal.style.display = 'none');

    document.getElementById('expenseForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const id = document.getElementById('expenseId').value;
        const params = new URLSearchParams({
            action: id ? 'editExpense' : 'addExpense',
            id,
            title: document.getElementById('expTitle').value,
            category: document.getElementById('expCategory').value,
            paymentMethod: document.getElementById('expMethod').value,
            amount: document.getElementById('expAmount').value,
            date: document.getElementById('expDate').value,
            notes: document.getElementById('expNotes').value
        });

        const res = await fetch('api', { method: 'POST', body: params });
        const data = await res.json();
        expModal.style.display = 'none';
        showToast(data.message || 'Saved!', !data.success);
        await loadDashboardData();
    });

    document.getElementById('budgetForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const params = new URLSearchParams({
            action: 'setBudget',
            month: document.getElementById('globalMonthPicker').value,
            budget: document.getElementById('budgetInput').value
        });
        const res = await fetch('api', { method: 'POST', body: params });
        const data = await res.json();
        showToast(data.message || 'Budget updated!', !data.success);
        await loadDashboardData();
    });

    const delModal = document.getElementById('deleteModal');
    document.getElementById('cancelDeleteBtn').addEventListener('click', () => delModal.style.display = 'none');
    document.getElementById('confirmDeleteBtn').addEventListener('click', async () => {
        if (!pendingDeleteId) return;
        const params = new URLSearchParams({ action: 'deleteExpense', id: pendingDeleteId });
        const res = await fetch('api', { method: 'POST', body: params });
        const data = await res.json();
        delModal.style.display = 'none';
        showToast(data.message || 'Deleted', !data.success);
        await loadDashboardData();
    });
}

window.openEditModal = function(id) {
    const item = currentExpenses.find(e => e.id === id);
    if (!item) return;
    document.getElementById('modalTitle').textContent = 'Edit Expense';
    document.getElementById('expenseId').value = item.id;
    document.getElementById('expTitle').value = item.title;
    document.getElementById('expCategory').value = item.category;
    document.getElementById('expMethod').value = item.paymentMethod;
    document.getElementById('expAmount').value = item.amount;
    document.getElementById('expDate').value = item.date;
    document.getElementById('expNotes').value = item.notes || '';
    document.getElementById('expenseModal').style.display = 'flex';
};

window.openDeleteModal = function(id) {
    pendingDeleteId = id;
    document.getElementById('deleteModal').style.display = 'flex';
};

function showToast(msg, isError = false) {
    const toast = document.getElementById('statusToast');
    toast.textContent = msg;
    toast.className = 'alert-banner ' + (isError ? 'alert-error' : 'alert-success');
    toast.style.display = 'block';
    setTimeout(() => { toast.style.display = 'none'; }, 3500);
}

function drawBarChart(canvasId, items, labelKey, valueKey, barColor) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (!items || !items.length) {
        ctx.fillStyle = '#94a3b8';
        ctx.font = '13px sans-serif';
        ctx.fillText('No expense data recorded yet.', 140, 120);
        return;
    }

    const maxVal = Math.max(...items.map(i => Number(i[valueKey]) || 0), 1);
    const padLeft = 45, padBottom = 35, padTop = 25;
    const chartW = canvas.width - padLeft - 20;
    const chartH = canvas.height - padBottom - padTop;
    const barW = Math.min(48, Math.floor(chartW / items.length) - 16);

    items.forEach((item, idx) => {
        const val = Number(item[valueKey]) || 0;
        const h = Math.max(4, (val / maxVal) * chartH);
        const x = padLeft + idx * (chartW / items.length) + 10;
        const y = padTop + chartH - h;

        ctx.fillStyle = barColor;
        ctx.beginPath();
        ctx.roundRect(x, y, barW, h, 6);
        ctx.fill();

        ctx.fillStyle = '#f8fafc';
        ctx.font = '11px sans-serif';
        ctx.fillText('₹' + Math.round(val), x, y - 6);

        ctx.fillStyle = '#94a3b8';
        ctx.font = '11px sans-serif';
        const lbl = String(item[labelKey]).slice(0, 10);
        ctx.fillText(lbl, x, canvas.height - 12);
    });
}
