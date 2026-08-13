function updateCartCount(count) {
  const normalizedCount = Math.max(0, Number(count) || 0);
  document.querySelectorAll("[data-cart-count]").forEach((element) => {
    element.textContent = normalizedCount > 0
      ? `장바구니 (${normalizedCount})`
      : "장바구니";
  });
}

async function loadCartCount() {
  if (!document.querySelector("[data-cart-count]")) return;
  try {
    const response = await fetch("/cart/count", {
      headers: { Accept: "application/json" }
    });
    if (!response.ok) return;
    const cart = await response.json();
    updateCartCount(cart.itemCount);
  } catch (error) {
    // 헤더 보조 정보 조회 실패는 현재 화면 사용을 막지 않는다.
  }
}

document.addEventListener("DOMContentLoaded", () => {
  const currentYear = String(new Date().getFullYear());
  document.querySelectorAll("[data-current-year]").forEach((element) => {
    element.textContent = currentYear;
  });
  loadCartCount();
});

document.addEventListener("cart:updated", (event) => updateCartCount(event.detail));

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
