(function () {
  "use strict";

  function money(value) {
    return Number(value || 0).toLocaleString("ko-KR") + "원";
  }

  function updateSummary(cart) {
    document.querySelector("[data-cart-total-quantity]").textContent = cart.totalQuantity + "개";
    document.querySelector("[data-cart-base-total]").textContent = money(cart.baseTotal);
    document.querySelector("[data-cart-option-total]").textContent = money(cart.optionTotal);
    document.querySelector("[data-cart-grand-total]").textContent = money(cart.grandTotal);
    document.dispatchEvent(new CustomEvent("cart:updated", { detail: cart.totalQuantity }));
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
    let totalQuantity = 0;
    let baseTotal = 0;
    let optionTotal = 0;
    document.querySelectorAll("[data-cart-item]").forEach(function (item) {
      const quantity = Number(item.querySelector("[name='quantity']").value);
      totalQuantity += quantity;
      baseTotal += Number(item.dataset.basePrice) * quantity;
      optionTotal += Number(item.dataset.optionPrice) * quantity;
    });
    updateSummary({
      totalQuantity: totalQuantity,
      baseTotal: baseTotal,
      optionTotal: optionTotal,
      grandTotal: baseTotal + optionTotal
    });
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
        item.querySelector("[data-cart-item-total]").textContent = money(cart.itemTotal);
        updateButtons(form);
        updateSummary(cart);
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
})();
