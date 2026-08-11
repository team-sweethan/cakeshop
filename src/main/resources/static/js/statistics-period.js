(function () {
  "use strict";

  document.addEventListener("DOMContentLoaded", function () {
    const form = document.querySelector("[data-statistics-search-form]");
    if (!form) return;

    const periodType = form.querySelector("[data-statistics-period-type]");
    const rangeInputs = form.querySelectorAll("[data-statistics-range-input]");
    const periodLinks = form.querySelectorAll("[data-statistics-period-link]");
    const modeInputs = form.querySelectorAll("[data-statistics-mode-input]");
    if (!periodType) return;

    rangeInputs.forEach(function (input) {
      input.addEventListener("input", function () {
        periodType.value = "RANGE";
        periodLinks.forEach(function (link) {
          link.classList.remove("is-active");
          link.removeAttribute("aria-current");
        });
        modeInputs.forEach(function (container) {
          container.hidden = true;
        });
      });
    });
  });
})();
