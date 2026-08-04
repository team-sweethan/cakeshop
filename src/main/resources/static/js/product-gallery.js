(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        const gallery = document.querySelector(".product-gallery");
        if (!gallery) return;

        const mainImage = gallery.querySelector("[data-product-main-image]");
        if (!mainImage) return;

        const thumbnailButtons = Array.from(
            gallery.querySelectorAll("[data-product-thumbnail]")
        );
        const previousButton = gallery.querySelector(
            "[data-product-gallery-previous]"
        );
        const nextButton = gallery.querySelector(
            "[data-product-gallery-next]"
        );
        const images = [
            {
                src: mainImage.src,
                alt: mainImage.alt
            }
        ].concat(thumbnailButtons.map(function (button) {
            return {
                src: button.dataset.imageUrl,
                alt: button.dataset.imageAlt
            };
        }));
        let currentIndex = 0;

        function showImage(index) {
            currentIndex = (index + images.length) % images.length;
            mainImage.src = images[currentIndex].src;
            mainImage.alt = images[currentIndex].alt;

            thumbnailButtons.forEach(function (button, thumbnailIndex) {
                const active = thumbnailIndex + 1 === currentIndex;
                button.classList.toggle("is-active", active);
                button.setAttribute("aria-current", active ? "true" : "false");
            });
        }

        thumbnailButtons.forEach(function (button, index) {
            button.addEventListener("click", function () {
                showImage(index + 1);
            });
        });

        if (previousButton) {
            previousButton.addEventListener("click", function () {
                showImage(currentIndex - 1);
            });
        }

        if (nextButton) {
            nextButton.addEventListener("click", function () {
                showImage(currentIndex + 1);
            });
        }
    });
})();
