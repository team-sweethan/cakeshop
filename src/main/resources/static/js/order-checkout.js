(function () {
  "use strict";

  function initializePickupScheduler() {
    const scheduler = document.querySelector("[data-pickup-scheduler]");
    if (!scheduler) return;

    const dateSelect = scheduler.querySelector("[data-pickup-date]");
    const panels = Array.from(
      scheduler.querySelectorAll("[data-pickup-time-panel]")
    );
    if (!dateSelect || !panels.length) return;

    function showDate(date, clearSelectedTime) {
      panels.forEach(function (panel) {
        panel.hidden = panel.dataset.pickupTimePanel !== date;
        panel.querySelectorAll("[name='pickupAt']").forEach(function (input) {
          input.required = false;
        });
      });
      const visiblePanel = panels.find(function (panel) {
        return panel.dataset.pickupTimePanel === date;
      });
      const firstVisibleTime = visiblePanel
        ? visiblePanel.querySelector("[name='pickupAt']")
        : null;
      if (firstVisibleTime) firstVisibleTime.required = true;
      if (clearSelectedTime) {
        const selectedTime = scheduler.querySelector("[name='pickupAt']:checked");
        if (selectedTime) selectedTime.checked = false;
      }
    }

    const selectedTime = scheduler.querySelector("[name='pickupAt']:checked");
    const initialDate = selectedTime
      ? selectedTime.value.substring(0, 10)
      : dateSelect.value;
    dateSelect.value = initialDate;
    showDate(initialDate, false);
    dateSelect.addEventListener("change", function () {
      showDate(dateSelect.value, true);
    });
  }

  function initializeOrdererContact() {
    const sameAsOrderer = document.querySelector("[data-same-as-orderer]");
    const ordererName = document.getElementById("ordererName");
    const ordererPhone = document.getElementById("ordererPhone");
    const pickupName = document.getElementById("pickupName");
    const pickupPhone = document.getElementById("pickupPhone");
    if (!sameAsOrderer || !ordererName || !ordererPhone
        || !pickupName || !pickupPhone) {
      return;
    }

    function copyOrdererContact() {
      if (!sameAsOrderer.checked) return;
      pickupName.value = ordererName.value;
      pickupPhone.value = ordererPhone.value;
    }

    function updatePickupContactMode() {
      pickupName.readOnly = sameAsOrderer.checked;
      pickupPhone.readOnly = sameAsOrderer.checked;
      copyOrdererContact();
    }

    sameAsOrderer.addEventListener("change", updatePickupContactMode);
    ordererName.addEventListener("input", copyOrdererContact);
    ordererPhone.addEventListener("input", copyOrdererContact);
    updatePickupContactMode();
  }

  function initializeSingleSubmit() {
    const form = document.querySelector("form[action$='/orders/general']");
    if (!form) return;

    form.addEventListener("submit", function () {
      if (form.dataset.submitting === "true") return;
      form.dataset.submitting = "true";
      const submitButton = form.querySelector("button[type='submit']");
      if (submitButton) {
        submitButton.disabled = true;
        submitButton.textContent = "주문 생성 중...";
      }
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    initializePickupScheduler();
    initializeOrdererContact();
    initializeSingleSubmit();
  });
})();
