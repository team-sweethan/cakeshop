(() => {
  "use strict";

  const rotation = document.querySelector("[data-home-banner-rotation]");
  if (!rotation) {
    return;
  }

  const slides = Array.from(rotation.querySelectorAll("[data-home-banner-slide]"));
  if (slides.length < 2) {
    return;
  }

  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const previousButton = rotation.querySelector("[data-home-banner-previous]");
  const nextButton = rotation.querySelector("[data-home-banner-next]");
  let currentIndex = slides.findIndex(
      (slide) => slide.dataset.homeBannerActive === "true");
  let rotationTimer = null;

  if (currentIndex < 0) {
    currentIndex = 0;
  }

  const showSlide = (nextIndex) => {
    slides.forEach((slide, index) => {
      const active = index === nextIndex;
      slide.dataset.homeBannerActive = String(active);
      slide.setAttribute("aria-hidden", String(!active));
    });
    currentIndex = nextIndex;
  };

  const moveBy = (offset) => {
    showSlide((currentIndex + offset + slides.length) % slides.length);
  };

  const scheduleRotation = () => {
    if (rotationTimer !== null) {
      window.clearInterval(rotationTimer);
      rotationTimer = null;
    }

    if (reducedMotion.matches) {
      return;
    }

    rotationTimer = window.setInterval(() => {
      if (document.hidden
          || rotation.matches(":hover")
          || rotation.contains(document.activeElement)) {
        return;
      }

      moveBy(1);
    }, 5000);
  };

  previousButton?.addEventListener("click", () => {
    moveBy(-1);
    scheduleRotation();
  });

  nextButton?.addEventListener("click", () => {
    moveBy(1);
    scheduleRotation();
  });

  if (typeof reducedMotion.addEventListener === "function") {
    reducedMotion.addEventListener("change", scheduleRotation);
  } else {
    reducedMotion.addListener(scheduleRotation);
  }

  scheduleRotation();
})();
