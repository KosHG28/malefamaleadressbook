(function () {
  "use strict";

  const STORAGE_KEY = "calendar.events.v1";

  const MONTH_NAMES = [
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
  ];
  const WEEKDAY_NAMES = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];

  /** @typedef {{id:string,title:string,date:string,allDay:boolean,start:string,end:string,color:string,notes:string}} CalEvent */

  const state = {
    viewDate: new Date(), // any date within the currently viewed month
    selectedDate: toDateKey(new Date()),
    events: loadEvents(),
    searchQuery: "",
  };

  // ---------- Storage ----------

  function loadEvents() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      console.warn("Не удалось прочитать события из localStorage", e);
      return [];
    }
  }

  function saveEvents() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state.events));
  }

  // ---------- Date helpers ----------

  function toDateKey(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  }

  function isSameDay(a, b) {
    return (
      a.getFullYear() === b.getFullYear() &&
      a.getMonth() === b.getMonth() &&
      a.getDate() === b.getDate()
    );
  }

  function startOfCalendarGrid(viewDate) {
    const first = new Date(viewDate.getFullYear(), viewDate.getMonth(), 1);
    const dow = (first.getDay() + 6) % 7; // Monday = 0
    const start = new Date(first);
    start.setDate(first.getDate() - dow);
    return start;
  }

  // ---------- Rendering ----------

  const els = {
    monthLabel: document.getElementById("monthLabel"),
    weekdays: document.getElementById("weekdays"),
    grid: document.getElementById("grid"),
    sidebarTitle: document.getElementById("sidebarTitle"),
    agendaList: document.getElementById("agendaList"),
    searchInput: document.getElementById("searchInput"),
  };

  function renderWeekdays() {
    els.weekdays.innerHTML = WEEKDAY_NAMES.map((w) => `<div>${w}</div>`).join("");
  }

  function eventsForDate(dateKey) {
    return state.events
      .filter((e) => e.date === dateKey)
      .sort((a, b) => {
        if (a.allDay && !b.allDay) return -1;
        if (!a.allDay && b.allDay) return 1;
        return (a.start || "").localeCompare(b.start || "");
      });
  }

  function matchesSearch(evt) {
    if (!state.searchQuery) return true;
    const q = state.searchQuery.toLowerCase();
    return (
      evt.title.toLowerCase().includes(q) ||
      (evt.notes || "").toLowerCase().includes(q)
    );
  }

  function renderGrid() {
    const viewDate = state.viewDate;
    els.monthLabel.textContent = `${MONTH_NAMES[viewDate.getMonth()]} ${viewDate.getFullYear()}`;

    const gridStart = startOfCalendarGrid(viewDate);
    const today = new Date();
    const cells = [];

    for (let i = 0; i < 42; i++) {
      const cellDate = new Date(gridStart);
      cellDate.setDate(gridStart.getDate() + i);
      const dateKey = toDateKey(cellDate);
      const inOtherMonth = cellDate.getMonth() !== viewDate.getMonth();
      const isToday = isSameDay(cellDate, today);
      const isSelected = dateKey === state.selectedDate;

      const dayEvents = eventsForDate(dateKey).filter(matchesSearch);
      const maxVisible = 3;
      const visible = dayEvents.slice(0, maxVisible);
      const remaining = dayEvents.length - visible.length;

      const chipsHtml = visible
        .map(
          (e) =>
            `<div class="event-chip" style="background:${e.color}" data-event-id="${e.id}" title="${escapeHtml(e.title)}">${
              e.allDay ? "" : `${escapeHtml(e.start || "")} `
            }${escapeHtml(e.title)}</div>`
        )
        .join("");
      const moreHtml = remaining > 0 ? `<div class="event-more">+${remaining} ещё</div>` : "";

      cells.push(`
        <div class="day-cell${inOtherMonth ? " other-month" : ""}${isToday ? " today" : ""}${isSelected ? " selected" : ""}" data-date="${dateKey}">
          <span class="day-number">${cellDate.getDate()}</span>
          <div class="day-events">${chipsHtml}${moreHtml}</div>
        </div>
      `);
    }

    els.grid.innerHTML = cells.join("");
  }

  function renderAgenda() {
    const dateKey = state.selectedDate;
    const d = new Date(dateKey + "T00:00:00");
    const label = d.toLocaleDateString("ru-RU", {
      day: "numeric",
      month: "long",
      year: "numeric",
      weekday: "long",
    });
    els.sidebarTitle.textContent = label;

    const dayEvents = eventsForDate(dateKey).filter(matchesSearch);

    if (dayEvents.length === 0) {
      els.agendaList.innerHTML = `<div class="agenda-empty">На этот день событий нет</div>`;
      return;
    }

    els.agendaList.innerHTML = dayEvents
      .map((e) => {
        const timeLabel = e.allDay
          ? "Весь день"
          : `${e.start || ""}${e.end ? " – " + e.end : ""}`;
        return `
          <div class="agenda-item" style="border-left-color:${e.color}" data-event-id="${e.id}">
            <span class="agenda-title">${escapeHtml(e.title)}</span>
            <span class="agenda-meta">${timeLabel}</span>
            ${e.notes ? `<span class="agenda-meta">${escapeHtml(e.notes)}</span>` : ""}
          </div>
        `;
      })
      .join("");
  }

  function renderAll() {
    renderGrid();
    renderAgenda();
  }

  function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str ?? "";
    return div.innerHTML;
  }

  // ---------- Modal ----------

  const modal = {
    overlay: document.getElementById("modalOverlay"),
    title: document.getElementById("modalTitle"),
    form: document.getElementById("eventForm"),
    id: document.getElementById("eventId"),
    titleInput: document.getElementById("eventTitle"),
    date: document.getElementById("eventDate"),
    allDay: document.getElementById("eventAllDay"),
    timeRow: document.getElementById("timeRow"),
    start: document.getElementById("eventStart"),
    end: document.getElementById("eventEnd"),
    colorPicker: document.getElementById("colorPicker"),
    colorInput: document.getElementById("eventColor"),
    notes: document.getElementById("eventNotes"),
    deleteBtn: document.getElementById("deleteEventBtn"),
  };

  function openModalForNew(dateKey) {
    modal.title.textContent = "Новое событие";
    modal.id.value = "";
    modal.titleInput.value = "";
    modal.date.value = dateKey || state.selectedDate;
    modal.allDay.checked = false;
    modal.start.value = "10:00";
    modal.end.value = "11:00";
    modal.notes.value = "";
    setSelectedColor("#5b8def");
    modal.deleteBtn.classList.add("hidden");
    toggleTimeRow();
    showModal();
  }

  function openModalForEdit(evt) {
    modal.title.textContent = "Редактировать событие";
    modal.id.value = evt.id;
    modal.titleInput.value = evt.title;
    modal.date.value = evt.date;
    modal.allDay.checked = !!evt.allDay;
    modal.start.value = evt.start || "10:00";
    modal.end.value = evt.end || "11:00";
    modal.notes.value = evt.notes || "";
    setSelectedColor(evt.color || "#5b8def");
    modal.deleteBtn.classList.remove("hidden");
    toggleTimeRow();
    showModal();
  }

  function setSelectedColor(color) {
    modal.colorInput.value = color;
    modal.colorPicker.querySelectorAll(".color-swatch").forEach((sw) => {
      sw.classList.toggle("selected", sw.dataset.color === color);
    });
  }

  function toggleTimeRow() {
    modal.timeRow.classList.toggle("hidden", modal.allDay.checked);
  }

  function showModal() {
    modal.overlay.classList.remove("hidden");
    setTimeout(() => modal.titleInput.focus(), 0);
  }

  function hideModal() {
    modal.overlay.classList.add("hidden");
  }

  function handleFormSubmit(e) {
    e.preventDefault();
    const title = modal.titleInput.value.trim();
    if (!title) return;

    const id = modal.id.value || crypto.randomUUID();
    const evt = {
      id,
      title,
      date: modal.date.value,
      allDay: modal.allDay.checked,
      start: modal.allDay.checked ? "" : modal.start.value,
      end: modal.allDay.checked ? "" : modal.end.value,
      color: modal.colorInput.value,
      notes: modal.notes.value.trim(),
    };

    const existingIndex = state.events.findIndex((ev) => ev.id === id);
    if (existingIndex >= 0) {
      state.events[existingIndex] = evt;
    } else {
      state.events.push(evt);
    }
    saveEvents();

    state.selectedDate = evt.date;
    state.viewDate = new Date(evt.date + "T00:00:00");
    hideModal();
    renderAll();
  }

  function handleDelete() {
    const id = modal.id.value;
    if (!id) return;
    state.events = state.events.filter((ev) => ev.id !== id);
    saveEvents();
    hideModal();
    renderAll();
  }

  // ---------- Event wiring ----------

  document.getElementById("prevBtn").addEventListener("click", () => {
    state.viewDate = new Date(state.viewDate.getFullYear(), state.viewDate.getMonth() - 1, 1);
    renderGrid();
  });

  document.getElementById("nextBtn").addEventListener("click", () => {
    state.viewDate = new Date(state.viewDate.getFullYear(), state.viewDate.getMonth() + 1, 1);
    renderGrid();
  });

  document.getElementById("todayBtn").addEventListener("click", () => {
    const now = new Date();
    state.viewDate = now;
    state.selectedDate = toDateKey(now);
    renderAll();
  });

  document.getElementById("addEventBtn").addEventListener("click", () => {
    openModalForNew(state.selectedDate);
  });

  document.getElementById("closeModalBtn").addEventListener("click", hideModal);
  document.getElementById("cancelBtn").addEventListener("click", hideModal);
  modal.overlay.addEventListener("click", (e) => {
    if (e.target === modal.overlay) hideModal();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !modal.overlay.classList.contains("hidden")) hideModal();
  });

  modal.form.addEventListener("submit", handleFormSubmit);
  modal.deleteBtn.addEventListener("click", handleDelete);
  modal.allDay.addEventListener("change", toggleTimeRow);

  modal.colorPicker.addEventListener("click", (e) => {
    const swatch = e.target.closest(".color-swatch");
    if (!swatch) return;
    setSelectedColor(swatch.dataset.color);
  });

  els.grid.addEventListener("click", (e) => {
    const chip = e.target.closest(".event-chip");
    if (chip) {
      const evt = state.events.find((ev) => ev.id === chip.dataset.eventId);
      if (evt) openModalForEdit(evt);
      return;
    }
    const cell = e.target.closest(".day-cell");
    if (cell) {
      state.selectedDate = cell.dataset.date;
      renderAll();
    }
  });

  els.grid.addEventListener("dblclick", (e) => {
    const cell = e.target.closest(".day-cell");
    if (cell) openModalForNew(cell.dataset.date);
  });

  els.agendaList.addEventListener("click", (e) => {
    const item = e.target.closest(".agenda-item");
    if (!item) return;
    const evt = state.events.find((ev) => ev.id === item.dataset.eventId);
    if (evt) openModalForEdit(evt);
  });

  els.searchInput.addEventListener("input", (e) => {
    state.searchQuery = e.target.value.trim();
    renderAll();
  });

  // ---------- Init ----------

  renderWeekdays();
  renderAll();
})();
