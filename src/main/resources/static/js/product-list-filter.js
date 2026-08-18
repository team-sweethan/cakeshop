(function () {
  "use strict";

  const priceRange = document.querySelector(
      "[data-product-price-range]"
  );

  if (!priceRange) {
    return;
  }

  const form = priceRange.closest("form");

  if (!form) {
    return;
  }

  let priceChanged = false;

  priceRange.addEventListener("input", function (event) {
    if (event.target.matches("[name='minPrice'], [name='maxPrice']")) {
      priceChanged = true;
    }
  });

  form.addEventListener("submit", function () {
    priceChanged = false;
  });

  priceRange.addEventListener("focusout", function () {
    window.setTimeout(function () {
      if (!priceChanged || priceRange.contains(document.activeElement)) {
        return;
      }

      if (form.checkValidity()) {
        form.requestSubmit();
      }
    }, 0);
  });
})();
