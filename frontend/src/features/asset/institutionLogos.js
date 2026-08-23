const LOCAL_LOGO_PATHS = Object.freeze({
  KB: "/images/institutions/kb.webp",
  HANA: "/images/institutions/hana.webp",
  SHINHAN: "/images/institutions/shinhan.webp",
});

const LOCAL_LOGO_KEYWORDS = [
  ["KB", ["KB", "국민"]],
  ["HANA", ["HANA", "하나"]],
  ["SHINHAN", ["SHINHAN", "신한"]],
];

export const getLocalInstitutionLogo = (groupCode, groupName) => {
  const normalizedText = `${groupCode || ""} ${groupName || ""}`.toUpperCase();
  const matchedEntry = LOCAL_LOGO_KEYWORDS.find(([, keywords]) => (
    keywords.some((keyword) => normalizedText.includes(keyword.toUpperCase()))
  ));

  return matchedEntry ? LOCAL_LOGO_PATHS[matchedEntry[0]] : "";
};
