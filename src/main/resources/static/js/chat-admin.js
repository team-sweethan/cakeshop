/**
 * 관리자용 1:1 동적 채팅 대시보드 REST API 연동 스크립트
 */
document.addEventListener("DOMContentLoaded", () => {
  let selectedChatRoomId = null;
  let selectedCustomerId = null;
  let currentFilter = "all";
  let adminRoomsData = [];
  let pendingAttachment = null;
  let lastFetchedMessageId = 0;

  const adminRoomListContainer = document.getElementById("adminChatRoomList");
  const adminChatMessagesContainer = document.getElementById("adminChatMessages");
  let adminChatForm = document.getElementById("adminChatForm");
  const adminCustomerNote = document.getElementById("adminCustomerNote");
  const saveAdminNoteBtn = document.getElementById("saveAdminNoteBtn");
  const adminSearchInput = document.getElementById("adminChatSearch");

  // 목업 스크립트(admin-mockup.js) 이벤트 간섭 전면 차단을 위한 폼 클로닝
  if (adminChatForm) {
    const freshForm = adminChatForm.cloneNode(true);
    adminChatForm.parentNode.replaceChild(freshForm, adminChatForm);
    adminChatForm = freshForm;
  }

  // 폼 클로닝 후 빠른 답변 드롭다운 다시 조회 및 리스너 등록
  const quickTemplateSelect = document.getElementById("quickTemplateSelect");
  if (quickTemplateSelect) {
    quickTemplateSelect.addEventListener("change", () => {
      const val = quickTemplateSelect.value;
      const inputEl = document.getElementById("adminChatInput");
      if (val && inputEl) {
        inputEl.value = val;
      }
    });
  }

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

  // 1. 관리자 전체 채팅방 목록 서버 필터 조회 (완료 탭: RESOLVED)
  async function loadAdminRooms() {
    try {
      let queryUrl = "/api/admin/chat/rooms";
      if (currentFilter === "unread") {
        queryUrl += "?status=WAITING_ADMIN";
      } else if (currentFilter === "done") {
        queryUrl += "?status=RESOLVED";
      }

      const response = await fetch(queryUrl);
      if (!response.ok) {
        if (adminRoomListContainer) {
          adminRoomListContainer.innerHTML = `<div class="text-muted" style="font-size:12px; padding:10px;">채팅방 목록을 불러올 수 없습니다.</div>`;
        }
        clearMainAndSidePanel();
        return;
      }

      adminRoomsData = await response.json();
      renderRoomList();

      // 방이 존재할 때 첫 번째 방 선택, 없으면 목업 영역 완전 비우기
      if (adminRoomsData && adminRoomsData.length > 0) {
        if (!selectedChatRoomId) {
          const firstRoom = adminRoomsData[0];
          const rId = firstRoom.chatRoomId || firstRoom.id;
          selectChatRoom(rId, firstRoom.customerId);
        }
      } else {
        clearMainAndSidePanel();
      }

    } catch (err) {
      console.error("관리자 방 목록 조회 실패:", err);
      clearMainAndSidePanel();
    }
  }

  // 메인 및 사이드 패널 초기화 (방이 없을 때 목업 잔재 지우기)
  function clearMainAndSidePanel() {
    selectedChatRoomId = null;
    selectedCustomerId = null;

    if (adminChatMessagesContainer) {
      adminChatMessagesContainer.innerHTML = `<div class="text-muted" style="text-align:center; padding:40px;">대화방을 선택하거나 활성화된 문의 내역이 없습니다.</div>`;
    }

    const infoPanel = document.querySelector(".admin-chat-info-panel");
    if (infoPanel) {
      const existingCards = infoPanel.querySelectorAll(".admin-order-item");
      existingCards.forEach((card) => card.remove());
    }

    const memoEl = document.getElementById("adminCustomerNote");
    if (memoEl) {
      memoEl.value = "";
    }

    const headerTitle = document.querySelector(".admin-chat-main-room .chat-room__header strong");
    if (headerTitle) {
      headerTitle.textContent = "1:1 채팅 상담";
    }
  }

  // 방 목록 렌더링 (검색어 필터 시 선택 방 지우지 않음)
  function renderRoomList() {
    if (!adminRoomListContainer) return;
    adminRoomListContainer.innerHTML = "";

    const searchKeyword = adminSearchInput ? adminSearchInput.value.trim().toLowerCase() : "";

    const filteredRooms = adminRoomsData.filter((room) => {
      if (searchKeyword) {
        const cName = (room.customerName || "").toLowerCase();
        const oNum = (room.orderNumber || room.orderNo || "").toLowerCase();
        if (!cName.includes(searchKeyword) && !oNum.includes(searchKeyword)) {
          return false;
        }
      }
      return true;
    });

    if (filteredRooms.length === 0) {
      adminRoomListContainer.innerHTML = `<p class="text-muted" style="font-size:12px; padding:10px;">해당하는 채팅방이 없습니다.</p>`;
      // 검색어가 없을 때만 전체 초기화, 검색 중일 때는 선택된 방 및 대화 내용 유지
      if (!searchKeyword) {
        clearMainAndSidePanel();
      }
      return;
    }

    filteredRooms.forEach((room) => {
      const rId = room.chatRoomId || room.id;
      const isSelected = rId === selectedChatRoomId;
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

      const msgTime = room.lastMessageCreatedAt || room.lastMessageTime;

      itemDiv.innerHTML = `
        <div class="cluster cluster--between" style="margin-bottom:4px;">
          <strong>${escapeHtml(room.customerName || `고객 #${room.customerId}`)}</strong>
          ${statusBadge}
        </div>
        <p class="text-muted" style="margin:0; font-size:12px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
          ${escapeHtml(room.lastMessageContent || "대화 내용 없음")}
        </p>
        <div class="cluster cluster--between" style="margin-top:6px; font-size:11px;">
          <span class="text-muted">${msgTime ? formatTime(msgTime) : ""}</span>
          ${unreadBadge}
        </div>
      `;

      itemDiv.addEventListener("click", () => {
        selectChatRoom(rId, room.customerId);
      });

      adminRoomListContainer.appendChild(itemDiv);
    });
  }

  // 방 선택 조작, 헤더 고객명 동적 갱신 및 읽음 처리 시 목록 배지 실시간 갱신
  async function selectChatRoom(roomId, customerId) {
    if (!roomId) return;
    selectedChatRoomId = roomId;
    selectedCustomerId = customerId;

    // 대화창 상단 헤더 고객명 동적 갱신
    const headerTitle = document.querySelector(".admin-chat-main-room .chat-room__header strong");
    const targetRoom = adminRoomsData.find((r) => (r.chatRoomId || r.id) === roomId);
    if (headerTitle) {
      const cName = targetRoom ? targetRoom.customerName : `고객 #${customerId}`;
      headerTitle.textContent = `${cName} 님과의 1:1 상담`;
    }

    renderRoomList(); // 선택 하이라이트 갱신

    await loadAdminMessages(roomId);
    await loadAdminSidePanel(roomId);
    if (lastFetchedMessageId > 0) {
      await markRead(roomId, lastFetchedMessageId);
      // 읽음 완료 시 로컬 메모리 방의 unreadCount 0 갱신 및 배지 리렌더링
      if (targetRoom) {
        targetRoom.unreadCount = 0;
        renderRoomList();
      }
    }
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

    lastFetchedMessageId = messages[messages.length - 1].id;

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

      // 고객 특이사항 메모 세팅 (note.content 읽기)
      const inputMemo = document.getElementById("adminCustomerNote");
      if (inputMemo) {
        inputMemo.value = data.note ? (data.note.content || "") : "";
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

          const oNum = ord.orderNumber || ord.orderNo || `주문 #${ord.orderId}`;
          const pName = ord.productName || ord.productSummary || "주문 제작 케이크";
          const pTime = ord.pickupDateTime || ord.pickupAt || "-";

          cardDiv.innerHTML = `
            <div class="cluster cluster--between" style="margin-bottom:6px;">
              <strong>${escapeHtml(oNum)}</strong>
              <span class="badge badge--warning">${escapeHtml(ord.orderStatus || "접수")}</span>
            </div>
            <p style="margin:0; font-weight:600;">${escapeHtml(pName)}</p>
            <p class="text-muted" style="font-size:12px; margin:2px 0;">픽업: ${escapeHtml(typeof pTime === "string" ? pTime : formatTime(pTime))}</p>
            <p style="font-size:13px; font-weight:700; margin-top:4px;">금액: ${ord.totalAmount ? ord.totalAmount.toLocaleString() + "원" : "-"}</p>
            <a class="btn btn--outline btn--block btn--xs" href="/admin/orders/${ord.orderId}" style="margin-top:8px;">주문 상세서 보기</a>
          `;

          const targetNote = document.getElementById("adminCustomerNote");
          if (targetNote && targetNote.parentElement) {
            infoPanel.insertBefore(cardDiv, targetNote.parentElement);
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

      const inputEl = document.getElementById("adminChatInput");
      const contentText = inputEl ? inputEl.value.trim() : "";
      if (!contentText && !pendingAttachment) {
        alert("메시지 내용 또는 이미지를 첨부해주세요.");
        return;
      }

      const payload = {
        chatRoomId: selectedChatRoomId,
        content: contentText,
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
          alert("답변 메시지 전송에 실패했습니다.");
          return;
        }

        const sentMsg = await response.json();
        lastFetchedMessageId = sentMsg.id;

        // 폼 초기화
        if (inputEl) inputEl.value = "";
        pendingAttachment = null;
        const fileLabel = document.getElementById("adminImageFileName");
        if (fileLabel) fileLabel.textContent = "선택된 파일 없음";
        const fileInput = document.getElementById("adminChatImageInput");
        if (fileInput) fileInput.value = "";

        // 재조회 및 스크롤
        await loadAdminMessages(selectedChatRoomId);
        await loadAdminRooms(); // 방 목록 미답변 상태 갱신
        await markRead(selectedChatRoomId, sentMsg.id);

      } catch (err) {
        console.error("관리자 답장 전송 오류:", err);
        alert("답변 전송 처리 중 오류가 발생했습니다.");
      }
    });
  }

  // 5. 관리자 고객 메모 저장 ({ content: noteText } 수신)
  if (saveAdminNoteBtn) {
    saveAdminNoteBtn.addEventListener("click", async () => {
      if (!selectedCustomerId) {
        alert("선택된 고객 정보가 없습니다.");
        return;
      }

      const memoEl = document.getElementById("adminCustomerNote");
      const noteText = memoEl ? memoEl.value.trim() : "";

      try {
        const response = await fetch(`/api/admin/chat/customers/${selectedCustomerId}/note`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...getCsrfHeaders()
          },
          body: JSON.stringify({ content: noteText })
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

  // 6. 이미지 업로드 (실패 시 보류 첨부 초기화)
  const fileInputEl = document.getElementById("adminChatImageInput");
  if (fileInputEl) {
    fileInputEl.addEventListener("change", async () => {
      const file = fileInputEl.files[0];
      if (!file) return;

      const fileLabel = document.getElementById("adminImageFileName");
      if (fileLabel) fileLabel.textContent = file.name;

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
          pendingAttachment = null;
          if (fileLabel) fileLabel.textContent = "선택된 파일 없음";
          if (fileInputEl) fileInputEl.value = "";
          return;
        }

        const objectKey = await response.text();
        pendingAttachment = {
          objectKey: objectKey,
          originalFilename: file.name,
          contentType: file.type || "image/jpeg",
          fileSize: file.size
        };
        if (fileLabel) fileLabel.textContent = `✔ ${file.name} (첨부 완료)`;

      } catch (err) {
        console.error("이미지 업로드 에러:", err);
        pendingAttachment = null;
        if (fileLabel) fileLabel.textContent = "선택된 파일 없음";
        if (fileInputEl) fileInputEl.value = "";
      }
    });
  }

  // 7. 탭 필터링 조작 (서버 필터 재조회)
  const filterBtns = document.querySelectorAll("[data-admin-filter]");
  filterBtns.forEach((btn) => {
    btn.addEventListener("click", async () => {
      filterBtns.forEach((b) => b.classList.remove("is-active"));
      btn.classList.add("is-active");
      currentFilter = btn.getAttribute("data-admin-filter");
      await loadAdminRooms();
    });
  });

  // 8. 검색어 실시간 필터링
  if (adminSearchInput) {
    adminSearchInput.addEventListener("input", () => {
      renderRoomList();
    });
  }

  // 읽음 커서 갱신 (쿼리 파라미터 전달)
  async function markRead(roomId, lastMsgId) {
    if (!roomId || !lastMsgId) return;
    try {
      await fetch(`/api/chat/read-cursor?chatRoomId=${roomId}&lastReadMessageId=${lastMsgId}`, {
        method: "PATCH",
        headers: getCsrfHeaders()
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
