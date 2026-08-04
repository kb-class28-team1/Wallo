<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue"
import { useRoute } from "vue-router"
import { useUserStore } from "@/stores/userStore"
import { formatWon } from "@/utils/formatters"
import {
  analyzeFeed, createFeed, getFeeds, getRoomMessages, sendRoomMessage,
} from "@/api/feedApi"

const route = useRoute()
const userStore = useUserStore()
const challengeId = computed(() => Number(route.params.challengeId))
const focusedFeedId = computed(() => String(route.query.focusFeedId || ""))
const feeds = ref([])
const messages = ref([])
const challengeName = ref("챌린지")
const mySavingTotal = ref(0)
const activeTab = ref(String(route.query.scope || "ALL").toUpperCase() === "ME" ? "mine" : "all")
const isLoading = ref(true)
const errorMessage = ref("")
const modalOpen = ref(false)
const isAnalyzing = ref(false)
const isUploading = ref(false)
const chatInput = ref("")
const mentionedFeed = ref(null)
const fileInput = ref(null)
const previewUrl = ref("")
const focusedFeedElement = ref(null)
let refreshTimer

const form = reactive({
  file: null,
  spendingType: "",
  category: "",
  customCategory: "",
  caption: "",
  savingAmount: 0,
  analysisSummary: "",
  confidenceScore: 0,
})

const spendingTypes = [
  { value: "SPENT", label: "💸 썼다" },
  { value: "REDUCED", label: "✂️ 줄였다" },
  { value: "SAVED", label: "🐷 모았다" },
]
const categories = [
  { value: "COFFEE", label: "☕ 커피" },
  { value: "DELIVERY", label: "🛵 배달" },
  { value: "TRANSPORT", label: "🚌 교통" },
  { value: "GROCERY", label: "🛒 장보기" },
  { value: "DINING", label: "🍚 외식" },
  { value: "CUSTOM", label: "✏️ 직접 입력" },
]
const categoryLabel = (value, custom) =>
  custom || categories.find((item) => item.value === value)?.label.replace(/^.. /, "") || value
const spendingLabel = (value) =>
  spendingTypes.find((item) => item.value === value)?.label || value
const isVideoFile = computed(() => form.file?.type?.startsWith("video/"))
const roomTitle = computed(() => `${challengeName.value} 채팅방`)

// 내 게시물에서 전달한 feedId와 현재 피드의 id가 같은지 확인함.
const isFocusedFeed = (feed) => String(feed.id) === focusedFeedId.value
const getFeedCardClass = (feed) => (isFocusedFeed(feed) ? "focused-feed" : "")
const setFocusedFeedElement = (element, feed) => {
  if (isFocusedFeed(feed)) {
    focusedFeedElement.value = element
  }
}

// 피드 조회와 화면 렌더링이 끝난 뒤 선택한 게시물 위치로 부드럽게 이동함.
const scrollToFocusedFeed = async () => {
  if (!focusedFeedId.value || isLoading.value) {
    return
  }

  await nextTick()
  focusedFeedElement.value?.scrollIntoView({
    behavior: "smooth",
    block: "center",
  })
}

