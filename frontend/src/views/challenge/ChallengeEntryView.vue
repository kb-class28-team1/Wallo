<script setup>
import { computed, onMounted, reactive, ref } from "vue"
import { useRouter } from "vue-router"
import { createChallenge, getCurrentChallenge, joinChallenge } from "@/api/challengeApi"
import AppDialog from "@/components/common/AppDialog.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppTabs from "@/components/ui/AppTabs.vue"
import { useToastStore } from "@/stores/toastStore"

const router = useRouter()
const toastStore = useToastStore()

const isLoading = ref(true)
const isSubmitting = ref(false)
const errorMessage = ref("")
const currentChallenge = ref(null)
const activeForm = ref("create")
const formTabs = [
  { value: "create", label: "챌린지 만들기" },
  { value: "join", label: "초대 코드로 참여" },
]
const dialogVisible = ref(false)
const dialogMessage = ref("")
const dialogNextRoute = ref(null)
const dialogImageSrc = ref("")
const dialogImageAlt = ref("")

const createForm = reactive({
  name: "",
})
const inviteCode = ref("")

const hasChallenge = computed(() => currentChallenge.value?.joined === true)

const showDialog = (message, nextRoute = null, imageSrc = "", imageAlt = "") => {
  dialogMessage.value = message
  dialogNextRoute.value = nextRoute
  dialogImageSrc.value = imageSrc
  dialogImageAlt.value = imageAlt
  dialogVisible.value = true
}

const closeDialog = async () => {
  const nextRoute = dialogNextRoute.value
  dialogNextRoute.value = null
  dialogVisible.value = false
  dialogImageSrc.value = ""
  dialogImageAlt.value = ""
  if (nextRoute) await router.push(nextRoute)
}

const loadCurrentChallenge = async () => {
  isLoading.value = true
  errorMessage.value = ""

  try {
    currentChallenge.value = await getCurrentChallenge()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    isLoading.value = false
  }
}

const submitCreate = async () => {
  const name = createForm.name.trim()
  if (!name) {
    showDialog("챌린지 이름을 입력해 주세요.")
    return
  }

  isSubmitting.value = true
  try {
    const createdChallenge = await createChallenge({
      name,
    })
    toastStore.show("챌린지가 만들어졌습니다.", { variant: "success" })
    await router.push({
      name: "challenge-feed",
      params: { challengeId: createdChallenge.id },
    })
  } catch (error) {
    showDialog(error.message)
  } finally {
    isSubmitting.value = false
  }
}

const submitJoin = async () => {
  const code = inviteCode.value.trim()
  if (!code) {
    showDialog("초대 코드를 입력해 주세요.")
    return
  }

  isSubmitting.value = true
  try {
    const joinedChallenge = await joinChallenge(code)
    showDialog("챌린지에 참여했습니다.", {
      name: "challenge-feed",
      params: { challengeId: joinedChallenge.id },
    })
  } catch (error) {
    const isInvalidInviteCode = error.message === "유효하지 않은 초대 코드입니다."
    showDialog(
      isInvalidInviteCode ? "코드가 맞는지 확인해주세요!" : error.message,
      null,
      isInvalidInviteCode ? "/images/profiles/challenge-missingcode.svg" : "",
      isInvalidInviteCode ? "초대 코드가 일치하지 않아 당황한 펭귄 이미지" : "",
    )
  } finally {
    isSubmitting.value = false
  }
}

const copyInviteCode = async () => {
  try {
    await navigator.clipboard.writeText(currentChallenge.value.inviteCode)
    showDialog("초대 코드가 복사되었습니다.")
  } catch {
    showDialog(`초대 코드: ${currentChallenge.value.inviteCode}`)
  }
}

const moveToFeed = () => {
  router.push(`/challenges/${currentChallenge.value.id}/feeds`)
}

onMounted(loadCurrentChallenge)
</script>

