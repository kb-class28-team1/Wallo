import { describe, expect, it } from "vitest"

import {
  getGoalAchievementRate,
  getGoalCurrentAmount,
  getGoalTargetAmount,
} from "./goalProgress"

describe("goalProgress", () => {
  it("uses the initial amount when current amount is unavailable", () => {
    expect(getGoalCurrentAmount({ initialAmount: 1_000_000 })).toBe(1_000_000)
  })

  it("calculates and caps the fallback achievement rate", () => {
    expect(getGoalAchievementRate({
      currentAmount: 1_400_000,
      targetAmount: 10_000_000,
    })).toBe(14)

    expect(getGoalAchievementRate({
      currentAmount: 12_000_000,
      targetAmount: 10_000_000,
    })).toBe(100)
  })

  it("prefers the server achievement rate and handles invalid targets", () => {
    expect(getGoalAchievementRate({
      currentAmount: 1_000_000,
      targetAmount: 10_000_000,
      achievementRate: 65.4,
    })).toBe(65)
    expect(getGoalAchievementRate({ currentAmount: 1_000_000, targetAmount: 0 })).toBe(0)
    expect(getGoalTargetAmount({ targetAmount: Number.NaN })).toBe(0)
  })
})
