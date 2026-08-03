<script setup>
import { computed } from "vue";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

const props = defineProps({
  month: {
    type: Date,
    required: true,
  },
  dailyBreakdown: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(["select-date"]);

const formatDateKey = (year, month, day) =>
  `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;

const dailyByDate = computed(() =>
  new Map((props.dailyBreakdown ?? []).map((item) => [item.date, item])),
);

const calendarCells = computed(() => {
  const year = props.month.getFullYear();
  const month = props.month.getMonth();
  const firstWeekday = new Date(year, month, 1).getDay();
  const lastDay = new Date(year, month + 1, 0).getDate();
  const cells = Array.from({ length: firstWeekday }, (_, index) => ({
    key: `blank-start-${index}`,
    isBlank: true,
  }));

  for (let day = 1; day <= lastDay; day += 1) {
    const date = formatDateKey(year, month, day);
    cells.push({
      key: date,
      isBlank: false,
      day,
      date,
      daily: dailyByDate.value.get(date) ?? null,
    });
  }

  const trailingCount = (7 - (cells.length % 7)) % 7;
  cells.push(
    ...Array.from({ length: trailingCount }, (_, index) => ({
      key: `blank-end-${index}`,
      isBlank: true,
    })),
  );

  return cells;
});

const formatAmount = (amount) => new Intl.NumberFormat("ko-KR").format(Number(amount) || 0);

const selectDate = (cell) => {
  if (!cell.isBlank) {
    emit("select-date", cell.date);
  }
};
</script>

<template>
  <div class="expense-calendar-scroll">
    <div class="expense-calendar" aria-label="월별 수입 및 지출 달력">
      <div
        v-for="(weekday, index) in WEEKDAYS"
        :key="weekday"
        class="calendar-weekday"
        :class="{ sunday: index === 0, saturday: index === 6 }"
      >
        {{ weekday }}
      </div>

      <component
        v-for="(cell, index) in calendarCells"
        :key="cell.key"
        :is="cell.isBlank ? 'div' : 'button'"
        :type="cell.isBlank ? undefined : 'button'"
        class="calendar-day"
        :class="{
          'calendar-day-blank': cell.isBlank,
          sunday: !cell.isBlank && index % 7 === 0,
          saturday: !cell.isBlank && index % 7 === 6,
        }"
        @click="selectDate(cell)"
      >
        <template v-if="!cell.isBlank">
          <span class="calendar-day-number">{{ cell.day }}</span>
          <div v-if="cell.daily" class="calendar-day-amounts">
            <span v-if="Number(cell.daily.totalIncome) > 0" class="income-amount">
              +{{ formatAmount(cell.daily.totalIncome) }}
            </span>
            <span v-if="Number(cell.daily.totalExpense) > 0" class="expense-amount">
              -{{ formatAmount(cell.daily.totalExpense) }}
            </span>
          </div>
        </template>
      </component>
    </div>
  </div>
</template>

<style scoped>
.expense-calendar-scroll {
  overflow-x: auto;
  padding-bottom: 2px;
}

.expense-calendar {
  display: grid;
  min-width: 680px;
  grid-template-columns: repeat(7, minmax(84px, 1fr));
  border-top: 1px solid #edf0f5;
  border-left: 1px solid #edf0f5;
}

.calendar-weekday {
  padding: 12px 8px;
  border-right: 1px solid #edf0f5;
  border-bottom: 1px solid #edf0f5;
  color: #70768a;
  background: #fafbfe;
  font-size: 0.82rem;
  font-weight: 700;
  text-align: center;
}

.calendar-day {
  position: relative;
  min-height: 112px;
  padding: 10px;
  border-right: 1px solid #edf0f5;
  border-top: 0;
  border-left: 0;
  border-bottom: 1px solid #edf0f5;
  background: #ffffff;
  font-family: inherit;
  text-align: initial;
}

button.calendar-day {
  cursor: pointer;
}

button.calendar-day:hover,
button.calendar-day:focus-visible {
  position: relative;
  z-index: 1;
  outline: 2px solid #d9d3ff;
  outline-offset: -2px;
  background: #faf9ff;
}

.calendar-day-blank {
  background: #fafbfe;
}

.calendar-day-number {
  position: absolute;
  top: 10px;
  left: 10px;
  display: inline-flex;
  width: 25px;
  height: 25px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #343044;
  font-size: 0.8rem;
  font-weight: 700;
}

.calendar-day-amounts {
  display: grid;
  gap: 4px;
  margin-top: 30px;
  font-size: 0.72rem;
  font-weight: 700;
  text-align: right;
}

.income-amount {
  color: #4f73e8;
}

.expense-amount {
  color: #7c8294;
}

.sunday,
.calendar-day.sunday .calendar-day-number {
  color: #e46b72;
}

.saturday,
.calendar-day.saturday .calendar-day-number {
  color: #4f73e8;
}
</style>
