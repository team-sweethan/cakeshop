function updateMockCartCount(items) {
  let cartItems = items;
  if (!Array.isArray(cartItems)) {
    try {
      cartItems = JSON.parse(localStorage.getItem("cakeShopCart") || "[]");
    } catch (error) {
      cartItems = [];
    }
  }

  const count = Array.isArray(cartItems)
    ? cartItems.reduce((sum, item) => sum + Math.max(0, Number(item && item.quantity) || 0), 0)
    : 0;
  document.querySelectorAll("[data-cart-count]").forEach((element) => {
    element.textContent = `장바구니 (${count})`;
  });
}

document.addEventListener("DOMContentLoaded", () => {
  const currentYear = String(new Date().getFullYear());
  document.querySelectorAll("[data-current-year]").forEach((element) => {
    element.textContent = currentYear;
  });
  updateMockCartCount();
});

document.addEventListener("cart:updated", (event) => updateMockCartCount(event.detail));
window.addEventListener("storage", (event) => {
  if (event.key === "cakeShopCart") updateMockCartCount();
});

// 안 읽은 알림 개수 DB API 호출 및 뱃지 업데이트
async function updateNotificationUnreadCount() {
  try {
    const response = await fetch('/api/notifications/unread-count');
    if (!response.ok) return;
    const count = await response.json();
    document.querySelectorAll('[data-unread-count]').forEach((element) => {
      element.textContent = count;
      element.style.display = count > 0 ? 'inline-block' : 'none';
    });
  } catch (e) {
    // 비로그인 시 예외 무시
  }
}

document.addEventListener('DOMContentLoaded', () => {
  updateNotificationUnreadCount();
});
