document.addEventListener("DOMContentLoaded", () => {
  const productType = document.querySelector("[data-product-type]");
  const preparationDays = document.querySelector("[data-preparation-days]");

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
