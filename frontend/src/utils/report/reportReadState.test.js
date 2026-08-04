import { beforeEach, describe, expect, it } from "vitest"
import { getReadReportIds, isReportRead, markReportAsRead } from "./reportReadState"

describe("reportReadState", () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it("marks a report as read in localStorage", () => {
    markReportAsRead(10)

    expect(isReportRead(10)).toBe(true)
    expect(getReadReportIds()).toEqual(new Set(["10"]))
  })

  it("deduplicates repeated reads", () => {
    markReportAsRead(10)
    markReportAsRead("10")

    expect([...getReadReportIds()]).toEqual(["10"])
  })

  it("returns an empty set when stored data is broken", () => {
    window.localStorage.setItem("wallo.readReportIds", "{broken")

    expect(getReadReportIds()).toEqual(new Set())
  })
})
