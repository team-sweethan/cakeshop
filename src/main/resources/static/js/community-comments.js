(function () {
  "use strict";

  const SECTION = "[data-comment-section]";

  let pending = null;
  let generation = 0;
  let autoLoadArmed = false;
  let observer = null;

  function section() {
    return document.querySelector(SECTION);
  }

  if (!section()) {
    return;
  }

  /* 링크가 하려던 일을 그대로 한다. 조각 로드가 실패하는 자리마다 여기로 떨어진다. */
  function fallback(pageUrl) {
    window.location.assign(pageUrl);
  }

  function swap(html, pageUrl, preserveScroll) {
    const current = section();

    if (!current) {
      fallback(pageUrl);
      return false;
    }

    const template = document.createElement("template");
    template.innerHTML = html.trim();

    const next = template.content.querySelector(SECTION);

    if (!next) {
      fallback(pageUrl);
      return false;
    }

    /*
     * 더 보기는 오래된 댓글을 위에 붙인다 — 문서가 위로 늘어난 만큼 스크롤을 내려 주지 않으면
     * 읽던 자리가 화면 밖으로 밀린다.
     */
    const heightBefore = document.documentElement.scrollHeight;

    current.replaceWith(next);

    if (preserveScroll) {
      window.scrollBy(0, document.documentElement.scrollHeight - heightBefore);
    }

    return true;
  }

  function load(pageUrl, options) {
    const current = section();

    if (!current) {
      return;
    }

    const sectionUrl = current.getAttribute("data-section-url");

    if (!sectionUrl) {
      fallback(pageUrl);
      return;
    }

    const queryAt = pageUrl.indexOf("?");
    const query = queryAt < 0 ? "" : pageUrl.slice(queryAt);

    /*
     * 나중에 시작한 탐색이 이긴다. 진행 중인 요청을 끊지 않고 새 요청을 막으면, 뒤로가기 도중
     * 도착한 옛 응답이 화면과 주소를 되돌려 놓아 뒤로가기가 취소된 것처럼 보인다.
     */
    if (pending) {
      pending.abort();
    }

    const controller = window.AbortController ? new window.AbortController() : null;
    const ticket = generation + 1;

    generation = ticket;
    pending = controller;

    window.fetch(sectionUrl + query, {
      credentials: "same-origin",
      signal: controller ? controller.signal : undefined
    })
        .then(function (response) {
          if (!response.ok) {
            throw new Error("comment section request failed");
          }

          return response.text();
        })
        .then(function (html) {
          /* 끊지 못한 옛 응답도 여기서 버린다 — AbortController 가 없는 브라우저의 안전망이다. */
          if (ticket !== generation) {
            return;
          }

          if (!swap(html, pageUrl, options.preserveScroll)) {
            return;
          }

          if (options.push) {
            window.history.pushState({ commentSection: true }, "", pageUrl);
          }

          observeMore();
        })
        .catch(function (error) {
          if (ticket !== generation || (error && error.name === "AbortError")) {
            return;
          }

          fallback(pageUrl);
        });
  }

  /*
   * 쓰다 만 댓글·답글이 있으면 자동 로드는 하지 않는다. 구역을 통째로 갈아끼우므로 입력 칸도
   * 함께 새 것이 되는데, 스크롤만 했을 뿐인 사용자에게 그것은 예고 없는 삭제다.
   *
   * <p>링크를 직접 누르는 경우는 막지 않는다 — 스크립트가 없었어도 그 링크는 페이지를 옮겨
   * 같은 내용을 지웠다. 자동 로드만 <b>링크에 없던 새 손실</b>이라 여기서만 멈춘다.
   */
  function hasDraft() {
    const current = section();

    if (!current) {
      return false;
    }

    return Array.prototype.some.call(
        current.querySelectorAll("textarea"),
        function (field) {
          return field.value.trim() !== "";
        }
    );
  }

  /*
   * 자동 로드는 첫 스크롤 뒤에만 켠다. 더 보기 링크가 댓글 구역 맨 위에 있어서, 무장해 두면
   * 짧은 글에서는 사용자가 스크롤하기도 전에 상한(200)까지 저절로 불러온다.
   */
  function observeMore() {
    if (!autoLoadArmed || !observer) {
      return;
    }

    const more = document.querySelector(SECTION + " [data-comment-more]");

    if (more) {
      observer.observe(more);
    }
  }

  if (window.IntersectionObserver) {
    observer = new window.IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        /* 관찰은 놓지 않는다 — 입력을 비우고 다시 지나가면 그때 이어 붙는다. */
        if (!entry.isIntersecting || hasDraft()) {
          return;
        }

        observer.unobserve(entry.target);
        load(entry.target.getAttribute("href"), { push: true, preserveScroll: true });
      });
    });

    window.addEventListener("scroll", function () {
      autoLoadArmed = true;
      observeMore();
    }, { once: true, passive: true });
  }

  /*
   * 구역이 통째로 갈리므로 이벤트는 document 에 맡긴다. 새 마크업에 다시 붙일 것이 없다.
   * 새 탭·다운로드 같은 브라우저 기본 동작은 가로채지 않는다.
   */
  document.addEventListener("click", function (event) {
    if (event.defaultPrevented || event.button !== 0) {
      return;
    }

    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
      return;
    }

    const link = event.target.closest("[data-comment-link]");
    const current = section();

    if (!link || !current || !current.contains(link) || !link.getAttribute("href")) {
      return;
    }

    event.preventDefault();

    load(link.getAttribute("href"), {
      push: true,
      preserveScroll: link.hasAttribute("data-comment-more")
    });
  });

  /* 뒤로가기로 돌아온 주소도 조각으로 맞춘다 — 주소와 화면이 갈리면 안 된다. */
  window.addEventListener("popstate", function () {
    if (section()) {
      load(window.location.href, { push: false, preserveScroll: false });
    }
  });
})();
