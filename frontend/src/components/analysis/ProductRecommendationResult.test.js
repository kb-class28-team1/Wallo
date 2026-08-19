import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"
import ProductRecommendationResult from "./ProductRecommendationResult.vue"

const recommendation = {
  productType: "예금",
  products: [
    {
      productName: "안심 정기예금",
      companyName: "Wallo Bank",
      baseRatePercent: 2.5,
      preferentialRatePercent: 3.1,
    },
  ],
}

describe("ProductRecommendationResult", () => {
  it("renders at most three recommended products", () => {
    const wrapper = mount(ProductRecommendationResult, {
      props: {
        recommendation: {
          ...recommendation,
          products: Array.from({ length: 4 }, (_, index) => ({
            ...recommendation.products[0],
            productName: `Product ${index + 1}`,
          })),
        },
      },
    })

    expect(wrapper.findAll(".product-card")).toHaveLength(3)
    expect(wrapper.text()).toContain("Product 1")
    expect(wrapper.text()).toContain("Product 3")
    expect(wrapper.text()).not.toContain("Product 4")
  })

  it("shows an empty state when no product matches", () => {
    const wrapper = mount(ProductRecommendationResult, {
      props: {
        recommendation: {
          productType: "deposit",
          products: [],
        },
      },
    })

    expect(wrapper.find(".product-recommendation__empty").exists()).toBe(true)
    expect(wrapper.find(".product-recommendation__empty").attributes("role")).toBe("status")
    expect(wrapper.findAll(".product-card")).toHaveLength(0)
  })

  it("puts numbered preferential conditions on separate lines", () => {
    const wrapper = mount(ProductRecommendationResult, {
      props: {
        recommendation: {
          productType: "deposit",
          products: [
            {
              ...recommendation.products[0],
              preferentialConditions: "1. First condition 2. Second condition 2-1. Detail 3. Third condition",
            },
          ],
        },
      },
    })

    expect(wrapper.find(".product-card__conditions").exists()).toBe(true)
    const conditions = wrapper.find(".product-card__conditions p").element.textContent
    expect(conditions).toBe("1. First condition\n2. Second condition\n2-1. Detail\n3. Third condition")
  })

  it("puts circled-number preferential conditions on separate lines", () => {
    const wrapper = mount(ProductRecommendationResult, {
      props: {
        recommendation: {
          productType: "deposit",
          products: [
            {
              ...recommendation.products[0],
              preferentialConditions: "① First condition ② Second condition ③ Third condition",
            },
          ],
        },
      },
    })

    const conditions = wrapper.find(".product-card__conditions p").element.textContent
    expect(conditions).toBe("① First condition\n② Second condition\n③ Third condition")
  })

  it("renders the AI recommendation reason as sanitized markdown", () => {
    const wrapper = mount(ProductRecommendationResult, {
      props: {
        recommendation,
        reason: "**추천 근거**\n\n- 기본금리가 높아요\n- 우대조건을 확인했어요",
      },
    })

    const reason = wrapper.find(".product-recommendation__reason-markdown")
    expect(reason.find("strong").text()).toBe("추천 근거")
    expect(reason.findAll("li")).toHaveLength(2)
    expect(reason.text()).not.toContain("**")
  })

  it("removes unsafe HTML from the AI recommendation reason", () => {
    const wrapper = mount(ProductRecommendationResult, {
      props: {
        recommendation,
        reason: '<script>alert("xss")</script>\n\n**안전한 안내**',
      },
    })

    const reason = wrapper.find(".product-recommendation__reason-markdown")
    expect(reason.find("script").exists()).toBe(false)
    expect(reason.find("strong").text()).toBe("안전한 안내")
  })
})
