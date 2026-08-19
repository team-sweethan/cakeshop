(() => {
  "use strict";

  const rotation = document.querySelector("[data-notice-rotation]");
  if (!rotation) {
    return;
  }

  const notices = Array.from(rotation.querySelectorAll("[data-notice-item]"));
  if (notices.length < 2) {
    return;
  }

  let currentIndex = 0;

  window.setInterval(() => {
    notices[currentIndex].hidden = true;
    currentIndex = (currentIndex + 1) % notices.length;
    notices[currentIndex].hidden = false;
  }, 5000);
})();
