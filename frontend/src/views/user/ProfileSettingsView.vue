<script setup>
import { computed, onMounted, ref } from "vue"
import { storeToRefs } from "pinia"
import { getApiErrorMessage } from "@/utils/apiError"
import AuthenticatedImage from "@/components/common/AuthenticatedImage.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppFormField from "@/components/ui/AppFormField.vue"
import AppState from "@/components/ui/AppState.vue"
import { useUserStore } from "@/stores/userStore"

const userStore = useUserStore()
const { user, profileImageUrl } = storeToRefs(userStore)
const isProfileLoading = ref(!user.value?.id)
const isProfileRefreshing = ref(false)
const hasLoadedProfile = ref(Boolean(user.value?.id))
const profileError = ref("")
const nicknameInput = ref(user.value?.nickname || "")
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

const isNicknameDirty = computed(() => nicknameInput.value.trim() !== (user.value?.nickname || ""))
const displayProfileImageUrl = computed(() => previewImageUrl.value || profileImageUrl.value)

const loadProfile = async ({ force = false } = {}) => {
  const isInitialLoad = !hasLoadedProfile.value
  const shouldSyncNickname = !isNicknameDirty.value
  isProfileLoading.value = isInitialLoad
  isProfileRefreshing.value = !isInitialLoad
  profileError.value = ""

  try {
    const profile = await userStore.fetchProfile({ force })
    if (shouldSyncNickname) {
      nicknameInput.value = profile?.nickname || ""
    }
    hasLoadedProfile.value = true
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
    isProfileRefreshing.value = false
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
  <AppCard
    as="section"
    class="settings-panel"
    padding="none"
    aria-labelledby="profile-settings-title"
  >
    <div v-if="isProfileRefreshing" class="profile-refresh-status" role="status">
      최신 프로필 정보를 확인하는 중...
    </div>

    <AppAlert
      v-if="profileError && user"
      class="profile-alert"
      variant="warning"
      :show-icon="false"
    >
      <div class="d-flex align-items-center justify-content-between gap-3">
        <span>{{ profileError }}</span>
        <AppButton variant="outline" size="sm" @click="loadProfile({ force: true })">
          다시 시도
        </AppButton>
      </div>
    </AppAlert>

    <AppState
      v-if="isProfileLoading"
      class="profile-state"
      type="loading"
      title="프로필 정보를 불러오는 중입니다."
      message="잠시만 기다려 주세요."
      compact
    />

    <AppState
      v-else-if="profileError && !user"
      class="profile-state"
      type="error"
      title="프로필 정보를 불러오지 못했습니다."
      :message="profileError"
      action-text="다시 시도"
      action-variant="danger"
      compact
      @action="loadProfile({ force: true })"
    />

    <div v-else class="profile-card-body">
      <h2 id="profile-settings-title" class="h5 fw-bold mb-4">프로필 편집</h2>

      <div
        class="profile-image-section d-flex flex-column flex-sm-row align-items-sm-center gap-3 mb-4"
      >
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
            <AppButton
              type="button"
              variant="outline"
              size="sm"
              :disabled="isProfileImageSaving"
              :loading="isProfileImageSaving"
              @click="chooseProfileImage"
            >
              사진 업로드
            </AppButton>
            <AppButton
              type="button"
              variant="secondary"
              size="sm"
              :disabled="isProfileImageSaving"
              :loading="isProfileImageSaving"
              @click="resetProfileImage"
            >
              기본 이미지
            </AppButton>
          </div>
          <input
            ref="profileImageInput"
            type="file"
            class="d-none"
            accept="image/jpeg,image/png"
            @change="handleProfileImageSelected"
          />
          <AppAlert
            v-if="profileImageError"
            class="profile-message"
            variant="danger"
            :message="profileImageError"
            :show-icon="false"
          />
          <AppAlert
            v-else-if="profileImageSavedMessage"
            class="profile-message"
            variant="success"
            :message="profileImageSavedMessage"
            :show-icon="false"
            role="status"
          />
        </div>
      </div>

      <form @submit.prevent="saveNickname">
        <div class="row g-3">
          <div class="col-12 col-md-6">
            <AppFormField
              id="profile-nickname"
              v-model="nicknameInput"
              label="닉네임"
              help-text="챌린지 피드에 표시되는 이름입니다. 50자 이하로 입력해 주세요."
              maxlength="50"
              :disabled="isNicknameSaving"
              autocomplete="nickname"
              @input="clearNicknameMessages"
            />
          </div>

          <div class="col-12 col-md-6">
            <AppFormField
              id="profile-name"
              label="이름"
              :model-value="user?.name || ''"
              disabled
              class="profile-readonly-field"
              help-text="실명은 수정할 수 없습니다."
            />
          </div>

          <div class="col-12">
            <AppFormField
              id="profile-email"
              label="이메일"
              type="email"
              :model-value="user?.email || ''"
              disabled
              class="profile-readonly-field"
              help-text="이메일은 수정할 수 없습니다."
            />
          </div>
        </div>

        <AppAlert
          v-if="nicknameError"
          class="profile-message"
          variant="danger"
          :message="nicknameError"
          :show-icon="false"
        />
        <AppAlert
          v-else-if="nicknameSavedMessage"
          class="profile-message"
          variant="success"
          :message="nicknameSavedMessage"
          :show-icon="false"
          role="status"
        />

        <div class="settings-actions d-flex flex-column-reverse flex-sm-row gap-2 mt-4 pt-4">
          <AppButton
            type="button"
            variant="secondary"
            class="flex-fill"
            :disabled="isNicknameSaving"
            @click="resetNickname"
          >
            취소
          </AppButton>
          <AppButton
            type="submit"
            variant="primary"
            class="flex-fill"
            :disabled="isNicknameSaving || !isNicknameDirty"
            :loading="isNicknameSaving"
          >
            저장하기
          </AppButton>
        </div>
      </form>
    </div>
  </AppCard>
</template>

<style scoped>
.settings-panel {
  min-height: 420px;
  border-radius: var(--wallo-radius-xl);
}

.profile-card-body {
  padding: var(--wallo-space-6);
}

.profile-state {
  min-height: 420px;
}

.profile-refresh-status {
  margin: var(--wallo-space-3);
  padding: var(--wallo-space-2) var(--wallo-space-3);
  color: var(--wallo-color-text-muted);
  border-radius: var(--wallo-radius-md);
  background: var(--wallo-color-info-bg);
  font-size: 0.85rem;
}

.profile-alert {
  margin: var(--wallo-space-3);
}

.profile-message {
  margin-top: var(--wallo-space-3);
}

.profile-image-section {
  padding-bottom: 24px;
  border-bottom: 1px solid #eef0f5;
}

.profile-image {
  width: 82px;
  height: 82px;
  object-fit: cover;
  background: var(--wallo-color-surface);
}

.profile-readonly-field:disabled {
  color: var(--wallo-color-text-muted);
  background-color: var(--wallo-color-surface-soft);
  border-color: var(--wallo-color-border-soft);
  opacity: 1;
  cursor: not-allowed;
}

.settings-actions {
  border-top: 1px solid var(--wallo-color-border-soft);
}

@media (max-width: 767.98px) {
  .profile-card-body {
    padding: var(--wallo-space-5);
  }
}
</style>
