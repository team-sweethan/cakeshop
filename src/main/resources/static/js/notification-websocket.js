(function () {
  let notificationStompClient = null;

  function initNotificationWebSocket() {
    if (typeof SockJS === "undefined" || typeof Stomp === "undefined") {
      return;
    }

    const currentMemberId = getCurrentMemberId();
    const isAdmin = isAdminUser();

    // 비로그인 익명 유저인 경우 보호된 웹소켓 연결 시도 안함
    if (!currentMemberId && !isAdmin) {
      return;
    }

    if (notificationStompClient && notificationStompClient.connected) {
      return;
    }

    const socket = new SockJS("/ws");
    notificationStompClient = Stomp.over(socket);
    notificationStompClient.debug = null;

    notificationStompClient.connect({}, () => {
      // 1. 개인 알림 구독 (/topic/notifications/{memberId})
      if (currentMemberId) {
        notificationStompClient.subscribe(`/topic/notifications/${currentMemberId}`, (event) => {
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

      // 3. 현재 알림 목록 화면(/notifications 또는 /admin/notifications)에 DOM 동적 상단 삽입
      appendNotificationToDOM(notification);
    } catch (e) {
      console.error("알림 수신 처리 오류:", e);
    }
  }

  function appendNotificationToDOM(notification) {
    // 1. 고객용 알림 목록 컨테이너 (#notification-container)
    const customerContainer = document.getElementById("notification-container");
    if (customerContainer) {
      const detailBtn = notification.targetUrl
        ? `<a class="btn" data-noti-id="${notification.id || ''}" href="${escapeHtmlNoti(notification.targetUrl)}">상세</a>`
        : "";

      const article = document.createElement("article");
      article.className = "notification-item is-unread";
      if (notification.id) article.setAttribute("data-id", notification.id);
      article.innerHTML = `
        <div class="cluster" style="align-items:flex-start">
          <span class="notification-dot" aria-hidden="true"></span>
          <div>
            <span class="badge">${escapeHtmlNoti(notification.title || '알림')}</span>
            <p>${escapeHtmlNoti(notification.content || '')}</p>
            <time class="text-muted">방금 전</time>
          </div>
        </div>
        ${detailBtn}
      `;

      if (customerContainer.querySelector("p.text-muted")) {
        customerContainer.innerHTML = "";
      }

      if (customerContainer.firstChild) {
        customerContainer.insertBefore(article, customerContainer.firstChild);
      } else {
        customerContainer.appendChild(article);
      }
      return;
    }

    // 2. 관리자용 알림 목록 컨테이너 (#admin-notification-container)
    const adminContainer = document.getElementById("admin-notification-container");
    if (adminContainer) {
      const detailBtn = notification.targetUrl
        ? `<a class="btn btn--sm" data-noti-id="${notification.id || ''}" href="${escapeHtmlNoti(notification.targetUrl)}">상세</a>`
        : "";

      const tr = document.createElement("tr");
      tr.className = "admin-noti-row is-unread";
      if (notification.id) tr.setAttribute("data-id", notification.id);
      tr.style.cssText = "font-weight: 600; background-color: rgba(255, 243, 205, 0.2);";
      tr.innerHTML = `
        <td>방금 전</td>
        <td><span class="badge">${escapeHtmlNoti(notification.title || '알림')}</span></td>
        <td>${escapeHtmlNoti(notification.content || '')}</td>
        <td><span class="badge badge--info">미읽음</span></td>
        <td>${detailBtn}</td>
      `;

      if (adminContainer.querySelector("td.text-muted")) {
        adminContainer.innerHTML = "";
      }

      if (adminContainer.firstChild) {
        adminContainer.insertBefore(tr, adminContainer.firstChild);
      } else {
        adminContainer.appendChild(tr);
      }
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
