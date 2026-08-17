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

// 안 읽은 알림 개수 DB API 호출 및 뱃지 업데이트 (세대 번호 관리로 지연 응답 덮어쓰기 방지)
let unreadCountSeq = 0;
async function updateNotificationUnreadCount() {
  const currentSeq = ++unreadCountSeq;
  try {
    const response = await fetch('/api/notifications/unread-count');
    if (!response.ok || currentSeq !== unreadCountSeq) return;
    const count = await response.json();
    if (currentSeq !== unreadCountSeq) return;
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

// 토스트 팝업 생성 및 렌더링 헬퍼 함수
function showToast(title, content, targetUrl, notificationId) {
  let container = document.querySelector(".toast-container");
  if (!container) {
    container = document.createElement("div");
    container.className = "toast-container";
    document.body.appendChild(container);
  }

  const toast = document.createElement("div");
  toast.className = "toast-item";
  toast.innerHTML = `
    <div class="toast-title">🔔 ${escapeHtmlApp(title || "알림")}</div>
    <div class="toast-content">${escapeHtmlApp(content || "")}</div>
  `;

  toast.addEventListener("click", () => {
    if (notificationId) {
      const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
      const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
      const headers = {};
      if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

      fetch(`/api/notifications/${notificationId}/read`, {
        method: "PATCH",
        headers: headers,
        keepalive: true
      }).finally(() => {
        if (targetUrl) {
          window.location.href = targetUrl;
        }
      });
    } else if (targetUrl) {
      window.location.href = targetUrl;
    }
  });

  container.appendChild(toast);

  setTimeout(() => {
    toast.classList.add("toast-item--out");
    setTimeout(() => {
      if (toast.parentNode) {
        toast.parentNode.removeChild(toast);
      }
    }, 300);
  }, 3500);
}

function escapeHtmlApp(text) {
  if (!text) return "";
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
