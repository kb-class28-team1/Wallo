<script setup>
import { computed, onMounted, ref } from "vue"
import { storeToRefs } from "pinia"
import { getApiErrorMessage } from "@/commonUtils/apiError"
import AuthenticatedImage from "@/components/common/AuthenticatedImage.vue"
import { useUserStore } from "@/stores/userStore"

const userStore = useUserStore()
const { user, profileImageUrl } = storeToRefs(userStore)
const isProfileLoading = ref(false)
const profileError = ref("")
const nicknameInput = ref("")
const nicknameError = ref("")
const nicknameSavedMessage = ref("")
const isNicknameSaving = ref(false)
const profileImageInput = ref(null)
const previewImageUrl = ref("")
const profileImageError = ref("")
const profileImageSavedMessage = ref("")
const isProfileImageSaving = ref(false)

const MAX_PROFILE_IMAGE_SIZE = 5 * 1024 * 1024
const ALLOWED_PROFILE_IMAGE_TYPES = ["image/jpeg", "image/png"]

const isNicknameDirty = computed(
  () => nicknameInput.value.trim() !== (user.value?.nickname || ""),
)
const displayProfileImageUrl = computed(
  () => previewImageUrl.value || profileImageUrl.value,
)

const loadProfile = async () => {
  isProfileLoading.value = true
  profileError.value = ""

  try {
    const profile = await userStore.fetchProfile()
    nicknameInput.value = profile?.nickname || ""
  } catch (error) {
    if (error.status === 401) {
      // 401은 Axios 전역 인터셉터가 인증 상태 초기화와 로그인 이동을 담당한다.
      return
    }

    profileError.value = getApiErrorMessage(
      error,
      "프로필 정보를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
    )
  } finally {
    isProfileLoading.value = false
  }
}

const clearNicknameMessages = () => {
  nicknameError.value = ""
  nicknameSavedMessage.value = ""
}

const resetNickname = () => {
  nicknameInput.value = user.value?.nickname || ""
  clearNicknameMessages()
}

const saveNickname = async () => {
  const nickname = nicknameInput.value.trim()
  clearNicknameMessages()

  if (!nickname) {
    nicknameError.value = "닉네임을 입력해 주세요."
    return
  }

  if (nickname.length > 50) {
    nicknameError.value = "닉네임은 50자 이하로 입력해 주세요."
    return
  }

  if (!isNicknameDirty.value) {
    return
  }

  isNicknameSaving.value = true
  try {
    await userStore.updateNickname(nickname)
    nicknameInput.value = user.value?.nickname || nickname
    nicknameSavedMessage.value = "닉네임이 저장되었습니다."
  } catch (error) {
    nicknameError.value = error.message || "닉네임을 저장하지 못했습니다."
  } finally {
    isNicknameSaving.value = false
  }
}

const clearProfileImagePreview = () => {
  if (previewImageUrl.value) {
    URL.revokeObjectURL(previewImageUrl.value)
    previewImageUrl.value = ""
  }
}

const chooseProfileImage = () => {
  profileImageInput.value?.click()
}

const saveProfileImage = async (file) => {
  profileImageError.value = ""
  profileImageSavedMessage.value = ""

  if (!file) {
    return
  }

  if (!ALLOWED_PROFILE_IMAGE_TYPES.includes(file.type)) {
    profileImageError.value = "JPG 또는 PNG 이미지 파일만 업로드할 수 있습니다."
    return
  }

  if (file.size > MAX_PROFILE_IMAGE_SIZE) {
    profileImageError.value = "프로필 이미지는 5MB 이하만 업로드할 수 있습니다."
    return
  }

  clearProfileImagePreview()
  previewImageUrl.value = URL.createObjectURL(file)
  isProfileImageSaving.value = true

  try {
    await userStore.updateProfileImage(file)
    clearProfileImagePreview()
    profileImageSavedMessage.value = "프로필 이미지가 저장되었습니다."
  } catch (error) {
    clearProfileImagePreview()
    profileImageError.value = error.message || "프로필 이미지를 저장하지 못했습니다."
  } finally {
    isProfileImageSaving.value = false
  }
}

const handleProfileImageSelected = async (event) => {
  const file = event.target.files?.[0]
  event.target.value = ""
  await saveProfileImage(file)
}

