document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("[data-payment-expiration-check]").forEach((checkbox) => {
    checkbox.addEventListener("change", () => {
      checkbox.form?.requestSubmit();
    });
  });
});
