document.addEventListener("click", (event) => {
  const loginRequiredAction = event.target.closest("[data-login-required]");
  if (!loginRequiredAction) return;

  window.alert("로그인 후 이용할 수 있습니다.");
}, true);
