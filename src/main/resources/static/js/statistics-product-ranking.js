(function () {
  "use strict";

  document.addEventListener("DOMContentLoaded", function () {
    const toggle = document.querySelector("[data-product-ranking-toggle]");
    if (!toggle) return;

    const overflowRows = document.querySelectorAll(
      '[data-product-ranking-overflow="true"]'
    );

    toggle.addEventListener("click", function () {
      const expanded = toggle.getAttribute("aria-expanded") === "true";
      const nextExpanded = !expanded;

      overflowRows.forEach(function (row) {
        row.hidden = !nextExpanded;
      });
      toggle.setAttribute("aria-expanded", String(nextExpanded));
      toggle.textContent = nextExpanded ? "접기" : "전체보기";
    });
  });
})();
