(function () {
  "use strict";

  document.addEventListener("DOMContentLoaded", function () {
    if (typeof Chart === "undefined") return;

    const rows = Array.from(document.querySelectorAll("[data-statistics-point]"));
    const labels = rows.map(function (row) { return row.dataset.dateLabel; });

    function formatValue(value, unit) {
      return Number(value).toLocaleString("ko-KR") + unit;
    }

    function createChart(canvasId, label, values, color, unit) {
      const canvas = document.getElementById(canvasId);
      if (!canvas) return;

      new Chart(canvas, {
        type: "line",
        data: {
          labels: labels,
          datasets: [{
            label: label,
            data: values,
            borderColor: color,
            backgroundColor: color,
            borderWidth: 2,
            pointRadius: 3,
            pointHoverRadius: 5,
            tension: 0.2,
            fill: false
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          interaction: {
            mode: "index",
            intersect: false
          },
          plugins: {
            legend: {
              display: false
            },
            tooltip: {
              callbacks: {
                label: function (context) {
                  return label + ": " + formatValue(context.parsed.y, unit);
                }
              }
            }
          },
          scales: {
            y: {
              beginAtZero: true,
              ticks: {
                precision: 0,
                callback: function (value) {
                  return formatValue(value, unit);
                }
              }
            }
          }
        }
      });
    }

    createChart(
        "daily-order-chart",
        "주문 건수",
        rows.map(function (row) { return Number(row.dataset.orderCount); }),
        "#2563eb",
        "건"
    );
    createChart(
        "daily-sales-chart",
        "매출",
        rows.map(function (row) { return Number(row.dataset.salesAmount); }),
        "#dc2626",
        "원"
    );
  });
})();