<template>
  <section class="challenge-entry" aria-labelledby="challenge-page-title">
    <div v-if="isLoading" class="state-card">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">챌린지 정보를 불러오는 중</span>
      </div>
      <p>챌린지 정보를 확인하고 있어요.</p>
    </div>

    <div v-else-if="errorMessage" class="state-card error-state">
      <span class="state-icon" aria-hidden="true">!</span>
      <h1 id="challenge-page-title">챌린지 정보를 불러오지 못했어요</h1>
      <p>{{ errorMessage }}</p>
      <button type="button" class="btn retry-button pressable" @click="loadCurrentChallenge">
        다시 시도
      </button>
    </div>

    <div v-else-if="hasChallenge" class="joined-page">
      <AppPageHeader
        title-id="challenge-page-title"
        :title="currentChallenge.name"
        eyebrow="MY SAVING CHALLENGE"
        description="함께 절약하고, 매주 달라지는 나의 기록을 확인해 보세요."
        align="center"
      >
        <template #actions>
          <span class="status-badge">
            <span class="status-dot"></span>
            진행 중
          </span>
        </template>
      </AppPageHeader>

      <div class="challenge-summary-card">
        <div class="summary-main">
          <div class="challenge-symbol" aria-hidden="true">💰</div>
          <div>
            <span class="summary-label">함께하는 절약</span>
            <strong>{{ currentChallenge.name }}</strong>
            <span class="challenge-number">Challenge #{{ currentChallenge.id }}</span>
          </div>
        </div>

        <div class="invite-panel">
          <span>친구 초대 코드</span>
          <div class="invite-code-row">
            <strong>{{ currentChallenge.inviteCode }}</strong>
            <button
              type="button"
              class="copy-button pressable"
              aria-label="초대 코드 복사"
              @click="copyInviteCode"
            >
              <i class="bi bi-copy"></i>
            </button>
          </div>
        </div>
      </div>

      <div class="challenge-actions">
        <button type="button" class="action-card action-primary pressable" @click="moveToFeed">
          <span class="action-icon"><i class="bi bi-card-list"></i></span>
          <span>
            <strong>챌린지 피드</strong>
            <small>오늘의 절약 기록을 남겨보세요</small>
          </span>
          <i class="bi bi-arrow-right"></i>
        </button>
        <button
          type="button"
          class="action-card pressable"
          @click="router.push('/challenges/rankings/weekly')"
        >
          <span class="action-icon"><i class="bi bi-trophy"></i></span>
          <span>
            <strong>주간 랭킹</strong>
            <small>이번 주 우리 챌린지 순위를 확인해요</small>
          </span>
          <i class="bi bi-arrow-right"></i>
        </button>
        <button
          type="button"
          class="action-card pressable"
          @click="router.push('/users/me/challenge-dashboard')"
        >
          <span class="action-icon"><i class="bi bi-graph-up-arrow"></i></span>
          <span>
            <strong>내 챌린지</strong>
            <small>나의 절약 현황을 한눈에 확인해요</small>
          </span>
          <i class="bi bi-arrow-right"></i>
        </button>
      </div>
    </div>

    <div v-else class="not-joined-page">
      <AppPageHeader
        class="entry-hero"
        title-id="challenge-page-title"
        title="작은 절약을 모아 큰 목표를 만들어 보세요"
        eyebrow="SAVE TOGETHER, GROW TOGETHER"
        description="새로운 챌린지를 만들거나 친구에게 받은 초대 코드로 바로 시작할 수 있어요."
      >
        <template #title>
          작은 절약을 모아<br />
          <span class="entry-hero-title-accent">큰 목표</span>를 만들어 보세요
        </template>
      </AppPageHeader>

      <div class="entry-card">
        <AppTabs
          v-model="activeForm"
          class="form-tabs"
          :items="formTabs"
          variant="segment"
          full-width
          aria-label="챌린지 시작 방법"
        />

        <form v-if="activeForm === 'create'" class="challenge-form" @submit.prevent="submitCreate">
          <img
            class="entry-form-image"
            src="/images/profiles/challenge-make.svg"
            alt="펭귄과 로봇이 챌린지를 만드는 모습"
          />
          <div class="form-copy">
            <span class="form-step">01</span>
            <div>
              <h2>나만의 절약 챌린지 만들기</h2>
              <p>함께 절약할 챌린지의 이름을 정해 주세요.</p>
            </div>
          </div>

          <label class="field-label" for="challenge-name">챌린지 이름</label>
          <input
            id="challenge-name"
            v-model="createForm.name"
            type="text"
            class="form-control challenge-input"
            maxlength="20"
            placeholder="예: 한 달 식비 30만 원 도전"
            :disabled="isSubmitting"
          />

          <button type="submit" class="submit-button pressable" :disabled="isSubmitting">
            <span v-if="isSubmitting" class="spinner-border spinner-border-sm"></span>
            <span v-else>챌린지 만들기</span>
          </button>
        </form>

        <form v-else class="challenge-form" @submit.prevent="submitJoin">
          <img
            class="entry-form-image"
            src="/images/profiles/challenge-code.svg"
            alt="초대 코드를 들고 있는 펭귄 이미지"
          />
          <div class="form-copy">
            <span class="form-step">02</span>
            <div>
              <h2>친구의 챌린지에 참여하기</h2>
              <p>전달받은 초대 코드를 그대로 입력해 주세요.</p>
            </div>
          </div>

          <label class="field-label" for="invite-code">초대 코드</label>
          <input
            id="invite-code"
            v-model="inviteCode"
            type="text"
            class="form-control challenge-input invite-input"
            maxlength="20"
            autocomplete="off"
            placeholder="예: PGM-7X2K9"
            :disabled="isSubmitting"
            @input="inviteCode = inviteCode.toUpperCase()"
          />

          <div class="invite-help">
            <i class="bi bi-info-circle"></i>
            초대 코드는 챌린지를 만든 친구에게 받을 수 있어요.
          </div>

          <button type="submit" class="submit-button pressable" :disabled="isSubmitting">
            <span v-if="isSubmitting" class="spinner-border spinner-border-sm"></span>
            <span v-else>챌린지 참여하기</span>
          </button>
        </form>
      </div>
    </div>

    <AppDialog
      :visible="dialogVisible"
      title="챌린지 안내"
      :message="dialogMessage"
      :image-src="dialogImageSrc"
      :image-alt="dialogImageAlt"
      @confirm="closeDialog"
      @close="closeDialog"
    />
  </section>
