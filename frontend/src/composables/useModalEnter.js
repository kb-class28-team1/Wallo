import { onBeforeUnmount, onMounted } from 'vue'

// 모달 밖에 포커스가 있어도 Enter로 현재 모달의 기본 동작을 실행한다.
const handleModalEnter = (event) => {
  if (event.key !== 'Enter' || event.isComposing) return

  const dialogs = Array.from(document.querySelectorAll('[role="dialog"]'))
  const dialog = dialogs[dialogs.length - 1]
  if (!dialog) return

  const target = event.target
  const targetInsideDialog = target instanceof Element && dialog.contains(target)
  if (
    targetInsideDialog
    && target.matches('input, textarea, select, button, [contenteditable="true"]')
  ) {
    return
  }

  const actionButton = dialog.querySelector(
    '[data-modal-confirm], .modal-footer .btn-primary, .modal-footer .submit, .submit',
  ) || dialog.querySelector('[aria-label="닫기"], .btn-close')
  if (!actionButton || actionButton.disabled) return

  event.preventDefault()
  actionButton.click()
}

export const useModalEnter = () => {
  onMounted(() => window.addEventListener('keydown', handleModalEnter))
  onBeforeUnmount(() => window.removeEventListener('keydown', handleModalEnter))
}