const resetProfileImage = async () => {
  profileImageError.value = ""
  profileImageSavedMessage.value = ""
  isProfileImageSaving.value = true

  try {
    await userStore.resetProfileImage()
    clearProfileImagePreview()
    profileImageSavedMessage.value = "프로필 이미지가 기본 이미지로 변경되었습니다."
  } catch (error) {
    profileImageError.value = error.message || "기본 프로필 이미지로 변경하지 못했습니다."
  } finally {
    isProfileImageSaving.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <section class="settings-panel card border-0 shadow-sm" aria-labelledby="profile-settings-title">
    <div v-if="isProfileLoading" class="profile-state text-center" aria-live="polite">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">프로필 정보를 불러오는 중</span>
      </div>
      <p class="text-secondary mb-0 mt-3">프로필 정보를 불러오고 있습니다.</p>
    </div>

    <div v-else-if="profileError" class="profile-state text-center">
      <i class="bi bi-exclamation-circle text-danger fs-2" aria-hidden="true"></i>
      <p class="fw-semibold mb-1 mt-3">프로필 정보를 불러오지 못했습니다.</p>
      <p class="small text-secondary mb-3">{{ profileError }}</p>
      <button type="button" class="btn btn-outline-danger" @click="loadProfile">
        다시 시도
      </button>
    </div>

    <div v-else class="card-body p-4 p-md-5">
      <h2 id="profile-settings-title" class="h5 fw-bold mb-4">프로필 편집</h2>

      <div class="profile-image-section d-flex flex-column flex-sm-row align-items-sm-center gap-3 mb-4">
        <AuthenticatedImage
          :src="displayProfileImageUrl"
          alt="프로필 이미지"
          class="profile-image rounded-circle"
        />
        <div>
          <p class="fw-semibold mb-1">프로필 사진</p>
          <p class="small text-secondary mb-2">
            JPG, PNG · 5MB 이하<br />랭킹과 챌린지 피드에 함께 노출돼요.
          </p>
          <div class="d-flex flex-wrap gap-2">
            <button
              type="button"
              class="btn btn-outline-secondary btn-sm"
              :disabled="isProfileImageSaving"
              @click="chooseProfileImage"
            >
              <span
                v-if="isProfileImageSaving"
                class="spinner-border spinner-border-sm me-1"
                aria-hidden="true"
              ></span>
              사진 업로드
            </button>
            <button
              type="button"
              class="btn btn-outline-secondary btn-sm"
              :disabled="isProfileImageSaving"
              @click="resetProfileImage"
            >
              기본 이미지
            </button>
          </div>
          <input
            ref="profileImageInput"
            type="file"
            class="d-none"
            accept="image/jpeg,image/png"
            @change="handleProfileImageSelected"
          />
          <div v-if="profileImageError" class="alert alert-danger py-2 mt-3 mb-0" role="alert">
            {{ profileImageError }}
          </div>
          <div
            v-else-if="profileImageSavedMessage"
            class="alert alert-success py-2 mt-3 mb-0"
            role="status"
          >
            {{ profileImageSavedMessage }}
          </div>
        </div>
      </div>

      <form @submit.prevent="saveNickname">
        <div class="row g-3">
          <div class="col-12 col-md-6">
            <label for="profile-nickname" class="form-label">닉네임</label>
            <input
              id="profile-nickname"
              v-model="nicknameInput"
              type="text"
              class="form-control"
              maxlength="50"
              autocomplete="nickname"
              :disabled="isNicknameSaving"
              @input="clearNicknameMessages"
            />
            <div class="form-text">챌린지와 피드에 표시되는 이름입니다. 50자 이하로 입력해 주세요.</div>
          </div>

          <div class="col-12 col-md-6">
            <label for="profile-name" class="form-label">이름</label>
            <input
              id="profile-name"
              :value="user?.name || ''"
              type="text"
              class="form-control profile-readonly-field"
              disabled
            />
            <div class="form-text">실명은 수정할 수 없습니다.</div>
          </div>

          <div class="col-12">
            <label for="profile-email" class="form-label">이메일</label>
            <input
              id="profile-email"
              :value="user?.email || ''"
              type="email"
              class="form-control profile-readonly-field"
              disabled
            />
            <div class="form-text">이메일은 수정할 수 없습니다.</div>
          </div>
        </div>

        <div v-if="nicknameError" class="alert alert-danger py-2 mt-3 mb-0" role="alert">
          {{ nicknameError }}
        </div>
        <div v-else-if="nicknameSavedMessage" class="alert alert-success py-2 mt-3 mb-0" role="status">
          {{ nicknameSavedMessage }}
        </div>

        <div class="settings-actions d-flex flex-column-reverse flex-sm-row gap-2 mt-4 pt-4">
          <button
            type="button"
            class="btn btn-light flex-fill"
            :disabled="isNicknameSaving"
            @click="resetNickname"
          >
            취소
          </button>
          <button
            type="submit"
            class="btn btn-primary flex-fill"
            :disabled="isNicknameSaving || !isNicknameDirty"
          >
            <span
              v-if="isNicknameSaving"
              class="spinner-border spinner-border-sm me-2"
              aria-hidden="true"
            ></span>
            {{ isNicknameSaving ? "저장 중..." : "저장하기" }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.settings-panel {
  min-height: 420px;
  border-radius: 20px;
  background: #ffffff;
}

.profile-state {
  display: flex;
  min-height: 420px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.profile-image-section {
  padding-bottom: 24px;
  border-bottom: 1px solid #eef0f5;
}

.profile-image {
  width: 82px;
  height: 82px;
  object-fit: cover;
  background: #f0efff;
}

.profile-readonly-field:disabled {
  color: #7f8ba0;
  background-color: #f8fafc;
  border-color: #e4e9f1;
  opacity: 1;
  cursor: not-allowed;
}

.settings-actions {
  border-top: 1px solid #eef0f5;
}
</style>
