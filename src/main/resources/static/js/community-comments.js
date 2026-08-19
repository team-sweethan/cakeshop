(function () {
  "use strict";

  const SECTION = "[data-comment-section]";

  let loading = false;
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

    if (loading || !current) {
      return;
    }

    const sectionUrl = current.getAttribute("data-section-url");

    if (!sectionUrl) {
      fallback(pageUrl);
      return;
    }

    const queryAt = pageUrl.indexOf("?");
    const query = queryAt < 0 ? "" : pageUrl.slice(queryAt);

    loading = true;

    window.fetch(sectionUrl + query, { credentials: "same-origin" })
        .then(function (response) {
          if (!response.ok) {
            throw new Error("comment section request failed");
          }

          return response.text();
        })
        .then(function (html) {
          if (!swap(html, pageUrl, options.preserveScroll)) {
            return;
          }

          if (options.push) {
            window.history.pushState({ commentSection: true }, "", pageUrl);
          }

          observeMore();
        })
        .catch(function () {
          fallback(pageUrl);
        })
        .finally(function () {
          loading = false;
        });
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
        if (!entry.isIntersecting) {
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
