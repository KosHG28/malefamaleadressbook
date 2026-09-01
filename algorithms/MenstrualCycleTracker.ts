/**
 * MenstrualCycleTracker
 *
 * Calendar-only, statistically driven forecasting of the next period start, ovulation day,
 * and fertile window from a history of logged period start dates. No biomarker data (basal
 * body temperature, LH strip results, etc.) is available, so the model leans entirely on
 * cycle-length statistics rather than the static "28-day cycle / ovulation on day 14" rule:
 *
 *   - an EWMA over historical cycle lengths reacts to real drift while damping a single
 *     outlier cycle far more gracefully than a plain average would,
 *   - ovulation is anchored to the *next* period via a luteal-phase length (13 days by
 *     default -- the luteal phase is the far more stable half of the cycle, so counting
 *     backward from a predicted period is more reliable than counting forward from the last
 *     one), with the follicular-phase length absorbing all of the person's cycle-to-cycle
 *     variability,
 *   - the fertile window's margin widens with the person's *own* measured variability
 *     instead of applying the same fixed number of days to everyone.
 */

/** Shape of a completed forecast, ready to serialize as JSON. */
export interface CyclePrediction {
  next_period_start: string;
  predicted_ovulation: string;
  fertile_window_start: string;
  fertile_window_end: string;
  is_irregular: boolean;
  cycle_standard_deviation: number;
  applied_margin_days: number;
}

export interface MenstrualCycleTrackerOptions {
  /** Luteal phase length in days (ovulation -> next period start). Default: 13. */
  lutealPhaseDays?: number;
  /** Population-median cycle length, used only when there is no history at all. Default: 29. */
  defaultCycleLengthDays?: number;
  /** EWMA smoothing factor for the cycle-length forecast, in (0, 1]. Default: 0.2. */
  alpha?: number;
}

/** Fertile-window half-widths, in days, before any dynamic margin is applied. */
const FERTILE_DAYS_BEFORE_OVULATION = 5;
const FERTILE_DAYS_AFTER_OVULATION = 1;

/** The dynamic fertile-window margin never widens the window by more than this many days per side. */
const MAX_MARGIN_DAYS = 4;

/** A cycle is flagged irregular once the standard deviation of its recent lengths exceeds this. */
const IRREGULAR_SD_THRESHOLD_DAYS = 7;

/** Only this many of the most recent cycle lengths feed the standard-deviation/irregularity check. */
const DEFAULT_SD_LOOKBACK_CYCLES = 6;

const DAY_MS = 24 * 60 * 60 * 1000;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function daysBetween(a: Date, b: Date): number {
  return Math.round((b.getTime() - a.getTime()) / DAY_MS);
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getTime() + days * DAY_MS);
}

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** Parses a "YYYY-MM-DD" string as a UTC midnight instant, so day-math is never off by one. */
function parseIsoDate(value: string): Date {
  if (!ISO_DATE_PATTERN.test(value)) {
    throw new Error(`Invalid period start date "${value}" -- expected "YYYY-MM-DD".`);
  }
  const date = new Date(`${value}T00:00:00.000Z`);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`Invalid period start date "${value}".`);
  }
  return date;
}

/**
 * Tracks a history of logged period start dates and forecasts the next cycle from it.
 *
 * Instances are effectively immutable snapshots of a given history: call
 * {@link MenstrualCycleTracker.withPeriodStart} to fold in a newly logged date and get back a
 * tracker reflecting it, rather than mutating this one in place.
 */
export class MenstrualCycleTracker {
  private readonly lutealPhaseDays: number;
  private readonly defaultCycleLengthDays: number;
  private readonly alpha: number;
  private readonly periodStarts: readonly Date[];

  /**
   * @param periodStartDates Logged period start dates as "YYYY-MM-DD" strings, in any order.
   *   Duplicate dates are collapsed; a duplicate carries no additional cycle-length information
   *   and would otherwise inject a spurious zero-length "cycle" into the statistics.
   */
  constructor(periodStartDates: readonly string[] = [], options: MenstrualCycleTrackerOptions = {}) {
    this.lutealPhaseDays = options.lutealPhaseDays ?? 13;
    this.defaultCycleLengthDays = options.defaultCycleLengthDays ?? 29;
    this.alpha = options.alpha ?? 0.2;

    if (!Number.isFinite(this.lutealPhaseDays) || this.lutealPhaseDays <= 0) {
      throw new Error("lutealPhaseDays must be a positive number of days.");
    }
    if (!Number.isFinite(this.defaultCycleLengthDays) || this.defaultCycleLengthDays <= 0) {
      throw new Error("defaultCycleLengthDays must be a positive number of days.");
    }
    if (!Number.isFinite(this.alpha) || this.alpha <= 0 || this.alpha > 1) {
      throw new Error("alpha must be a number in (0, 1].");
    }

    const seen = new Set<string>();
    this.periodStarts = periodStartDates
      .map(parseIsoDate)
      .sort((a, b) => a.getTime() - b.getTime())
      .filter((date) => {
        const key = toIsoDate(date);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });
  }

