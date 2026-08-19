const DEFAULT_RESOURCE_STALE_TIME = 60 * 1000
const defaultScope = {}
const createState = () => ({
  cache: new Map(),
  inFlight: new Map(),
})
const defaultState = createState()
const scopedStates = new WeakMap()

const isWeakMapKey = (scope) =>
  (typeof scope === "object" && scope !== null) || typeof scope === "function"

const getState = (scope = defaultScope) => {
  if (!isWeakMapKey(scope)) {
    return defaultState
  }

  if (!scopedStates.has(scope)) {
    scopedStates.set(scope, createState())
  }

  return scopedStates.get(scope)
}

const normalizeKey = (key) => String(key)

export const getCachedResource = (
  key,
  { scope = defaultScope, staleTime = DEFAULT_RESOURCE_STALE_TIME } = {},
) => {
  const state = getState(scope)
  const resourceKey = normalizeKey(key)
  const entry = state.cache.get(resourceKey)

  if (!entry) {
    return undefined
  }

  if (staleTime !== Infinity && Date.now() - entry.fetchedAt >= staleTime) {
    state.cache.delete(resourceKey)
    return undefined
  }

  return entry.value
}

export const setCachedResource = (key, value, { scope = defaultScope } = {}) => {
  const state = getState(scope)
  state.cache.set(normalizeKey(key), {
    value,
    fetchedAt: Date.now(),
  })

  return value
}

export const hasInFlightResource = (key, { scope = defaultScope } = {}) =>
  getState(scope).inFlight.has(normalizeKey(key))

export const invalidateResource = (key, { scope = defaultScope } = {}) => {
  getState(scope).cache.delete(normalizeKey(key))
}

export const clearResourceCache = ({ scope = defaultScope } = {}) => {
  const state = getState(scope)
  state.cache.clear()
  state.inFlight.clear()
}

export const getResource = (
  key,
  loader,
  { scope = defaultScope, force = false, staleTime = DEFAULT_RESOURCE_STALE_TIME } = {},
) => {
  const state = getState(scope)
  const resourceKey = normalizeKey(key)
  const inFlightRequest = state.inFlight.get(resourceKey)

  if (inFlightRequest) {
    return inFlightRequest
  }

  if (!force) {
    const cachedValue = getCachedResource(resourceKey, { scope, staleTime })
    if (cachedValue !== undefined) {
      return Promise.resolve(cachedValue)
    }
  }

  const request = Promise.resolve()
    .then(loader)
    .then((value) => {
      setCachedResource(resourceKey, value, { scope })
      return value
    })
    .finally(() => {
      if (state.inFlight.get(resourceKey) === request) {
        state.inFlight.delete(resourceKey)
      }
    })

  state.inFlight.set(resourceKey, request)
  return request
}

export { DEFAULT_RESOURCE_STALE_TIME }
