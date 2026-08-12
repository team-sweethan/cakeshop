/**
 * 관리자용 1:1 동적 채팅 대시보드 REST API 연동 스크립트
 */
document.addEventListener("DOMContentLoaded", () => {
  let selectedChatRoomId = null;
  let selectedCustomerId = null;
  let currentFilter = "all";
  let adminRoomsData = [];
  let pendingAttachmentKey = null;

  const adminRoomListContainer = document.getElementById("adminChatRoomList");
  const adminChatMessagesContainer = document.getElementById("adminChatMessages");
  const adminChatForm = document.getElementById("adminChatForm");
  const adminChatInput = document.getElementById("adminChatInput");
  const adminChatImageInput = document.getElementById("adminChatImageInput");
  const adminImageFileName = document.getElementById("adminImageFileName");
  const quickTemplateSelect = document.getElementById("quickTemplateSelect");
  const adminCustomerNote = document.getElementById("adminCustomerNote");
  const saveAdminNoteBtn = document.getElementById("saveAdminNoteBtn");
  const adminSearchInput = document.getElementById("adminChatSearch");

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

  // 1. 관리자 전체 채팅방 목록 조회
  async function loadAdminRooms() {
    try {
      const response = await fetch("/api/admin/chat/rooms");
      if (!response.ok) {
        if (adminRoomListContainer) {
          adminRoomListContainer.innerHTML = `<div class="text-muted" style="font-size:12px; padding:10px;">채팅방 목록을 불러올 수 없습니다.</div>`;
        }
        return;
      }

      adminRoomsData = await response.json();
      renderRoomList();

      // 첫 번째 방 자동 선택
      if (adminRoomsData && adminRoomsData.length > 0 && !selectedChatRoomId) {
        selectChatRoom(adminRoomsData[0].id, adminRoomsData[0].customerId);
      }

    } catch (err) {
      console.error("관리자 방 목록 조회 실패:", err);
    }
  }

  // 방 목록 렌더링
  function renderRoomList() {
    if (!adminRoomListContainer) return;
    adminRoomListContainer.innerHTML = "";

    const searchKeyword = adminSearchInput ? adminSearchInput.value.trim().toLowerCase() : "";

    const filteredRooms = adminRoomsData.filter((room) => {
      // 탭 필터링
      if (currentFilter === "unread" && room.responseStatus !== "WAITING_ADMIN") return false;
      if (currentFilter === "done" && room.responseStatus === "WAITING_ADMIN") return false;

      // 검색어 필터링
      if (searchKeyword) {
        const cName = (room.customerName || "").toLowerCase();
        if (!cName.includes(searchKeyword)) return false;
      }

      return true;
    });

    if (filteredRooms.length === 0) {
      adminRoomListContainer.innerHTML = `<p class="text-muted" style="font-size:12px; padding:10px;">해당하는 채팅방이 없습니다.</p>`;
      return;
    }

    filteredRooms.forEach((room) => {
      const isSelected = room.id === selectedChatRoomId;
      const itemDiv = document.createElement("div");
      itemDiv.className = `panel admin-chat-room-item ${isSelected ? "is-selected" : ""}`;
      itemDiv.style.cssText = "cursor:pointer; padding:10px; margin-bottom:8px;";

      const isWaitingAdmin = room.responseStatus === "WAITING_ADMIN";
      const statusBadge = isWaitingAdmin
        ? `<span class="badge badge--danger">답변 대기</span>`
        : `<span class="badge badge--info">답변 완료</span>`;

      const unreadBadge = room.unreadCount > 0
        ? `<span class="badge badge--danger">${room.unreadCount}</span>`
        : "";

      itemDiv.innerHTML = `
        <div class="cluster cluster--between" style="margin-bottom:4px;">
          <strong>${escapeHtml(room.customerName || `고객 #${room.customerId}`)}</strong>
          ${statusBadge}
        </div>
        <p class="text-muted" style="margin:0; font-size:12px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
          ${escapeHtml(room.lastMessageContent || "대화 내용 없음")}
        </p>
        <div class="cluster cluster--between" style="margin-top:6px; font-size:11px;">
          <span class="text-muted">${room.lastMessageTime ? formatTime(room.lastMessageTime) : ""}</span>
          ${unreadBadge}
        </div>
      `;

      itemDiv.addEventListener("click", () => {
        selectChatRoom(room.id, room.customerId);
      });

      adminRoomListContainer.appendChild(itemDiv);
    });
  }

  // 방 선택 조작
  async function selectChatRoom(roomId, customerId) {
    selectedChatRoomId = roomId;
    selectedCustomerId = customerId;

    renderRoomList(); // 선택 하이라이트 갱신

    await loadAdminMessages(roomId);
    await loadAdminSidePanel(roomId);
    await markRead(roomId);
  }

  // 2. 대화 타임라인 렌더링
  async function loadAdminMessages(roomId) {
    if (!adminChatMessagesContainer) return;
    try {
      const response = await fetch(`/api/chat/messages?chatRoomId=${roomId}&page=1&size=50`);
      if (!response.ok) return;
      const messages = await response.json();
      renderAdminTimeline(messages);
    } catch (err) {
      console.error("관리자 대화 내역 조회 실패:", err);
    }
  }

  function renderAdminTimeline(messages) {
    if (!adminChatMessagesContainer) return;
    adminChatMessagesContainer.innerHTML = "";

    if (!messages || messages.length === 0) {
      adminChatMessagesContainer.innerHTML = `<div class="text-muted" style="text-align:center; padding:30px;">대화 기록이 없습니다.</div>`;
      return;
    }

    messages.forEach((msg) => {
      const isAdminSender = msg.senderType === "ADMIN";
      const msgDiv = document.createElement("div");
      msgDiv.className = `chat-msg ${isAdminSender ? "chat-msg--me" : "chat-msg--other"}`;

      const formattedTime = msg.createdAt ? formatTime(msg.createdAt) : "";

      let attachmentsHtml = "";
      if (msg.imageUrls && msg.imageUrls.length > 0) {
        msg.imageUrls.forEach((url) => {
          attachmentsHtml += `<div style="margin-bottom:6px;"><img src="${escapeHtml(url)}" style="max-width:200px; border-radius:8px;" alt="첨부 이미지"/></div>`;
        });
      }

      let productTagHtml = "";
      if (msg.productId) {
        const pName = msg.productName ? escapeHtml(msg.productName) : `상품 #${msg.productId}`;
        productTagHtml = `<div style="font-size:12px; margin-bottom:4px; opacity:0.9;"><span class="badge badge--info">🛒 문의 상품: ${pName}</span></div>`;
      }

      if (isAdminSender) {
        msgDiv.innerHTML = `
          <div class="chat-msg__body">
            ${productTagHtml}
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
          <div class="chat-msg__sender">${escapeHtml(msg.senderName || "고객")}</div>
          <div class="chat-msg--other__content-wrap">
            <div class="chat-msg__body">
              ${productTagHtml}
              ${attachmentsHtml}
              <div class="chat-msg__content">${escapeHtml(msg.content || "")}</div>
            </div>
            <div class="chat-msg__meta">
              <span class="chat-msg__time">${formattedTime}</span>
            </div>
          </div>
        `;
      }

      adminChatMessagesContainer.appendChild(msgDiv);
    });

    scrollToBottom();
  }

  // 3. 우측 사이드 패널 (메모 + 연동 주문) 렌더링
  async function loadAdminSidePanel(roomId) {
    try {
      const response = await fetch(`/api/admin/chat/rooms/${roomId}/side-panel`);
      if (!response.ok) return;
      const data = await response.json();

      // 고객 특이사항 메모 세팅
      if (adminCustomerNote) {
        adminCustomerNote.value = data.customerNote || "";
      }

      // 연동 주문 목록 렌더링
      const infoPanel = document.querySelector(".admin-chat-info-panel");
      if (!infoPanel) return;

      // 기존 주문 카드 부분 업데이트
      const existingCards = infoPanel.querySelectorAll(".admin-order-item");
      existingCards.forEach((card) => card.remove());

      if (data.orders && data.orders.length > 0) {
        data.orders.forEach((ord) => {
          const cardDiv = document.createElement("div");
          cardDiv.className = "panel section admin-order-item";
          cardDiv.style.cssText = "cursor:pointer; padding:10px; margin-bottom:12px; background:var(--color-background-soft);";

          cardDiv.innerHTML = `
            <div class="cluster cluster--between" style="margin-bottom:6px;">
              <strong>${escapeHtml(ord.orderNo || `주문 #${ord.orderId}`)}</strong>
              <span class="badge badge--warning">${escapeHtml(ord.orderStatus || "접수")}</span>
            </div>
            <p style="margin:0; font-weight:600;">${escapeHtml(ord.productSummary || "주문 제작 케이크")}</p>
            <p class="text-muted" style="font-size:12px; margin:2px 0;">픽업: ${ord.pickupDatetime || "-"}</p>
            <p style="font-size:13px; font-weight:700; margin-top:4px;">금액: ${ord.totalAmount ? ord.totalAmount.toLocaleString() + "원" : "-"}</p>
            <a class="btn btn--outline btn--block btn--xs" href="/admin/orders/${ord.orderId}" style="margin-top:8px;">주문 상세서 보기</a>
          `;

          if (adminCustomerNote && adminCustomerNote.parentElement) {
            infoPanel.insertBefore(cardDiv, adminCustomerNote.parentElement);
          }
        });
      }

    } catch (err) {
      console.error("우측 사이드 패널 조회 실패:", err);
    }
  }

  // 4. 답변 메시지 전송
  if (adminChatForm) {
    adminChatForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      if (!selectedChatRoomId) {
        alert("선택된 채팅방이 없습니다.");
        return;
      }

      const contentText = adminChatInput.value.trim();
      if (!contentText && !pendingAttachmentKey) {
        alert("메시지 내용 또는 이미지를 첨부해주세요.");
        return;
      }

      const payload = {
        chatRoomId: selectedChatRoomId,
        content: contentText,
        attachments: pendingAttachmentKey ? [{ objectKey: pendingAttachmentKey }] : []
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
          alert("답변 메시지 전송에 실패했습니다.");
          return;
        }

        const sentMsg = await response.json();

        // 폼 초기화
        adminChatInput.value = "";
        pendingAttachmentKey = null;
        if (adminImageFileName) adminImageFileName.textContent = "선택된 파일 없음";
        if (adminChatImageInput) adminChatImageInput.value = "";

        // 재조회 및 스크롤
        await loadAdminMessages(selectedChatRoomId);
        await loadAdminRooms(); // 방 목록 미답변 상태 갱신

      } catch (err) {
        console.error("관리자 답장 전송 오류:", err);
        alert("답변 전송 처리 중 오류가 발생했습니다.");
      }
    });
  }

  // 5. 관리자 고객 메모 저장
  if (saveAdminNoteBtn) {
    saveAdminNoteBtn.addEventListener("click", async () => {
      if (!selectedCustomerId) {
        alert("선택된 고객 정보가 없습니다.");
        return;
      }

      const noteText = adminCustomerNote ? adminCustomerNote.value.trim() : "";

      try {
        const response = await fetch(`/api/admin/chat/customers/${selectedCustomerId}/note`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...getCsrfHeaders()
          },
          body: JSON.stringify({ noteContent: noteText })
        });

        if (!response.ok) {
          alert("메모 저장에 실패했습니다.");
          return;
        }

        alert("고객 특이사항 메모가 성공적으로 저장되었습니다!");

      } catch (err) {
        console.error("메모 저장 오류:", err);
        alert("메모 저장 중 오류가 발생했습니다.");
      }
    });
  }

  // 6. 이미지 업로드
  if (adminChatImageInput) {
    adminChatImageInput.addEventListener("change", async () => {
      const file = adminChatImageInput.files[0];
      if (!file) return;

      if (adminImageFileName) adminImageFileName.textContent = file.name;

      const formData = new FormData();
      formData.append("file", file);

      try {
        const response = await fetch("/api/chat/images", {
          method: "POST",
          headers: getCsrfHeaders(),
          body: formData
        });

        if (!response.ok) {
          alert("이미지 업로드 실패");
          return;
        }

        pendingAttachmentKey = await response.text();
        if (adminImageFileName) adminImageFileName.textContent = `✔ ${file.name} (첨부 완료)`;

      } catch (err) {
        console.error("이미지 업로드 에러:", err);
      }
    });
  }

  // 7. 빠른 답변 템플릿 드롭다운 선택
  if (quickTemplateSelect) {
    quickTemplateSelect.addEventListener("change", () => {
      const val = quickTemplateSelect.value;
      if (val && adminChatInput) {
        adminChatInput.value = val;
      }
    });
  }

  // 8. 탭 필터링 조작
  const filterBtns = document.querySelectorAll("[data-admin-filter]");
  filterBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      filterBtns.forEach((b) => b.classList.remove("is-active"));
      btn.classList.add("is-active");
      currentFilter = btn.getAttribute("data-admin-filter");
      renderRoomList();
    });
  });

  // 9. 검색어 실시간 필터링
  if (adminSearchInput) {
    adminSearchInput.addEventListener("input", () => {
      renderRoomList();
    });
  }

  // 읽음 커서 갱신
  async function markRead(roomId) {
    try {
      await fetch("/api/chat/read-cursor", {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          ...getCsrfHeaders()
        },
        body: JSON.stringify({ chatRoomId: roomId })
      });
    } catch (err) {
      console.error("읽음 처리 실패:", err);
    }
  }

  function scrollToBottom() {
    if (adminChatMessagesContainer) {
      adminChatMessagesContainer.scrollTop = adminChatMessagesContainer.scrollHeight;
    }
  }

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
  loadAdminRooms();
});
