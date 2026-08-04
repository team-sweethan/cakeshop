document.addEventListener("DOMContentLoaded", () => {
  const productType = document.querySelector("[data-product-type]");
  const preparationDays = document.querySelector("[data-preparation-days]");
  const createImageInput = document.querySelector(
    "[data-product-create-image-input]"
  );
  const createImageSelect = document.querySelector(
    "[data-product-create-image-select]"
  );
  const createImagePlaceholder = document.querySelector(
    "[data-product-create-image-placeholder]"
  );
  const createImageGrid = document.querySelector(
    "[data-product-create-image-grid]"
  );
  const createImageLimit = document.querySelector(
    "[data-product-create-image-limit]"
  );

  if (createImageInput && createImageSelect) {
    const maxImageCount = 5;
    let selectedImageFiles = [];
    let previewUrls = [];

    const fileKey = (file) => {
      return `${file.name}:${file.size}:${file.lastModified}`;
    };

    const createActionButton = (text, className) => {
      const button = document.createElement("button");

      button.className = className;
      button.type = "button";
      button.textContent = text;

      return button;
    };

    const renderSelectedImages = () => {
      previewUrls.forEach((url) => URL.revokeObjectURL(url));
      previewUrls = [];

      const hasImages = selectedImageFiles.length > 0;

      if (createImagePlaceholder) {
        createImagePlaceholder.hidden = hasImages;
      }

      if (createImageGrid) {
        createImageGrid.hidden = !hasImages;
        createImageGrid.replaceChildren();

        selectedImageFiles.forEach((file, index) => {
          const card = document.createElement("div");
          const badge = document.createElement("span");
          const preview = document.createElement("div");
          const image = document.createElement("img");
          const actions = document.createElement("div");
          const replaceButton = createActionButton("교체", "btn");
          const deleteButton = createActionButton(
            "삭제",
            "btn btn--danger"
          );
          const previewUrl = URL.createObjectURL(file);

          previewUrls.push(previewUrl);
          card.className = "product-image-card";
          badge.className = "badge badge--success product-image-badge";
          badge.textContent = "대표 이미지";

          if (index > 0) {
            badge.classList.add("product-image-badge--placeholder");
            badge.setAttribute("aria-hidden", "true");
          }

          preview.className = "product-image-preview";
          image.className = "placeholder-image";
          image.src = previewUrl;
          image.alt = `상품 이미지 ${index + 1} 미리보기`;
          actions.className = "product-image-actions";

          replaceButton.addEventListener("click", () => {
            const replacementInput = document.createElement("input");

            replacementInput.type = "file";
            replacementInput.accept = "image/jpeg,image/png";
            replacementInput.addEventListener("change", () => {
              const replacement = replacementInput.files
                ? replacementInput.files[0]
                : null;

              if (replacement) {
                selectedImageFiles[index] = replacement;
                updateSelectedFiles();
              }
            });
            replacementInput.click();
          });

          deleteButton.addEventListener("click", () => {
            selectedImageFiles.splice(index, 1);
            updateSelectedFiles();
          });

          preview.appendChild(image);
          actions.append(replaceButton, deleteButton);
          card.append(badge, preview, actions);
          createImageGrid.appendChild(card);
        });
      }

      createImageSelect.disabled =
        selectedImageFiles.length >= maxImageCount;

      if (createImageLimit) {
        createImageLimit.hidden =
          selectedImageFiles.length < maxImageCount;
      }
    };

    function updateSelectedFiles() {
      const dataTransfer = new DataTransfer();

      selectedImageFiles.forEach((file) => {
        dataTransfer.items.add(file);
      });

      createImageInput.files = dataTransfer.files;
      renderSelectedImages();
    }

    createImageSelect.addEventListener("click", () => {
      createImageInput.click();
    });

    createImageInput.addEventListener("change", () => {
      const existingKeys = new Set(
        selectedImageFiles.map(fileKey)
      );
      const newlySelectedFiles = Array.from(
        createImageInput.files || []
      );

      newlySelectedFiles.forEach((file) => {
        const key = fileKey(file);

        if (selectedImageFiles.length < maxImageCount
            && !existingKeys.has(key)) {
          selectedImageFiles.push(file);
          existingKeys.add(key);
        }
      });

      updateSelectedFiles();
    });
  }

  if (!productType || !preparationDays) {
    return;
  }

  const applyProductPreparationPolicy = (normalizeCustomValues) => {
    const isGeneral = productType.value === "GENERAL";

    preparationDays.readOnly = isGeneral;

    if (isGeneral) {
      preparationDays.value = "0";
      preparationDays.min = "0";

      return;
    }

    preparationDays.min = "1";

    if (normalizeCustomValues && Number(preparationDays.value) < 1) {
      preparationDays.value = "1";
    }
  };

  productType.addEventListener("change", () => {
    applyProductPreparationPolicy(true);
  });

  applyProductPreparationPolicy(false);
});
