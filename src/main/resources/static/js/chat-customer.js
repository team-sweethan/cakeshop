/**
 * 고객용 1:1 동적 채팅 REST API 연동 스크립트
 */
document.addEventListener("DOMContentLoaded", () => {
  let currentChatRoomId = null;
  let pendingAttachment = null;
  let lastFetchedMessageId = 0;

  // URL QueryString에서 productId 파라미터 추출
  const urlParams = new URLSearchParams(window.location.search);
  const currentProductId = urlParams.get("productId") ? parseInt(urlParams.get("productId"), 10) : null;

  const chatMessagesContainer = document.getElementById("chatMessages");
  const chatForm = document.getElementById("chatForm");
  const chatInput = document.getElementById("chatInput");
  const chatImageInput = document.getElementById("chatImageInput");
  const imageFileName = document.getElementById("imageFileName");
  const orderSidebarStack = document.querySelector(".chat-sidebar .stack");

  // CSRF 메타 태그 획득 헬퍼
  function getCsrfHeaders() {
    const tokenMeta = document.querySelector('meta[name="_csrf"]');
    const headerMeta = document.querySelector('meta[name="_csrf_header"]');
    const headers = {};
    if (tokenMeta && headerMeta && tokenMeta.content && headerMeta.content) {
      headers[headerMeta.content] = tokenMeta.content;
    }
    return headers;
  }

  // 1. 방 정보 획득 (GET 없으면 404 후 자동으로 POST 생성)
  async function initChatRoom() {
    try {
      let response = await fetch("/api/chat/room");
      if (response.status === 404) {
        // 방이 없으면 POST 생성 요청
        response = await fetch("/api/chat/room", {
          method: "POST",
          headers: getCsrfHeaders()
        });
      }
      if (!response.ok) {
        throw new Error("채팅방을 불러올 수 없습니다.");
      }
      const room = await response.json();
      currentChatRoomId = room.id;

      // 대화 목록 및 연동 주문 조회
      await loadMessages(currentChatRoomId);
      await loadOrderBanners(currentChatRoomId);
      if (lastFetchedMessageId > 0) {
        await updateReadCursor(currentChatRoomId, lastFetchedMessageId);
      }

    } catch (err) {
      console.error(err);
      if (chatMessagesContainer) {
        chatMessagesContainer.innerHTML = `<div class="text-muted" style="text-align:center; padding:20px;">채팅방 연결 중 오류가 발생했습니다.</div>`;
      }
    }
  }

  // 2. 대화 메시지 목록 조회 & 타임라인 렌더링
  async function loadMessages(roomId) {
    try {
      const response = await fetch(`/api/chat/messages?chatRoomId=${roomId}&page=1&size=50`);
      if (!response.ok) return;
      const messages = await response.json();
      renderTimeline(messages);
    } catch (err) {
      console.error("메시지 조회 실패:", err);
    }
  }

  // 타임라인 HTML 렌더링
  function renderTimeline(messages) {
    if (!chatMessagesContainer) return;
    chatMessagesContainer.innerHTML = "";

    if (!messages || messages.length === 0) {
      chatMessagesContainer.innerHTML = `<div class="text-muted empty-chat-notice" style="text-align:center; padding:30px;">아직 주고받은 메시지가 없습니다. 문의사항을 남겨보세요!</div>`;
      return;
    }

    lastFetchedMessageId = messages[messages.length - 1].id;

    messages.forEach((msg) => {
      appendProductBannerDOM(msg, chatMessagesContainer);
      const msgEl = createMessageDOM(msg);
      chatMessagesContainer.appendChild(msgEl);
    });

    scrollToBottom();
  }

  // 문의 상품 중앙 시스템 배너 카드 생성 헬퍼
  function appendProductBannerDOM(msg, container) {
    if (!msg || !msg.productId || !container) return;
    const pName = msg.productName ? escapeHtml(msg.productName) : `상품 #${msg.productId}`;
    const bannerDiv = document.createElement("div");
    bannerDiv.className = "chat-msg chat-msg--system";
    bannerDiv.style.cssText = "margin: 14px 0 8px 0; text-align: center;";
    bannerDiv.innerHTML = `
      <div class="chat-msg__content" style="display: inline-block; background: #fff8eb; border: 1px solid #ffe0b2; border-radius: 20px; padding: 6px 16px; font-size: 12px; color: #e65100; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
        <strong>[문의 상품]: </strong> <span style="font-weight: 700;">${pName}</span>
      </div>
    `;
    container.appendChild(bannerDiv);
  }

  // 메시지 단일 DOM 생성
  function createMessageDOM(msg) {
    const isMe = msg.senderType === "CUSTOMER";
    const msgDiv = document.createElement("div");
    msgDiv.className = `chat-msg ${isMe ? "chat-msg--me" : "chat-msg--other"}`;

    const formattedTime = msg.createdAt ? formatTime(msg.createdAt) : "";

    let attachmentsHtml = "";
    if (msg.imageUrls && msg.imageUrls.length > 0) {
      msg.imageUrls.forEach((url) => {
        attachmentsHtml += `<div style="margin-bottom:6px;"><img src="${escapeHtml(url)}" style="max-width:200px; border-radius:8px;" alt="첨부 이미지"/></div>`;
      });
    }

    if (isMe) {
      msgDiv.innerHTML = `
        <div class="chat-msg__body">
          ${attachmentsHtml}
          <div class="chat-msg__content">${escapeHtml(msg.content || "")}</div>
        </div>
        <div class="chat-msg__meta">
          <span class="chat-msg__read">${msg.isRead ? "읽음" : "미읽음"}</span>
          <span class="chat-msg__time">${formattedTime}</span>
        </div>
      `;
    } else {
      msgDiv.innerHTML = `
        <div class="chat-msg__sender">${escapeHtml(msg.senderName || "관리자")}</div>
        <div class="chat-msg--other__content-wrap">
          <div class="chat-msg__body">
            ${attachmentsHtml}
            <div class="chat-msg__content">${escapeHtml(msg.content || "")}</div>
          </div>
          <div class="chat-msg__meta">
            <span class="chat-msg__time">${formattedTime}</span>
          </div>
        </div>
      `;
    }

    return msgDiv;
  }

  // 3. 연동 주문 배너 목록 조회 및 동적 헤더 건수 갱신
  async function loadOrderBanners(roomId) {
    if (!orderSidebarStack) return;
    try {
      const response = await fetch(`/api/chat/rooms/${roomId}/orders`);
      if (!response.ok) return;
      const orders = await response.json();
      renderOrderSidebar(orders);
    } catch (err) {
      console.error("연동 주문 조회 실패:", err);
    }
  }

  function renderOrderSidebar(orders) {
    if (!orderSidebarStack) return;
    orderSidebarStack.innerHTML = "";

    // 주문 개수 헤더 동적 갱신
    const sidebarTitle = document.querySelector(".chat-sidebar h3, .chat-sidebar .sidebar-title, .chat-sidebar strong");
    const count = orders ? orders.length : 0;
    if (sidebarTitle && sidebarTitle.textContent.includes("주문")) {
      sidebarTitle.textContent = `연동 주문 내역 (${count}건)`;
    }

    if (!orders || orders.length === 0) {
      orderSidebarStack.innerHTML = `<p class="text-muted" style="font-size:12px;">연동된 주문 내역이 없습니다.</p>`;
      return;
    }

    orders.forEach((ord) => {
      const itemDiv = document.createElement("div");
      itemDiv.className = "panel chat-order-item";
      itemDiv.style.cssText = "cursor:pointer; padding:10px; margin-bottom:8px;";

      const oNum = ord.orderNumber || ord.orderNo || `주문 #${ord.orderId}`;
      const pName = ord.productName || ord.productSummary || "케이크 주문건";
      const pTime = ord.pickupDateTime || ord.pickupAt || "-";
      const amtStr = (ord.totalAmount !== null && ord.totalAmount !== undefined)
        ? ord.totalAmount.toLocaleString() + "원"
        : "-";

      itemDiv.innerHTML = `
        <div class="cluster cluster--between" style="margin-bottom:4px;">
          <strong>${escapeHtml(oNum)}</strong>
          <span class="badge badge--warning">${escapeHtml(ord.orderStatus || "접수")}</span>
        </div>
        <p class="text-muted" style="margin:0;">${escapeHtml(pName)}</p>
        <p class="text-muted" style="font-size:11px;">금액: ${amtStr} | 픽업: ${escapeHtml(typeof pTime === "string" ? pTime : formatTime(pTime))}</p>
        <a class="btn btn--block" href="/orders/${ord.orderId}" style="margin-top:6px;">주문 상세 보기</a>
      `;
      itemDiv.addEventListener("click", (e) => {
        if (e.target.tagName === "A" || e.target.tagName === "BUTTON") return;
        const targetAnchor = document.getElementById(`msg-ord-${ord.orderId}`);
        if (targetAnchor) {
          targetAnchor.scrollIntoView({ behavior: "smooth", block: "center" });
        } else if (chatMessagesContainer) {
          chatMessagesContainer.scrollTop = chatMessagesContainer.scrollHeight;
        }
      });

      orderSidebarStack.appendChild(itemDiv);
    });
  }

  // Enter 키 전송 이벤트 연동 (Shift+Enter는 줄바꿈)
  if (chatInput && chatForm) {
    chatInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        chatForm.requestSubmit();
      }
    });
  }

  // 4. 메시지 전송
  if (chatForm) {
    chatForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      if (!currentChatRoomId) {
        alert("채팅방이 활성화되지 않았습니다.");
        return;
      }

      const contentText = chatInput.value.trim();
      if (!contentText && !pendingAttachment) {
        alert("메시지 내용 또는 이미지를 첨부해주세요.");
        return;
      }

      const payload = {
        chatRoomId: currentChatRoomId,
        content: contentText,
        productId: currentProductId,
        attachments: pendingAttachment ? [pendingAttachment] : []
      };

      try {
        const response = await fetch("/api/chat/messages", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...getCsrfHeaders()
          },
          body: JSON.stringify(payload)
        });

        if (!response.ok) {
          alert("메시지 전송에 실패했습니다.");
          return;
        }

        const sentMsg = await response.json();
        lastFetchedMessageId = sentMsg.id;

        // 폼 초기화
        chatInput.value = "";
        pendingAttachment = null;
        if (imageFileName) imageFileName.textContent = "선택된 파일 없음";
        if (chatImageInput) chatImageInput.value = "";

        // 첫 메시지 전송 시 기존 안내 문구 지우기
        if (chatMessagesContainer) {
          const noticeEl = chatMessagesContainer.querySelector(".empty-chat-notice");
          if (noticeEl) {
            chatMessagesContainer.innerHTML = "";
          }
          appendProductBannerDOM(sentMsg, chatMessagesContainer);
          const msgEl = createMessageDOM(sentMsg);
          chatMessagesContainer.appendChild(msgEl);
          scrollToBottom();
        }

        await updateReadCursor(currentChatRoomId, sentMsg.id);

      } catch (err) {
        console.error("전송 에러:", err);
        alert("메시지 전송 중 오류가 발생했습니다.");
      }
    });
  }

  // 5. 사진 업로드
  if (chatImageInput) {
    chatImageInput.addEventListener("change", async () => {
      const file = chatImageInput.files[0];
      if (!file) return;

      if (imageFileName) imageFileName.textContent = file.name;

      const formData = new FormData();
      formData.append("file", file);

      try {
        const response = await fetch("/api/chat/images", {
          method: "POST",
          headers: getCsrfHeaders(),
          body: formData
        });

        if (!response.ok) {
          alert("이미지 업로드에 실패했습니다. (5MB 이하 이미지 파일만 가능합니다)");
          pendingAttachment = null;
          if (imageFileName) imageFileName.textContent = "선택된 파일 없음";
          if (chatImageInput) chatImageInput.value = "";
          return;
        }

        const objectKey = await response.text();
        pendingAttachment = {
          objectKey: objectKey,
          originalFilename: file.name,
          contentType: file.type || "image/jpeg",
          fileSize: file.size
        };

        if (imageFileName) imageFileName.textContent = `✔ ${file.name} (첨부 준비 완료)`;

      } catch (err) {
        console.error("이미지 업로드 오류:", err);
        alert("이미지 업로드 처리 중 에러가 발생했습니다.");
        pendingAttachment = null;
        if (imageFileName) imageFileName.textContent = "선택된 파일 없음";
        if (chatImageInput) chatImageInput.value = "";
      }
    });
  }

  // 6. 읽음 커서 갱신 (쿼리 파라미터로 전달)
  async function updateReadCursor(roomId, lastMsgId) {
    if (!roomId || !lastMsgId) return;
    try {
      await fetch(`/api/chat/read-cursor?chatRoomId=${roomId}&lastReadMessageId=${lastMsgId}`, {
        method: "PATCH",
        headers: getCsrfHeaders()
      });
    } catch (err) {
      console.error("읽음 커서 갱신 실패:", err);
    }
  }

  // 스크롤 맨 아래로 이동
  function scrollToBottom() {
    if (chatMessagesContainer) {
      chatMessagesContainer.scrollTop = chatMessagesContainer.scrollHeight;
    }
  }

  // 헬퍼 함수
  function escapeHtml(str) {
    if (!str) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  function formatTime(isoString) {
    try {
      const date = new Date(isoString);
      const hours = String(date.getHours()).padStart(2, "0");
      const minutes = String(date.getMinutes()).padStart(2, "0");
      return `${hours}:${minutes}`;
    } catch (e) {
      return "";
    }
  }

  // 초기화 실행
  initChatRoom();
});
