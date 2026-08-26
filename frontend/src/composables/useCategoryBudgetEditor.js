import { nextTick, onMounted, ref, unref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"

export const useCategoryBudgetEditor = ({
  canEditBudget,
  loadInitialData,
  saveCategoryBudgets,
}) => {
  const route = useRoute()
  const router = useRouter()
  const isBudgetEditorVisible = ref(false)

  const openBudgetEditor = async () => {
    if (!unref(canEditBudget)) {
      alert("예산은 현재 월에서만 수정할 수 있습니다.")
      return
    }

    isBudgetEditorVisible.value = true
    if (route.query.budget !== "edit") {
      await router.replace({
        query: {
          ...route.query,
          budget: "edit",
        },
      })
    }
  }

  const closeBudgetEditor = async () => {
    isBudgetEditorVisible.value = false
    if (route.query.budget === "edit") {
      const query = { ...route.query }
      delete query.budget
      await router.replace({ query })
    }
  }

  const saveBudget = async (request) => {
    try {
      await saveCategoryBudgets(request)
      await closeBudgetEditor()
    } catch {
      // The store handles the user-facing API error message.
    }
  }

  watch(
    () => route.query.budget,
    (budgetQuery) => {
      isBudgetEditorVisible.value = budgetQuery === "edit" && unref(canEditBudget)
    },
  )

  onMounted(async () => {
    await loadInitialData()
    if (route.query.budget === "edit") {
      await nextTick()
      isBudgetEditorVisible.value = unref(canEditBudget)
    }
  })

  return {
    isBudgetEditorVisible,
    openBudgetEditor,
    closeBudgetEditor,
    saveBudget,
  }
}