const loadFeeds = async () => {
  const data = await getFeeds(challengeId.value, activeTab.value === "mine")
  feeds.value = data.feeds
  challengeName.value = data.challengeName
  mySavingTotal.value = data.mySavingTotal
}
const loadMessages = async () => {
  const data = await getRoomMessages(challengeId.value)
  messages.value = data.messages
  challengeName.value = data.challengeName
}
const loadPage = async () => {
  isLoading.value = true
  errorMessage.value = ""
  try {
    await Promise.all([loadFeeds(), loadMessages()])
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    isLoading.value = false
  }
}
const changeTab = async (tab) => {
  activeTab.value = tab
  try { await loadFeeds() } catch (error) { errorMessage.value = error.message }
}
const openModal = () => { modalOpen.value = true }
const closeModal = () => {
  modalOpen.value = false
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ""
  Object.assign(form, {
    file: null, spendingType: "", category: "", customCategory: "",
    caption: "", savingAmount: 0, analysisSummary: "", confidenceScore: 0,
  })
  if (fileInput.value) fileInput.value.value = ""
}
const chooseFile = () => fileInput.value?.click()
const handleFile = (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith("image/") && !file.type.startsWith("video/")) {
    alert("사진 또는 영상 파일을 선택해 주세요.")
    return
  }
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  form.file = file
  previewUrl.value = URL.createObjectURL(file)
  form.analysisSummary = ""
}
const validationMessage = () => {
  if (!form.file) return "사진이나 영상을 선택해 주세요."
  if (!form.spendingType) return "소비 종류를 선택해 주세요."
  if (!form.category) return "세부 카테고리를 선택해 주세요."
  if (form.category === "CUSTOM" && !form.customCategory.trim())
    return "직접 입력할 카테고리를 작성해 주세요."
  return ""
}
const makeFormData = () => {
  const data = new FormData()
  data.append("media", form.file)
  data.append("spendingType", form.spendingType)
  data.append("category", form.category)
  return data
}
const requestAnalysis = async () => {
  const invalid = validationMessage()
  if (invalid) return alert(invalid)
  isAnalyzing.value = true
  try {
    const result = await analyzeFeed(challengeId.value, makeFormData())
    form.savingAmount = result.estimatedSavingAmount
    form.analysisSummary = result.summary
    form.confidenceScore = result.confidenceScore
  } catch (error) {
    alert(error.message)
  } finally {
    isAnalyzing.value = false
  }
}
const uploadFeed = async () => {
  const invalid = validationMessage()
  if (invalid) return alert(invalid)
  if (!form.analysisSummary) return alert("먼저 AI 분석을 진행해 주세요.")
  isUploading.value = true
  try {
    const data = makeFormData()
    data.append("customCategory", form.customCategory)
    data.append("caption", form.caption)
    data.append("savingAmount", String(form.savingAmount))
    data.append("analysisSummary", form.analysisSummary)
    data.append("confidenceScore", String(form.confidenceScore))
    await createFeed(challengeId.value, data)
    closeModal()
    await Promise.all([loadFeeds(), loadMessages()])
  } catch (error) {
    alert(error.message)
  } finally {
    isUploading.value = false
  }
}
const mentionFeed = (message) => {
  mentionedFeed.value = {
    id: message.referenceFeedId,
    mediaUrl: message.thumbnailUrl || message.mediaUrl,
    mediaType: message.mediaType,
  }
  chatInput.value = `@피드${message.referenceFeedId} `
}
const sendMessage = async () => {
  if (!chatInput.value.trim()) return
  try {
    await sendRoomMessage(challengeId.value, {
      content: chatInput.value.trim(),
      referenceFeedId: mentionedFeed.value?.id || null,
    })
    chatInput.value = ""
    mentionedFeed.value = null
    await loadMessages()
  } catch (error) {
    alert(error.message)
  }
}

watch([focusedFeedId, feeds, isLoading], scrollToFocusedFeed, { flush: "post" })