  /** Returns a new tracker with `date` folded into the history (same options as this one). */
  withPeriodStart(date: string): MenstrualCycleTracker {
    return new MenstrualCycleTracker([...this.periodStarts.map(toIsoDate), date], {
      lutealPhaseDays: this.lutealPhaseDays,
      defaultCycleLengthDays: this.defaultCycleLengthDays,
      alpha: this.alpha,
    });
  }

  /** How many complete cycles (consecutive logged pairs) the history contains. */
  get cycleHistoryCount(): number {
    return Math.max(0, this.periodStarts.length - 1);
  }

  /** Lengths, in days, between consecutive logged period starts -- oldest pair first. */
  private cycleLengths(): number[] {
    const lengths: number[] = [];
    for (let i = 1; i < this.periodStarts.length; i++) {
      lengths.push(daysBetween(this.periodStarts[i - 1]!, this.periodStarts[i]!));
    }
    return lengths;
  }

  /**
   * Forecasts the next cycle's length with an exponentially weighted moving average:
   *
   *   Forecast_Length = alpha * Current_Cycle_Length + (1 - alpha) * Previous_Forecast_Length
   *
   * An EWMA needs a seed "previous forecast" to recur from, which doesn't exist yet with under
   * 3 cycles of history -- those cases fall back to the plain arithmetic mean of whatever cycle
   * lengths exist. With no history at all, falls back to the population median
   * ({@link MenstrualCycleTrackerOptions.defaultCycleLengthDays}).
   */
  forecastCycleLength(): number {
    const lengths = this.cycleLengths();
    if (lengths.length === 0) return this.defaultCycleLengthDays;
    if (lengths.length < 3) {
      return lengths.reduce((sum, len) => sum + len, 0) / lengths.length;
    }
    let forecast = lengths[0]!;
    for (let i = 1; i < lengths.length; i++) {
      forecast = this.alpha * lengths[i]! + (1 - this.alpha) * forecast;
    }
    return forecast;
  }

  /**
   * Sample standard deviation (n-1 denominator -- this estimates spread from a limited sample,
   * not a full population) of the last `lookback` logged cycle lengths. Returns 0 with fewer
   * than two data points, since spread is undefined for a single value.
   */
  cycleLengthStandardDeviation(lookback: number = DEFAULT_SD_LOOKBACK_CYCLES): number {
    const lengths = this.cycleLengths().slice(-lookback);
    if (lengths.length < 2) return 0;
    const mean = lengths.reduce((sum, len) => sum + len, 0) / lengths.length;
    const variance = lengths.reduce((sum, len) => sum + (len - mean) ** 2, 0) / (lengths.length - 1);
    return Math.sqrt(variance);
  }

  /** `round(SD / 2)`, capped at {@link MAX_MARGIN_DAYS} and never negative. */
  private appliedMarginDays(standardDeviation: number): number {
    return Math.min(MAX_MARGIN_DAYS, Math.max(0, Math.round(standardDeviation / 2)));
  }

  /**
   * Produces the full forecast. Returns `null` when there is no period history at all -- there
   * is nothing to anchor a prediction to.
   */
  predict(): CyclePrediction | null {
    if (this.periodStarts.length === 0) return null;

    const latestPeriodStart = this.periodStarts[this.periodStarts.length - 1]!;
    const forecastLengthDays = Math.round(this.forecastCycleLength());
    const standardDeviation = this.cycleLengthStandardDeviation();
    const marginDays = this.appliedMarginDays(standardDeviation);
    const isIrregular = standardDeviation > IRREGULAR_SD_THRESHOLD_DAYS;

    const nextPeriodStart = addDays(latestPeriodStart, forecastLengthDays);
    const predictedOvulation = addDays(nextPeriodStart, -this.lutealPhaseDays);
    const fertileWindowStart = addDays(predictedOvulation, -(FERTILE_DAYS_BEFORE_OVULATION + marginDays));
    const fertileWindowEnd = addDays(predictedOvulation, FERTILE_DAYS_AFTER_OVULATION + marginDays);

    return {
      next_period_start: toIsoDate(nextPeriodStart),
      predicted_ovulation: toIsoDate(predictedOvulation),
      fertile_window_start: toIsoDate(fertileWindowStart),
      fertile_window_end: toIsoDate(fertileWindowEnd),
      is_irregular: isIrregular,
      cycle_standard_deviation: Math.round(standardDeviation * 100) / 100,
      applied_margin_days: marginDays,
    };
  }

  /** Convenience: {@link predict}'s result serialized straight to a JSON string. */
  predictAsJson(space?: number): string {
    return JSON.stringify(this.predict(), null, space);
  }
}

/*
 * Example usage:
 *
 *   const tracker = new MenstrualCycleTracker([
 *     "2026-05-03", "2026-06-01", "2026-06-30", "2026-07-28",
 *   ]);
 *   console.log(tracker.predictAsJson(2));
 *   // {
 *   //   "next_period_start": "2026-08-26",
 *   //   "predicted_ovulation": "2026-08-13",
 *   //   "fertile_window_start": "2026-08-08",
 *   //   "fertile_window_end": "2026-08-14",
 *   //   "is_irregular": false,
 *   //   "cycle_standard_deviation": 0.58,
 *   //   "applied_margin_days": 0
 *   // }
 */
