(() => {
    const form = document.querySelector('#couponForm');
    if (!form) return;

    const guide = document.querySelector('#targetTypeGuide');
    const quantity = document.querySelector('#totalQuantity');
    const quantityGroup = quantity?.closest('.form-group');
    const maximumDiscountAmount = document.querySelector('#maximumDiscountAmount');
    const maximumDiscountAmountGroup = document.querySelector('#maximumDiscountAmountGroup');
    const guides = {
        ALL_MEMBERS: '전체 회원에게 모두 지급합니다. 발급 수량 제한은 없습니다.',
        NEW_MEMBERS: '유효기간 중 가입한 신규 회원에게 모두 지급합니다. 발급 수량 제한은 없습니다.',
        FIRST_ORDER: '첫 주문 조건을 만족하는 회원에게 모두 지급합니다. 발급 수량 제한은 없습니다.',
        BIRTHDAY: '해당 월 생일 회원에게 모두 지급합니다. 발급 수량 제한은 없습니다.',
        SPECIFIC_MEMBERS: '등록 후 상세 화면에서 관리자가 선택한 회원에게 수동 발급합니다. 발급 수량을 입력해 주세요.'
    };

    const selectedType = () => document.querySelector('input[name="targetType"]:checked')?.value ?? form.dataset.targetType;
    const refreshTargetPolicy = () => {
        const isSpecificMembers = selectedType() === 'SPECIFIC_MEMBERS';
        if (guide) guide.textContent = guides[selectedType()] ?? '발급 대상을 선택해 주세요.';
        quantityGroup.hidden = !isSpecificMembers;
        quantity.disabled = !isSpecificMembers;
        if (!isSpecificMembers) quantity.value = '';
    };

    document.querySelectorAll('input[name="targetType"]').forEach(input => input.addEventListener('change', refreshTargetPolicy));

    const selectedDiscountType = () => document.querySelector('input[name="discountType"]:checked')?.value;
    const refreshDiscountPolicy = () => {
        const isPercentage = selectedDiscountType() === 'PERCENTAGE';
        const canChangeDiscountType = Array.from(document.querySelectorAll('input[name="discountType"]'))
            .some(input => !input.disabled);

        maximumDiscountAmountGroup.hidden = !isPercentage;

        // 금액 할인에서는 값 자체가 정책에 사용되지 않으므로 화면·요청값 모두 비운다.
        if (!isPercentage && canChangeDiscountType) {
            maximumDiscountAmount.value = '';
            maximumDiscountAmount.disabled = true;
            return;
        }

        if (isPercentage && canChangeDiscountType) {
            maximumDiscountAmount.disabled = false;
        }
    };

    document.querySelectorAll('input[name="discountType"]').forEach(input => input.addEventListener('change', refreshDiscountPolicy));
    refreshTargetPolicy();
    refreshDiscountPolicy();
})();