onMounted(async () => {
  await loadPage()
  refreshTimer = window.setInterval(() => loadMessages().catch(() => {}), 5000)
})
onBeforeUnmount(() => {
  window.clearInterval(refreshTimer)
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <section class="feed-page">
    <div v-if="isLoading" class="page-state">
      <div class="spinner-border text-primary"></div><p>챌린지 피드를 불러오고 있어요.</p>
    </div>
    <div v-else-if="errorMessage" class="page-state">
      <strong>{{ errorMessage }}</strong>
      <button class="btn btn-primary" @click="loadPage">다시 시도</button>
    </div>
    <template v-else>
      <header class="feed-header">
        <div>
          <span>MY SAVING FEED</span>
          <h1>{{ challengeName }}</h1>
          <p>함께 남긴 절약 기록을 확인하고 응원해 보세요.</p>
        </div>
        <div class="saving-total"><small>나의 누적 절약 금액</small><strong>{{ formatWon(mySavingTotal) }}</strong></div>
      </header>

      <div class="feed-layout">
        <main class="feed-column">
          <nav class="feed-tabs">
            <button
              :class="{ active: activeTab === &quot;all&quot; }"
              @click="changeTab(&quot;all&quot;)"
            >
              전체 피드
            </button>
            <button
              :class="{ active: activeTab === &quot;mine&quot; }"
              @click="changeTab(&quot;mine&quot;)"
            >
              내 피드
            </button>
          </nav>
          <div v-if="!feeds.length" class="empty-feed">
            <span>📷</span><strong>아직 등록된 피드가 없어요</strong>
            <p>오른쪽 아래 + 버튼을 눌러 첫 절약 기록을 남겨보세요.</p>
          </div>
          <article
            v-for="feed in feeds"
            :key="feed.id"
            :ref="(element) => setFocusedFeedElement(element, feed)"
            class="feed-card"
            :class="getFeedCardClass(feed)"
            :data-feed-id="feed.id"
          >
            <span v-if="isFocusedFeed(feed)" class="focus-badge">선택한 게시물</span>
            <header>
              <img
                :src="feed.profileImageUrl || &quot;/images/profiles/default-profile.svg&quot;"
                alt=""
              />
              <div><strong>{{ feed.nickname }}</strong><small>{{ spendingLabel(feed.spendingType) }} · {{ categoryLabel(feed.category, feed.customCategory) }}</small></div>
              <span class="saving-badge">+ {{ formatWon(feed.savingAmount) }}</span>
            </header>
            <video
              v-if="feed.mediaType === &quot;VIDEO&quot;"
              :src="feed.mediaUrl"
              controls
              preload="metadata"
            ></video>
            <img
              v-else
              class="feed-media"
              :src="feed.mediaUrl"
              :alt="feed.caption || &quot;절약 인증 사진&quot;"
            />
            <footer>
              <p>{{ feed.caption || "오늘의 절약 기록을 공유했어요." }}</p>
              <span>🤖 AI 분석 완료 · 절약 금액 {{ formatWon(feed.savingAmount) }}</span>
            </footer>
          </article>
        </main>

        <aside class="chat-room">
          <header><span class="online-dot"></span><div><h2>{{ roomTitle }}</h2><small>피드와 이야기를 함께 나눠요</small></div></header>
          <div class="messages">
            <div v-for="item in messages" :key="item.id" class="message" :class="{ mine: item.userId === userStore.user?.id }">
              <strong>{{ item.nickname }}</strong>
              <button v-if="item.referenceFeedId" class="shared-feed" @click="mentionFeed(item)">
                <video v-if="item.mediaType === &quot;VIDEO&quot;" :src="item.mediaUrl" muted></video>
                <img v-else :src="item.thumbnailUrl || item.mediaUrl" alt="공유 피드 썸네일" />
                <span><b>피드 #{{ item.referenceFeedId }}</b><small>눌러서 언급하기</small></span>
              </button>
              <p>{{ item.content }}</p>
            </div>
          </div>
          <div v-if="mentionedFeed" class="mention-preview">
            <span>피드 #{{ mentionedFeed.id }} 언급 중</span>
            <button @click="mentionedFeed = null">×</button>
          </div>
          <form class="chat-form" @submit.prevent="sendMessage">
            <input v-model="chatInput" placeholder="메시지 보내기..." />
            <button aria-label="메시지 전송">↑</button>
          </form>
        </aside>
      </div>
      <button class="floating-add" aria-label="절약 피드 추가" @click="openModal">+</button>
    </template>

    <div v-if="modalOpen" class="modal-layer" @click.self="closeModal">
      <section class="upload-modal" role="dialog" aria-modal="true" aria-labelledby="upload-title">
        <header><h2 id="upload-title">절약 피드 추가</h2><button @click="closeModal">×</button></header>
        <div class="modal-body">
          <label class="section-label">인증 자료</label>
          <input ref="fileInput" class="visually-hidden" type="file" accept="image/*,video/*" @change="handleFile" />
          <button class="upload-zone" type="button" @click="chooseFile">
            <template v-if="previewUrl">
              <video v-if="isVideoFile" :src="previewUrl" muted></video>
              <img v-else :src="previewUrl" alt="업로드 미리보기" />
            </template>
            <template v-else><span>🖼️</span><strong>사진 / 동영상 업로드</strong><small>클릭해 인증 사진 또는 영상을 올려주세요</small></template>
          </button>

          <label class="section-label">소비 종류</label>
          <div class="chip-row">
            <button v-for="item in spendingTypes" :key="item.value" type="button"
              :class="{ selected: form.spendingType === item.value }" @click="form.spendingType = item.value">{{ item.label }}</button>
          </div>
          <label class="section-label">세부 카테고리</label>
          <div class="chip-row">
            <button v-for="item in categories" :key="item.value" type="button"
              :class="{ selected: form.category === item.value }" @click="form.category = item.value">{{ item.label }}</button>
          </div>
          <input
            v-if="form.category === &quot;CUSTOM&quot;"
            v-model="form.customCategory"
            class="custom-input"
            maxlength="50"
            placeholder="카테고리를 직접 입력해 주세요"
          />

          <div class="analysis-box">
            <div><b>🤖 AI 분석</b><span>선택 정보와 미디어를 외부 AI 분석기로 전달합니다.</span></div>
            <button type="button" :disabled="isAnalyzing" @click="requestAnalysis">
              {{ isAnalyzing ? "분석 중..." : "✨ AI에게 분석 맡기기" }}
            </button>
          </div>
          <div class="result-box" :class="{ ready: form.analysisSummary }">
            <span>🤖 AI 추정</span><small>{{ form.analysisSummary || "분석하면 예상 절약 금액을 알려드려요." }}</small>
            <div><input v-model.number="form.savingAmount" type="number" min="0" :disabled="!form.analysisSummary" /><b>원</b></div>
          </div>
          <label class="section-label" for="feed-caption">문구</label>
          <textarea id="feed-caption" v-model="form.caption" maxlength="500" placeholder="예) 퇴근길 편의점 대신 집에서 커피 ☕ 굿!"></textarea>
          <p class="share-notice">💬 업로드하면 {{ roomTitle }}에도 자동으로 공유돼요.</p>
        </div>
        <footer><button class="cancel" @click="closeModal">취소</button><button class="submit" :disabled="isUploading" @click="uploadFeed">{{ isUploading ? "올리는 중..." : "피드 올리기" }}</button></footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.feed-page{min-height:calc(100vh - 130px);color:#202840}.page-state{min-height:520px;display:grid;place-content:center;justify-items:center;gap:18px}.feed-header{display:flex;justify-content:space-between;align-items:end;margin-bottom:24px}.feed-header>div>span{color:#7164de;font-size:.76rem;font-weight:900;letter-spacing:.14em}.feed-header h1{margin:8px 0 4px;font-size:2rem;font-weight:900}.feed-header p{margin:0;color:#939bad}.saving-total{min-width:210px;padding:15px 20px;background:#f0edff;border-radius:16px}.saving-total small,.saving-total strong{display:block}.saving-total small{color:#8e87ba}.saving-total strong{margin-top:4px;color:#6758d6;font-size:1.35rem}.feed-layout{display:grid;grid-template-columns:minmax(0,1fr) 330px;gap:22px;align-items:start}.feed-column{min-width:0}.feed-tabs{display:flex;gap:6px;margin-bottom:14px;padding:5px;background:#f0eff7;border-radius:13px;width:max-content}.feed-tabs button{padding:10px 22px;border:0;border-radius:10px;color:#8b91a3;background:transparent;font-weight:800}.feed-tabs button.active{color:#fff;background:#6f61dc}.empty-feed{display:grid;place-items:center;min-height:430px;background:#fff;border:1px dashed #d9dbe8;border-radius:22px;text-align:center}.empty-feed span{font-size:2.4rem}.empty-feed strong{margin-top:-80px}.empty-feed p{margin-top:-100px;color:#999fb0}.feed-card{position:relative;overflow:hidden;margin-bottom:18px;border:2px solid transparent;background:#121d3e;border-radius:22px;box-shadow:0 14px 32px #29315a1f;transition:border-color 180ms ease,box-shadow 180ms ease,transform 180ms ease}.feed-card.focused-feed{border-color:#8d80ff;box-shadow:0 0 0 5px #8d80ff2e,0 18px 38px #29315a35;transform:translateY(-2px);animation:focus-pulse 900ms ease-out}.focus-badge{position:absolute;top:12px;right:16px;z-index:2;padding:5px 10px;color:#fff;background:#796bea;border-radius:999px;font-size:.72rem;font-weight:850}.feed-card header{display:flex;align-items:center;gap:12px;padding:16px 18px;color:#fff}.feed-card header img{width:42px;height:42px;object-fit:cover;border-radius:50%;background:#fff}.feed-card header div{flex:1}.feed-card header strong,.feed-card header small{display:block}.feed-card header small{color:#aeb8d4}.saving-badge{padding:7px 11px;color:#dcd7ff;background:#ffffff18;border-radius:999px;font-size:.82rem;font-weight:800}.feed-media,.feed-card>video{display:block;width:100%;max-height:560px;object-fit:contain;background:#09122d}.feed-card footer{padding:15px 18px 18px;color:#fff}.feed-card footer p{margin:0 0 8px;font-weight:700}.feed-card footer span{color:#aeb8d4;font-size:.8rem}.chat-room{position:sticky;top:18px;display:flex;flex-direction:column;height:calc(100vh - 175px);min-height:560px;overflow:hidden;color:#e7eaff;background:#111a36;border-radius:22px}.chat-room>header{display:flex;align-items:center;gap:10px;padding:18px;border-bottom:1px solid #ffffff14}.chat-room h2{margin:0;font-size:1rem}.chat-room header small{color:#8d96b7}.online-dot{width:9px;height:9px;background:#65d49a;border-radius:50%}.messages{flex:1;overflow:auto;padding:16px}.message{margin-bottom:14px}.message>strong{display:block;margin-bottom:4px;color:#8bdfbc;font-size:.75rem}.message.mine>strong{color:#f6cf75}.message p{margin:5px 0;font-size:.86rem;line-height:1.45}.shared-feed{display:flex;width:100%;gap:10px;align-items:center;padding:8px;color:#fff;text-align:left;background:#202b4d;border:1px solid #ffffff12;border-radius:11px}.shared-feed img,.shared-feed video{width:56px;height:50px;object-fit:cover;border-radius:8px}.shared-feed span{min-width:0}.shared-feed b,.shared-feed small{display:block}.shared-feed small{margin-top:3px;color:#939dbc}.mention-preview{display:flex;justify-content:space-between;padding:8px 14px;color:#c7c1ff;background:#27224c;font-size:.76rem}.mention-preview button{color:#fff;background:transparent;border:0}.chat-form{display:flex;gap:7px;padding:12px;background:#172140}.chat-form input{min-width:0;flex:1;padding:11px 13px;color:#fff;background:#222d4d;border:0;border-radius:12px;outline:0}.chat-form button,.floating-add{display:grid;place-items:center;color:#fff;background:#7162de;border:0;border-radius:50%;font-weight:800}.chat-form button{width:40px}.floating-add{position:fixed;right:34px;bottom:30px;z-index:10;width:58px;height:58px;font-size:2rem;box-shadow:0 10px 28px #6658cf66}.modal-layer{position:fixed;inset:0;z-index:1100;display:grid;place-items:center;padding:20px;background:#13172b99;backdrop-filter:blur(3px)}.upload-modal{display:flex;flex-direction:column;width:min(600px,100%);max-height:92vh;overflow:hidden;background:#fff;border-radius:26px;box-shadow:0 25px 80px #0004}.upload-modal>header{display:flex;justify-content:space-between;align-items:center;padding:24px 28px 8px}.upload-modal h2{margin:0;font-size:1.4rem;font-weight:900}.upload-modal>header button{font-size:2rem;color:#9ba2b7;background:transparent;border:0}.modal-body{overflow:auto;padding:12px 28px 20px}.section-label{display:block;margin:16px 0 9px;font-size:.88rem;font-weight:850}.upload-zone{display:grid;place-items:center;width:100%;height:170px;overflow:hidden;background:#fafbfe;border:2px dashed #d9dcec;border-radius:18px}.upload-zone>span{font-size:2rem}.upload-zone strong,.upload-zone small{display:block}.upload-zone small{color:#9ba2b5}.upload-zone img,.upload-zone video{width:100%;height:100%;object-fit:contain}.chip-row{display:flex;flex-wrap:wrap;gap:9px}.chip-row button{padding:9px 14px;background:#f7f7fb;border:1px solid #e5e5f0;border-radius:999px;font-weight:750}.chip-row button.selected{color:#6557d5;background:#eeebff;border-color:#8a7ee8}.custom-input,textarea{width:100%;margin-top:10px;padding:13px;border:1px solid #dedfeb;border-radius:13px}.analysis-box{margin-top:20px;padding:17px;background:#f3f0ff;border:1px solid #d8d1ff;border-radius:18px}.analysis-box div{display:flex;gap:8px;margin-bottom:12px}.analysis-box span{color:#737a90;font-size:.82rem}.analysis-box button{width:100%;padding:13px;color:#fff;background:linear-gradient(90deg,#705ef0,#bd36f5);border:0;border-radius:13px;font-weight:850}.result-box{margin-top:12px;padding:15px;background:#f6f6fa;border-radius:16px}.result-box>span{font-weight:800}.result-box>small{margin-left:8px;color:#969caf}.result-box>div{display:flex;align-items:center;gap:8px;margin-top:10px}.result-box input{width:100%;padding:12px;border:1px solid #dedfeb;border-radius:12px}.result-box.ready{background:#f0fff7}.result-box.ready>span{color:#328665}textarea{min-height:84px;resize:vertical}.share-notice{padding:10px;margin:12px 0 0;color:#737a90;background:#f6f6fa;border-radius:11px;font-size:.8rem}.upload-modal>footer{display:grid;grid-template-columns:1fr 2fr;gap:12px;padding:16px 28px 24px}.upload-modal>footer button{padding:14px;border-radius:13px;font-weight:850}.cancel{color:#687086;background:#fff;border:1px solid #dedfeb}.submit{color:#fff;background:#6d5ddd;border:0}.submit:disabled,.analysis-box button:disabled{opacity:.55}@keyframes focus-pulse{from{box-shadow:0 0 0 12px #8d80ff35}to{box-shadow:0 0 0 5px #8d80ff2e,0 18px 38px #29315a35}}@media(max-width:1000px){.feed-layout{grid-template-columns:1fr}.chat-room{position:relative;top:0;height:600px}.floating-add{right:20px;bottom:20px}}@media(max-width:650px){.feed-header{align-items:start;flex-direction:column;gap:15px}.saving-total{width:100%}.modal-layer{padding:0}.upload-modal{height:100%;max-height:none;border-radius:0}}
</style>
