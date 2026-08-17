(function () {
  let notificationStompClient = null;

  function initNotificationWebSocket() {
    if (typeof SockJS === "undefined" || typeof Stomp === "undefined") {
      return;
    }

    const socket = new SockJS("/ws");
    notificationStompClient = Stomp.over(socket);
    notificationStompClient.debug = null;

    notificationStompClient.connect({}, () => {
      const currentMemberId = getCurrentMemberId();

      // 1. 개인 고객 알림 구독 (/topic/notifications/{memberId})
      if (currentMemberId) {
        notificationStompClient.subscribe(`/topic/notifications/${currentMemberId}`, (event) => {
          handleNotificationReceived(event);
        });
      }

      // 2. 관리자 알림 구독 (/topic/admin/notifications)
      if (isAdminUser()) {
        notificationStompClient.subscribe("/topic/admin/notifications", (event) => {
          handleNotificationReceived(event);
        });
      }
    }, () => {
      // 연결 오류 시 5초 후 자동 재연결
      setTimeout(initNotificationWebSocket, 5000);
    });
  }

  function handleNotificationReceived(event) {
    try {
      const notification = JSON.parse(event.body);
      if (!notification) return;

      // 1. 토스트 팝업 띄우기
      if (typeof showToast === "function") {
        showToast(notification.title, notification.content, notification.targetUrl);
      }

      // 2. 헤더 알림 배지 숫자 +1 동적 증가
      if (typeof updateNotificationUnreadCount === "function") {
        updateNotificationUnreadCount();
      }

      // 3. 현재 알림 목록 화면(/notifications)을 보는 중인 경우 DOM 동적 상단 삽입
      appendNotificationToDOM(notification);
    } catch (e) {
      console.error("알림 수신 처리 오류:", e);
    }
  }

  function appendNotificationToDOM(notification) {
    const listContainer = document.querySelector("[data-notification-list]");
    if (!listContainer) return;

    const item = document.createElement("div");
    item.className = "notification-card notification-card--unread";
    item.style.cssText = "margin-bottom: 12px; padding: 12px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff;";
    item.innerHTML = `
      <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
        <span class="badge badge--primary" style="font-weight: bold;">${escapeHtmlNoti(notification.title || "알림")}</span>
        <span class="text-muted" style="font-size: 12px;">방금 전</span>
      </div>
      <p style="margin: 0; font-size: 13px; color: #374151;">${escapeHtmlNoti(notification.content || "")}</p>
    `;
    if (notification.targetUrl) {
      item.style.cursor = "pointer";
      item.addEventListener("click", () => {
        window.location.href = notification.targetUrl;
      });
    }

    if (listContainer.firstChild) {
      listContainer.insertBefore(item, listContainer.firstChild);
    } else {
      listContainer.appendChild(item);
    }
  }

  function getCurrentMemberId() {
    const element = document.querySelector("[data-member-id]");
    if (element && element.dataset && element.dataset.memberId) {
      return element.dataset.memberId;
    }
    return null;
  }

  function isAdminUser() {
    const identity = document.querySelector("[data-account-identity]");
    return Boolean(identity && identity.textContent && identity.textContent.includes("관리자"));
  }

  function escapeHtmlNoti(text) {
    if (!text) return "";
    return String(text)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  document.addEventListener("DOMContentLoaded", () => {
    initNotificationWebSocket();
  });
})();
