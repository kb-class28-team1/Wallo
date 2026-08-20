<script setup>
import { onMounted, ref } from "vue"

const canvas = ref(null)

const BALL_COLORS = [
  "#f4d76e",
  "#f49bc0",
  "#9bd9bd",
  "#b7dff4",
  "#c6a1db",
  "#f0b5d2",
  "#a4c4ee",
  "#d5e8d2",
]
const BALL_COLUMNS = 10
const BALL_ROWS = 8
const BALL_COUNT = BALL_COLUMNS * BALL_ROWS
const BALL_RADIUS = 0.12
const FLOOR_Y = 0.72

let context = null
let canvasWidth = 280
let canvasHeight = 280

const randomBetween = (min, max) => min + Math.random() * (max - min)

const createPile = () => {
  const pile = []

  for (let index = 0; index < BALL_COUNT; index += 1) {
    const row = Math.floor(index / BALL_COLUMNS)
    const column = index % BALL_COLUMNS
    const stagger = row % 2 === 1 ? 0.1 : 0

    // 공 지름보다 촘촘한 엇갈림 배치라서 바닥과 양옆에 빈틈이 생기지 않음.
    pile.push({
      x: -0.9 + column * 0.2 + stagger + randomBetween(-0.008, 0.008),
      y: FLOOR_Y - 0.012 - row * 0.16 + randomBetween(-0.006, 0.006),
      z: randomBetween(-0.42, 0.42),
    })
  }

  return pile
}

const resizeCanvas = () => {
  if (!canvas.value || !context) return

  const rect = canvas.value.getBoundingClientRect()
  canvasWidth = rect.width || 280
  canvasHeight = rect.height || 280
  const devicePixelRatio = Math.min(window.devicePixelRatio || 1, 2)

  canvas.value.width = Math.round(canvasWidth * devicePixelRatio)
  canvas.value.height = Math.round(canvasHeight * devicePixelRatio)
  context.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0)
}

const hexToRgb = (hex) => {
  const value = hex.replace("#", "")
  const normalized = value.length === 3 ? value.split("").map((part) => part + part).join("") : value
  const number = Number.parseInt(normalized, 16)

  return {
    red: (number >> 16) & 255,
    green: (number >> 8) & 255,
    blue: number & 255,
  }
}

const drawBall = (ball, index) => {
  // 모든 공의 화면상 크기도 동일하게 유지하고, z축은 겹침 순서와 위치감에만 사용함.
  const depthScale = 1 / (1 - ball.z * 0.18)
  const centerX = canvasWidth / 2 + ball.x * canvasWidth * 0.43 * depthScale
  const centerY = canvasHeight / 2 + ball.y * canvasHeight * 0.43 * depthScale
  const radius = BALL_RADIUS * canvasWidth * 0.48
  const rgb = hexToRgb(BALL_COLORS[index % BALL_COLORS.length])

  context.save()
  context.globalAlpha = 0.96
  context.shadowColor = "rgb(0 0 0 / 30%)"
  context.shadowBlur = radius * 0.28
  context.shadowOffsetY = radius * 0.2

  const gradient = context.createRadialGradient(
    centerX - radius * 0.34,
    centerY - radius * 0.42,
    radius * 0.05,
    centerX,
    centerY,
    radius,
  )
  gradient.addColorStop(0, "rgb(255 255 255 / 94%)")
  gradient.addColorStop(0.2, `rgb(${rgb.red} ${rgb.green} ${rgb.blue} / 1)`)
  gradient.addColorStop(0.82, `rgb(${rgb.red} ${rgb.green} ${rgb.blue} / 0.96)`)
  gradient.addColorStop(
    1,
    `rgb(${Math.max(0, rgb.red - 45)} ${Math.max(0, rgb.green - 45)} ${Math.max(0, rgb.blue - 45)} / 1)`,
  )

  context.beginPath()
  context.arc(centerX, centerY, radius, 0, Math.PI * 2)
  context.fillStyle = gradient
  context.fill()

  context.shadowColor = "transparent"
  context.lineWidth = Math.max(1, radius * 0.07)
  context.strokeStyle = "rgb(255 255 255 / 64%)"
  context.stroke()

  context.restore()
}

const drawPile = () => {
  if (!context) return

  context.clearRect(0, 0, canvasWidth, canvasHeight)
  const pile = createPile().sort((first, second) => first.z - second.z)
  pile.forEach(drawBall)
}

const start = () => {
  // jsdom에는 canvas 2D context가 없으므로 테스트에서는 DOM만 렌더링함.
  if (import.meta.env.MODE === "test" || !canvas.value) return

  try {
    context = canvas.value.getContext("2d")
  } catch {
    context = null
  }
  if (!context) return

  resizeCanvas()
  drawPile()
}

onMounted(() => {
  start()
})
</script>

<template>
  <canvas ref="canvas" class="point-draw-machine-canvas" aria-hidden="true"></canvas>
</template>

<style scoped>
.point-draw-machine-canvas {
  position: absolute;
  z-index: 1;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
}
</style>
