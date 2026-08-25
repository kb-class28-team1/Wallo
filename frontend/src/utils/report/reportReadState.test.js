import { beforeEach, describe, expect, it } from "vitest"
import { getReadReportIds, isReportRead, markReportAsRead } from "./reportReadState"

describe("reportReadState", () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it("marks a report as read in localStorage", () => {
    markReportAsRead(10, 1)

    expect(isReportRead(10, 1)).toBe(true)
    expect(isReportRead(10, 2)).toBe(false)
    expect(getReadReportIds(1)).toEqual(new Set(["10"]))
  })

  it("deduplicates repeated reads", () => {
    markReportAsRead(10, 1)
    markReportAsRead("10", 1)

    expect([...getReadReportIds(1)]).toEqual(["10"])
  })

  it("returns an empty set when stored data is broken", () => {
    window.localStorage.setItem("wallo.readReportIds:1", "{broken")

    expect(getReadReportIds(1)).toEqual(new Set())
  })

  it("does not share read state between users", () => {
    markReportAsRead(10, 1)

    expect(getReadReportIds(2)).toEqual(new Set())
  })
})
