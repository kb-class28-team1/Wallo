<script setup>
import { storeToRefs } from "pinia"
import { useUserStore } from "@/stores/userStore"

const userStore = useUserStore()
const { user, profileImageUrl } = storeToRefs(userStore)
</script>

<template>
  <section class="settings-panel card border-0 shadow-sm" aria-labelledby="profile-settings-title">
    <div class="card-body p-4 p-md-5">
      <h2 id="profile-settings-title" class="h5 fw-bold mb-4">프로필 편집</h2>

      <div class="profile-image-section d-flex flex-column flex-sm-row align-items-sm-center gap-3 mb-4">
        <img
          :src="profileImageUrl"
          alt="프로필 이미지"
          class="profile-image rounded-circle"
          @error="userStore.useDefaultProfileImage"
        />
        <div>
          <p class="fw-semibold mb-1">프로필 사진</p>
          <p class="small text-secondary mb-2">
            JPG, PNG · 5MB 이하<br />현재는 기본 프로필 이미지를 사용합니다.
          </p>
          <div class="d-flex flex-wrap gap-2">
            <button type="button" class="btn btn-outline-secondary btn-sm" disabled>
              사진 업로드
            </button>
            <button type="button" class="btn btn-outline-secondary btn-sm" disabled>
              기본 이미지로
            </button>
          </div>
        </div>
      </div>

      <div class="row g-3">
        <div class="col-12 col-md-6">
          <label for="profile-nickname" class="form-label">닉네임</label>
          <input
            id="profile-nickname"
            :value="user?.nickname || ''"
            type="text"
            class="form-control"
            readonly
          />
          <div class="form-text">닉네임 수정 API 연결 후 편집할 수 있습니다.</div>
        </div>

        <div class="col-12 col-md-6">
          <label for="profile-name" class="form-label">이름</label>
          <input
            id="profile-name"
            :value="user?.name || ''"
            type="text"
            class="form-control"
            readonly
          />
          <div class="form-text">실명은 현재 읽기 전용입니다.</div>
        </div>

        <div class="col-12">
          <label for="profile-email" class="form-label">이메일</label>
          <input
            id="profile-email"
            :value="user?.email || ''"
            type="email"
            class="form-control"
            readonly
          />
          <div class="form-text">로그인에 사용하는 이메일입니다.</div>
        </div>
      </div>

      <div class="settings-actions d-flex flex-column-reverse flex-sm-row gap-2 mt-4 pt-4">
        <button type="button" class="btn btn-light flex-fill" disabled>취소</button>
        <button type="button" class="btn btn-primary flex-fill" disabled>저장하기</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.settings-panel {
  border-radius: 20px;
  background: #ffffff;
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

.settings-actions {
  border-top: 1px solid #eef0f5;
}
</style>
