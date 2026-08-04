document.querySelectorAll('[data-birth-date-picker]').forEach(picker => {
  const dateInput = picker.querySelector('[data-birth-date-value]');
  const yearSelect = picker.querySelector('[data-birth-year]');
  const monthSelect = picker.querySelector('[data-birth-month]');
  const daySelect = picker.querySelector('[data-birth-day]');
  const currentYear = new Date().getFullYear();
  const initialParts = dateInput.value ? dateInput.value.split('-') : [];

  for (let year = currentYear; year >= currentYear - 120; year--) {
    yearSelect.add(new Option(`${year}년`, String(year)));
  }
  for (let month = 1; month <= 12; month++) {
    const value = String(month).padStart(2, '0');
    monthSelect.add(new Option(`${month}월`, value));
  }

  yearSelect.value = initialParts[0] || '';
  monthSelect.value = initialParts[1] || '';

  function updateDays(preferredDay = daySelect.value) {
    daySelect.length = 1;
    if (!yearSelect.value || !monthSelect.value) {
      dateInput.value = '';
      return;
    }

    const lastDay = new Date(
            Number(yearSelect.value),
            Number(monthSelect.value),
            0).getDate();
    for (let day = 1; day <= lastDay; day++) {
      const value = String(day).padStart(2, '0');
      daySelect.add(new Option(`${day}일`, value));
    }
    daySelect.value = Number(preferredDay) <= lastDay ? preferredDay : '';
    syncBirthDate();
  }

  function syncBirthDate() {
    dateInput.value = yearSelect.value && monthSelect.value && daySelect.value
            ? `${yearSelect.value}-${monthSelect.value}-${daySelect.value}`
            : '';
  }

  function syncSelectsFromBirthDate() {
    const parts = dateInput.value ? dateInput.value.split('-') : [];
    yearSelect.value = parts[0] || '';
    monthSelect.value = parts[1] || '';
    updateDays(parts[2] || '');
  }

  yearSelect.addEventListener('change', () => updateDays());
  monthSelect.addEventListener('change', () => updateDays());
  daySelect.addEventListener('change', syncBirthDate);
  dateInput.addEventListener('change', syncSelectsFromBirthDate);
  updateDays(initialParts[2] || '');
});
