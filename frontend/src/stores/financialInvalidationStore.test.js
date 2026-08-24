import { createPinia, setActivePinia } from "pinia"
import { beforeEach, describe, expect, it } from "vitest"
import { useFinancialInvalidationStore } from "./financialInvalidationStore"

describe("financialInvalidationStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it("increments the shared revision whenever financial data changes", () => {
    const store = useFinancialInvalidationStore()

    expect(store.revision).toBe(0)
    expect(store.lastChangedAt).toBe(0)

    const revision = store.markChanged()

    expect(revision).toBe(1)
    expect(store.revision).toBe(1)
    expect(store.lastChangedAt).toBeGreaterThan(0)
  })
})
