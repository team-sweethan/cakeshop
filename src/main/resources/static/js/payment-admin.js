document.addEventListener("DOMContentLoaded", () => {
  const attentionCount = document.querySelector("[data-payment-attention-count]");
  if (!attentionCount) return;

  document.querySelectorAll("[data-payment-expiration-check]").forEach((checkbox) => {
    checkbox.addEventListener("change", () => {
      const currentCount = Number.parseInt(attentionCount.textContent, 10) || 0;
      attentionCount.textContent = `${Math.max(0, currentCount + (checkbox.checked ? -1 : 1))}건`;
    });
  });
});