</template>

<style scoped>
.challenge-entry {
  width: 100%;
  min-height: 0;
  color: #202840;
}

.state-card {
  min-height: 520px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 48px;
  background: #fff;
  border: 1px solid #eceef5;
  border-radius: 28px;
  box-shadow: 0 16px 42px rgba(37, 44, 82, 0.07);
}

.state-card p {
  margin: 0;
  color: #8b93a9;
  font-weight: 650;
}

.error-state h1 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 800;
}

.state-icon {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  color: #fff;
  background: #4f8fe8;
  border-radius: 50%;
  font-size: 1.5rem;
  font-weight: 900;
}

.retry-button {
  padding: 11px 22px;
  color: #fff;
  background: #4f8fe8;
  border-radius: 12px;
  font-weight: 700;
}

.entry-hero :deep(.app-page-header__description) {
  max-width: 430px;
  line-height: 1.75;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.eyebrow {
  display: block;
  margin-bottom: 12px;
  color: #5c8fd5;
  font-size: 0.78rem;
  font-weight: 850;
  letter-spacing: 0.14em;
}

.page-heading h1,
.entry-hero h1 {
  margin: 0;
  color: #1e2538;
  font-size: clamp(2rem, 4vw, 3.35rem);
  font-weight: 900;
  letter-spacing: -0.055em;
}

.page-heading p,
.entry-hero p {
  margin: 14px 0 0;
  color: #8c94a8;
  font-size: 1rem;
  font-weight: 600;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 15px;
  color: #3c8063;
  background: #effaf5;
  border-radius: 999px;
  font-size: 0.86rem;
  font-weight: 800;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: #48b887;
  border-radius: 50%;
}

.challenge-summary-card {
  display: grid;
  grid-template-columns: 1fr minmax(260px, 0.45fr);
  gap: 28px;
  padding: 34px;
  background: linear-gradient(135deg, #4d89d3 0%, #86b3e8 100%);
  border-radius: 26px;
  box-shadow: 0 18px 42px rgba(100, 88, 201, 0.2);
}

.summary-main {
  display: flex;
  align-items: center;
  gap: 22px;
  color: #fff;
}

.challenge-symbol {
  width: 76px;
  height: 76px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 22px;
  font-size: 2rem;
}

.summary-label,
.challenge-number {
  display: block;
  color: rgba(255, 255, 255, 0.72);
  font-size: 0.82rem;
  font-weight: 700;
}

.summary-main strong {
  display: block;
  margin: 5px 0;
  font-size: 1.55rem;
  font-weight: 850;
}

.invite-panel {
  padding: 20px 24px;
  color: #4f7fc8;
  background: #fff;
  border-radius: 18px;
}

.invite-panel > span {
  display: block;
  margin-bottom: 8px;
  color: #9a9fb1;
  font-size: 0.78rem;
  font-weight: 700;
}

.invite-code-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.invite-code-row strong {
  font-size: 1.35rem;
  letter-spacing: 0.08em;
}

.copy-button {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  color: #5d8fd5;
  background: #ebf4ff;
  border: 0;
  border-radius: 11px;
}

.challenge-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin-top: 24px;
}

.action-card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 16px;
  padding: 24px;
  text-align: left;
  color: #30384e;
  background: #fff;
  border: 1px solid #eceef5;
  border-radius: 20px;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.action-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 14px 30px rgba(39, 45, 77, 0.1);
}

