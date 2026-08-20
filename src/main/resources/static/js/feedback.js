document.addEventListener("DOMContentLoaded", () => {
  const feedbackMessage = document.querySelector(
    '[data-feedback-message][role="alert"]'
  ) || document.querySelector("[data-feedback-message]");

  if (feedbackMessage) {
    feedbackMessage.focus();
  }
});
