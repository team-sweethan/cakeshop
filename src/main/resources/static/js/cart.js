(function () {
  "use strict";

  function money(value) {
    return Number(value || 0).toLocaleString("ko-KR") + "원";
  }

  function updateSummary(cart) {
    document.querySelector("[data-cart-item-count]").textContent = cart.itemCount + "종";
    document.querySelector("[data-cart-base-total]").textContent = money(cart.baseTotal);
    document.querySelector("[data-cart-option-total]").textContent = money(cart.optionTotal);
    document.querySelector("[data-cart-grand-total]").textContent = money(cart.grandTotal);
  }

  function updateButtons(form) {
    const input = form.elements.quantity;
    const quantity = Number(input.value);
    const maximum = input.max ? Number(input.max) : null;
    form.querySelector('[data-cart-quantity-change="-1"]').disabled = quantity <= 1;
    form.querySelector('[data-cart-quantity-change="1"]').disabled =
      maximum !== null && quantity >= maximum;
  }

  function optimisticItemTotal(form) {
    const item = form.closest("[data-cart-item]");
    item.querySelector("[data-cart-item-total]").textContent =
      money(Number(item.dataset.unitPrice) * Number(form.elements.quantity.value));
  }

  function optimisticSummary() {
    let itemCount = 0;
    let baseTotal = 0;
    let optionTotal = 0;
    document.querySelectorAll("[data-cart-item]").forEach(function (item) {
      const selector = item.querySelector("[data-cart-item-select]");
      if (item.dataset.available !== "true" || !selector || !selector.checked) return;
      itemCount += 1;
      const quantity = Number(item.querySelector("[name='quantity']").value);
      baseTotal += Number(item.dataset.basePrice) * quantity;
      optionTotal += Number(item.dataset.optionPrice) * quantity;
    });
    updateSummary({
      itemCount: itemCount,
      baseTotal: baseTotal,
      optionTotal: optionTotal,
      grandTotal: baseTotal + optionTotal
    });
  }

  function updateAvailability(item, available) {
    item.dataset.available = String(available);
    item.classList.toggle("is-disabled", !available);
    const status = item.querySelector("[data-cart-item-status]");
    status.classList.toggle("badge--success", available);
    status.classList.toggle("badge--danger", !available);
    status.textContent = available ? "주문 가능" : "재고 부족 또는 판매 중지";
    const selector = item.querySelector("[data-cart-item-select]");
    if (selector) {
      selector.disabled = !available;
      selector.checked = available;
    }
    updateSelectAllState();
  }

  function updateSelectAllState() {
    const selectAll = document.querySelector("[data-cart-select-all]");
    if (!selectAll) return;
    const itemSelectors = Array.from(
      document.querySelectorAll("[data-cart-item-select]:not(:disabled)")
    );
    const selectedCount = itemSelectors.filter(input => input.checked).length;
    selectAll.checked = itemSelectors.length > 0 && selectedCount === itemSelectors.length;
    selectAll.indeterminate = selectedCount > 0 && selectedCount < itemSelectors.length;
    selectAll.disabled = itemSelectors.length === 0;
  }

  function initializeItemSelection() {
    const selectAll = document.querySelector("[data-cart-select-all]");
    if (!selectAll) return;
    selectAll.addEventListener("change", function () {
      document.querySelectorAll("[data-cart-item-select]:not(:disabled)")
        .forEach(input => { input.checked = selectAll.checked; });
      updateSelectAllState();
      optimisticSummary();
    });
    document.addEventListener("change", function (event) {
      if (event.target.matches("[data-cart-item-select]")) {
        updateSelectAllState();
        optimisticSummary();
      }
    });
    updateSelectAllState();
    optimisticSummary();
  }

  async function saveQuantity(form) {
    if (form.dataset.saving === "true") {
      form.dataset.pending = "true";
      return;
    }
    form.dataset.saving = "true";
    form.dataset.pending = "false";

    const item = form.closest("[data-cart-item]");
    try {
      const response = await fetch(form.action + "/async", {
        method: "POST",
        headers: { Accept: "application/json" },
        body: new FormData(form)
      });
      if (!response.ok) throw new Error("quantity update failed");
      const cart = await response.json();
      if (Number(item.dataset.itemId) !== cart.itemId) throw new Error("item mismatch");
      if (form.dataset.pending !== "true") {
        form.elements.quantity.value = cart.quantity;
        cart.itemAvailability.forEach(function (state) {
          const affectedItem = document.querySelector(
            '[data-cart-item][data-item-id="' + state.itemId + '"]');
          if (affectedItem) updateAvailability(affectedItem, state.available);
        });
        item.querySelector("[data-cart-item-total]").textContent = money(cart.itemTotal);
        updateButtons(form);
        // 수량 갱신 후에도 주문 요약은 현재 체크된 주문 대상만 다시 계산한다.
        optimisticSummary();
      }
    } catch (error) {
      window.location.reload();
      return;
    } finally {
      form.dataset.saving = "false";
    }

    if (form.dataset.pending === "true") saveQuantity(form);
  }

  document.addEventListener("click", function (event) {
    const button = event.target.closest("[data-cart-quantity-change]");
    if (!button) return;
    const form = button.closest("[data-cart-quantity-form]");
    const input = form.elements.quantity;
    const next = Number(input.value) + Number(button.dataset.cartQuantityChange);
    const maximum = input.max ? Number(input.max) : Number.MAX_SAFE_INTEGER;
    input.value = Math.max(1, Math.min(maximum, next));
    optimisticItemTotal(form);
    optimisticSummary();
    updateButtons(form);
    saveQuantity(form);
  });

  document.addEventListener("change", function (event) {
    if (!event.target.matches("[data-cart-quantity-form] [name='quantity']")) return;
    const form = event.target.closest("[data-cart-quantity-form]");
    const maximum = event.target.max ? Number(event.target.max) : Number.MAX_SAFE_INTEGER;
    event.target.value = Math.max(1, Math.min(maximum, Number(event.target.value) || 1));
    optimisticItemTotal(form);
    optimisticSummary();
    updateButtons(form);
    saveQuantity(form);
  });

  document.addEventListener("submit", function (event) {
    const form = event.target.closest("[data-cart-quantity-form]");
    if (!form) return;
    event.preventDefault();
    saveQuantity(form);
  });

  document.addEventListener("DOMContentLoaded", initializeItemSelection);
})();
