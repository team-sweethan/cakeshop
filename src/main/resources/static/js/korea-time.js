/* offset 없는 LocalDateTime은 한국 시각이며 Z/offset이 있으면 같은 순간을 표시한다. */
(function () {
  "use strict";
  const formatter = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23"
  });

  function parse(value) {
    if (typeof value !== "string" || !value.trim()) return null;
    let input = value.trim().replace(" ", "T");
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(input)) return null;
    if (!/(?:Z|[+-]\d{2}:?\d{2})$/i.test(input)) input += "+09:00";
    const date = new Date(input);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  function parts(value) {
    const date = parse(value);
    return date ? Object.fromEntries(formatter.formatToParts(date).map(part => [part.type, part.value])) : null;
  }

  function dateTime(value) {
    const p = parts(value);
    if (!p) return "";
    return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}`;
  }

  function time(value) {
    const p = parts(value);
    return p ? `${p.hour}:${p.minute}` : "";
  }

  function pickup(value) {
    const p = parts(value);
    return p ? `${p.month}월 ${p.day}일 ${p.hour}:${p.minute}` : "-";
  }

  window.CakeShopTime = Object.freeze({ parse, dateTime, time, pickup });
})();
