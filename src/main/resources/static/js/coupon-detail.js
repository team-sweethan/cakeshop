(() => {
    const detail = document.querySelector('[data-coupon-detail]');
    if (!detail) return;

    const { couponId, keyword, status, page, csrfName, csrfToken, specificMembers } = detail.dataset;
    configureTargetMemberHeader();
    configureIssuedMemberHeader();
    bindSearch('targetMember', `/admin/coupons/${couponId}/target-members`, true, true);
    bindSearch('issuedMember', `/admin/coupons/${couponId}/issued-members`, false, false);

    function bindSearch(prefix, endpoint, canIssue, requiresKeyword) {
        const input = document.querySelector(`#${prefix}Search`);
        const button = document.querySelector(`#${prefix}SearchButton`);
        const resetButton = document.querySelector(`#${prefix}ResetButton`);
        const searchType = document.querySelector(`#${prefix}SearchType`);
        const results = document.querySelector(`#${prefix}Results`);
        const pagination = document.querySelector(`#${prefix}Pagination`);
        if (!input || !button || !results || !pagination) return;

        if (requiresKeyword) {
            updateTargetPlaceholder(searchType, input);
            searchType?.addEventListener('change', () => updateTargetPlaceholder(searchType, input));
        }

        const load = async (targetPage = 1) => {
            const value = input.value.trim();
            const validationMessage = requiresKeyword
                ? getTargetValidationMessage(searchType?.value, value)
                : null;
            if (validationMessage) {
                results.innerHTML = `<tr><td colspan="7">${validationMessage}</td></tr>`;
                pagination.replaceChildren();
                return;
            }
            const params = new URLSearchParams({ page: targetPage });
            if (requiresKeyword) {
                params.set('searchType', searchType?.value ?? 'NAME');
                params.set('keyword', value);
            } else if (value) {
                if (!/^\d+$/.test(value)) {
                    results.innerHTML = '<tr><td colspan="7">회원 ID는 숫자로 입력해주세요.</td></tr>';
                    pagination.replaceChildren();
                    return;
                }
                params.set('memberId', value);
            }
            const response = await fetch(`${endpoint}?${params}`);
            const result = await response.json();
            renderRows(results, result.content, canIssue);
            renderPagination(pagination, result, load);
        };
        button.addEventListener('click', () => load());
        input.addEventListener('keydown', event => { if (event.key === 'Enter') { event.preventDefault(); load(); } });
        if (requiresKeyword) {
            results.innerHTML = '<tr><td colspan="7">검색어를 입력해주세요.</td></tr>';
        } else {
            load();
        }
        resetButton?.addEventListener('click', () => {
            input.value = '';
            if (requiresKeyword) {
                results.innerHTML = '<tr><td colspan="7">검색어를 입력해주세요.</td></tr>';
                pagination.replaceChildren();
                return;
            }
            load();
        });
    }

    function updateTargetPlaceholder(searchType, input) {
        const placeholders = {
            MEMBER_ID: '회원 ID를 입력해주세요',
            NAME: '이름을 2자 이상 입력해주세요',
            EMAIL: '이메일을 2자 이상 입력해주세요',
            PHONE: '휴대폰 번호를 4자 이상 입력해주세요'
        };
        input.placeholder = placeholders[searchType?.value] ?? placeholders.NAME;
    }

    function getTargetValidationMessage(searchType, value) {
        const messages = {
            MEMBER_ID: '회원 ID를 입력해주세요.',
            NAME: '이름을 2자 이상 입력해주세요.',
            EMAIL: '이메일을 2자 이상 입력해주세요.',
            PHONE: '휴대폰 번호를 4자 이상 입력해주세요.'
        };
        const type = searchType ?? 'NAME';
        if (!value || value.length < ({ MEMBER_ID: 1, NAME: 2, EMAIL: 2, PHONE: 4 }[type] ?? 2)) {
            return messages[type] ?? messages.NAME;
        }
        if (type === 'MEMBER_ID' && !/^\d+$/.test(value)) {
            return '회원 ID는 숫자로 입력해주세요.';
        }
        return null;
    }

    function renderRows(container, members, canIssue) {
        container.replaceChildren();
        if (members.length === 0) {
            const colspan = canIssue ? 7 : (specificMembers === 'true' ? 8 : 7);
            container.innerHTML = `<tr><td colspan="${colspan}">조회 결과가 없습니다.</td></tr>`;
            return;
        }
        members.forEach(member => {
            const row = document.createElement('tr');
            const values = canIssue
                ? [member.memberId, member.name, member.email, member.phone, formatBirthday(member.birthday),
                    member.issued ? '발급 완료' : '미발급']
                : [member.memberId, member.name, member.email, member.phone, formatBirthday(member.birthday),
                    member.statusDescription, formatDate(member.usedAt)];
            values.forEach(value => { const cell = document.createElement('td'); cell.textContent = value ?? '-'; row.appendChild(cell); });
            const action = document.createElement('td');
            if (canIssue && !member.issued) action.appendChild(createForm(member.memberId, 'issue', '발급', 'btn btn--primary'));
            else if (specificMembers === 'true') {
                if (member.status === 'AVAILABLE') action.appendChild(createForm(member.memberId, 'cancel', '발급 취소', 'btn btn--danger'));
                row.appendChild(action);
            }
            if (canIssue) row.appendChild(action);
            container.appendChild(row);
        });
    }

    function createForm(memberId, action, text, className) {
        const form = document.createElement('form');
        form.method = 'post';
        form.action = `/admin/coupons/${couponId}/members/${memberId}/${action}`;
        form.addEventListener('submit', event => {
            const message = action === 'issue' ? '선택한 회원에게 쿠폰을 발급하시겠습니까?' : '선택한 회원의 쿠폰 발급을 취소하시겠습니까?';
            if (!window.confirm(message)) event.preventDefault();
        });
        [[csrfName, csrfToken], ['keyword', keyword], ['status', status], ['page', page]].forEach(([name, value]) => {
            const input = document.createElement('input'); input.type = 'hidden'; input.name = name; input.value = value ?? ''; form.appendChild(input);
        });
        const button = document.createElement('button'); button.type = 'submit'; button.className = className; button.textContent = text; form.appendChild(button);
        return form;
    }

    function renderPagination(container, result, load) {
        container.replaceChildren();
        const blockSize = 10;
        const startPage = Math.floor((result.page - 1) / blockSize) * blockSize + 1;
        const endPage = Math.min(startPage + blockSize - 1, result.totalPages);

        if (startPage > 1) {
            container.appendChild(createPageButton('이전', startPage - 1, load));
        }
        for (let current = startPage; current <= endPage; current += 1) {
            const button = document.createElement('button'); button.type = 'button'; button.className = 'btn'; button.textContent = current;
            button.disabled = current === result.page; button.addEventListener('click', () => load(current)); container.appendChild(button);
        }
        if (endPage < result.totalPages) {
            container.appendChild(createPageButton('다음', endPage + 1, load));
        }
    }

    function createPageButton(text, targetPage, load) {
        const button = document.createElement('button');
        button.type = 'button'; button.className = 'btn'; button.textContent = text;
        button.addEventListener('click', () => load(targetPage));
        return button;
    }

    function configureIssuedMemberHeader() {
        const headerRow = document.querySelector('#issuedMemberResults')?.closest('table')?.querySelector('thead tr');
        if (!headerRow) return;

        headerRow.replaceChildren();
        ['회원 ID', '이름', '이메일', '휴대폰 번호', '생일', '사용', '사용 일시'].forEach(text => {
            const header = document.createElement('th');
            header.textContent = text;
            headerRow.appendChild(header);
        });
        if (specificMembers === 'true') {
            const header = document.createElement('th');
            header.textContent = '관리';
            headerRow.appendChild(header);
        }
    }

    function configureTargetMemberHeader() {
        const headerRow = document.querySelector('#targetMemberResults')?.closest('table')?.querySelector('thead tr');
        if (!headerRow) return;

        headerRow.replaceChildren();
        ['회원 ID', '이름', '이메일', '휴대폰 번호', '생일', '발급 상태', '관리'].forEach(text => {
            const header = document.createElement('th');
            header.textContent = text;
            headerRow.appendChild(header);
        });
    }

    function formatDate(value) { return value ? value.replace('T', ' ').slice(0, 16) : '-'; }

    function formatBirthday(value) { return value || '-'; }

})();