.action-card:active:not(:disabled) {
  transform: translateY(-1px) scale(0.98);
}

.action-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  color: #5d8fd5;
  background: #ebf4ff;
  border-radius: 13px;
  font-size: 1.1rem;
}

.action-card strong,
.action-card small {
  display: block;
}

.action-card strong {
  margin-bottom: 5px;
  font-size: 1rem;
}

.action-card small {
  color: #9a9fb0;
  line-height: 1.45;
}

.action-primary {
  color: #fff;
  background: #282e45;
  border-color: #282e45;
}

.action-primary .action-icon {
  color: #282e45;
  background: #fff;
}

.action-primary small {
  color: #bfc4d1;
}

.not-joined-page {
  display: grid;
  grid-template-columns: minmax(280px, 0.8fr) minmax(460px, 1.2fr);
  gap: 70px;
  align-items: center;
  min-height: calc(100vh - var(--wallo-header-height));
  box-sizing: border-box;
  padding: 0px 18px;
}

.entry-hero h1 {
  line-height: 1.2;
}

.entry-hero h1 span {
  color: #5c8fd5;
}

.entry-hero p {
  max-width: 430px;
  line-height: 1.75;
}

.entry-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border: 1px solid #eceef5;
  border-radius: 26px;
  box-shadow: 0 20px 50px rgba(37, 44, 82, 0.09);
}

.challenge-form {
  display: flex;
  height: 545px;
  flex-direction: column;
  padding: 36px;
  box-sizing: border-box;
}

.entry-form-image {
  display: block;
  width: 100%;
  height: 150px;
  margin: -10px auto 24px;
  object-fit: contain;
  /* 이미지 원본의 미세한 아이보리 배경이 카드와 분리되어 보이지 않도록 보정 */
  filter: brightness(1.03);
}

:deep(.app-dialog-image) {
  /* 생성 완료 이미지도 생성 화면과 같은 밝기 기준으로 표시 */
  filter: brightness(1.03);
}

.form-copy {
  display: flex;
  gap: 16px;
  margin-bottom: 30px;
}

.form-step {
  width: 42px;
  height: 42px;
  display: grid;
  flex: 0 0 42px;
  place-items: center;
  color: #5c8fd5;
  background: #e9f3ff;
  border-radius: 13px;
  font-size: 0.8rem;
  font-weight: 850;
}

.form-copy h2 {
  margin: 0 0 6px;
  font-size: 1.25rem;
  font-weight: 850;
}

.form-copy p {
  margin: 0;
  color: #9a9fb0;
  font-size: 0.9rem;
}

.field-label {
  display: block;
  margin: 20px 0 9px;
  color: #4b5265;
  font-size: 0.86rem;
  font-weight: 800;
}

.challenge-input {
  min-height: 54px;
  padding: 0 17px;
  background-color: #f8f9fc;
  border: 1px solid #eceef4;
  border-radius: 13px;
  font-weight: 650;
}

.challenge-input:focus {
  background-color: #fff;
  border-color: #8aaee0;
  box-shadow: 0 0 0 4px rgba(118, 104, 218, 0.1);
}

.invite-input {
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.invite-help {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 12px;
  color: #989eaf;
  font-size: 0.82rem;
  font-weight: 650;
}

.submit-button {
  width: 100%;
  min-height: 54px;
  margin-top: auto;
  color: #fff;
  background: #4f8fe8;
  border: 0;
  border-radius: 14px;
  font-weight: 800;
  box-shadow: 0 10px 22px rgba(112, 98, 220, 0.22);
}

.submit-button:disabled {
  opacity: 0.65;
}

@media (max-width: 991.98px) {
  .not-joined-page {
    min-height: auto;
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .challenge-actions {
    grid-template-columns: 1fr;
  }

  .challenge-form {
    height: auto;
  }
}

@media (max-width: 767.98px) {
  .challenge-entry :deep(.app-page-header) {
    align-items: flex-start;
    flex-direction: column;
  }

  .challenge-entry :deep(.app-page-header__actions) {
    align-self: flex-start;
  }

  .challenge-summary-card {
    grid-template-columns: 1fr;
    padding: 24px;
  }

  .not-joined-page {
    padding: 0 0 12px;
  }

  .challenge-form {
    height: auto;
    padding: 26px 22px;
  }

  .entry-form-image {
    height: 132px;
    margin-top: -4px;
    margin-bottom: 20px;
  }

  .submit-button {
    margin-top: 28px;
  }
}
</style>
