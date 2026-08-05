const koreanNumberFormatter = new Intl.NumberFormat("ko-KR");

export const formatNumber = (value = 0) =>
  koreanNumberFormatter.format(Number(value) || 0);

export const formatWon = (value = 0) => `${formatNumber(value)}원`;
