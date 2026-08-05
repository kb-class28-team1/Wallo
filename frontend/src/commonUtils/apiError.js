export const UNAUTHORIZED_MESSAGE = "로그인이 만료되었습니다. 다시 로그인해 주세요.";

export const getApiErrorMessage = (error, fallbackMessage) =>
  error?.response?.data?.error?.message ||
  error?.response?.data?.message ||
  (error?.response?.status === 401
    ? UNAUTHORIZED_MESSAGE
    : error?.message || fallbackMessage);

export const getApiErrorCode = (error) =>
  error?.response?.data?.error?.code ||
  error?.response?.data?.code ||
  null;
