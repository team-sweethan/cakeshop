document.addEventListener("click", (event) => {
    const openButton = event.target.closest("[data-modal-open]");

    if (openButton) {
        const modal = document.getElementById(openButton.dataset.modalOpen);

        if (modal) {
            modal.hidden = false;
            modal.querySelector("textarea")?.focus();
        }

        return;
    }

    const closeButton = event.target.closest("[data-modal-close]");

    if (closeButton) {
        closeButton.closest(".modal").hidden = true;
        return;
    }

    if (event.target.matches(".modal")) {
        event.target.hidden = true;
    }
});

document.addEventListener("keydown", (event) => {
    if (event.key !== "Escape") {
        return;
    }

    document.querySelectorAll(".modal:not([hidden])")
            .forEach((modal) => {
                modal.hidden = true;
            });
});
