const koreanNumberFormatter = new Intl.NumberFormat("ko-KR");

const millionAmountPattern = /(^|[^A-Za-z0-9])(\d+(?:[.,]\d+)?)\s*[mM](?:illion)?(?:\s*원)?(?=$|[^A-Za-z0-9])/g;

export const formatNumber = (value = 0) =>
  koreanNumberFormatter.format(Number(value) || 0);

export const formatWon = (value = 0) => `${formatNumber(value)}원`;

export const formatRoadmapText = (value) => {
  if (typeof value !== "string") return value;

  return value.replace(
    millionAmountPattern,
    (_, prefix, amount) => `${prefix}${amount}백만원`,
  );
};
