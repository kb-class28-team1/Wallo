export const getGoalCurrentAmount = (goal) => {
  const currentAmount = Number(goal?.currentAmount)
  if (Number.isFinite(currentAmount)) {
    return currentAmount
  }

  return Number(goal?.initialAmount) || 0
}

export const getGoalTargetAmount = (goal) => {
  const targetAmount = Number(goal?.targetAmount)
  return Number.isFinite(targetAmount) ? targetAmount : 0
}

export const getGoalAchievementRate = (goal) => {
  const serverRate = Number(goal?.achievementRate)
  if (Number.isFinite(serverRate)) {
    return Math.min(100, Math.max(0, Math.round(serverRate)))
  }

  const targetAmount = getGoalTargetAmount(goal)
  if (targetAmount <= 0) {
    return 0
  }

  return Math.min(
    100,
    Math.max(
      0,
      Math.round((getGoalCurrentAmount(goal) / targetAmount) * 100),
    ),
  )
}
