const LOCAL_LOGO_PATHS = Object.freeze({
  KB: "/images/institutions/kb.webp",
  HANA: "/images/institutions/hana.webp",
  SHINHAN: "/images/institutions/shinhan.webp",
  SAVINGS_BANK: "/images/institutions/cheongju-savings-bank.png",
});

const LOCAL_LOGO_KEYWORDS = [
  ["KB", ["KB", "국민"]],
  ["HANA", ["HANA", "하나"]],
  ["SHINHAN", ["SHINHAN", "신한"]],
  ["SAVINGS_BANK", ["SAVINGS_BANK", "저축은행", "청주저축은행"]],
];

export const getLocalInstitutionLogo = (groupCode, groupName) => {
  const normalizedText = `${groupCode || ""} ${groupName || ""}`.toUpperCase();
  const matchedEntry = LOCAL_LOGO_KEYWORDS.find(([, keywords]) => (
    keywords.some((keyword) => normalizedText.includes(keyword.toUpperCase()))
  ));

  return matchedEntry ? LOCAL_LOGO_PATHS[matchedEntry[0]] : "";
};

export const getInstitutionLogoFallbackClass = (logoUrl) => (logoUrl ? "d-none" : "");

export const handleInstitutionLogoError = (event) => {
  const fallbackSrc = event.target.dataset.fallbackSrc;
  const currentSrc = event.target.getAttribute("src");

  if (fallbackSrc && currentSrc !== fallbackSrc) {
    event.target.src = fallbackSrc;
    return;
  }

  event.target.classList.add("d-none");
  event.target.nextElementSibling?.classList.remove("d-none");
};
