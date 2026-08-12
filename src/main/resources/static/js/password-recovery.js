const recoveryForm = document.getElementById('password-recovery-form');

if (recoveryForm) {
  const emailInput = document.getElementById('recovery-email');
  const sendButton = document.getElementById('recovery-code-send-button');
  const emailMessage = document.getElementById('recovery-email-message');
  const codeGroup = document.getElementById('recovery-code-group');
  const codeInput = document.getElementById('recovery-code');
  const verifyButton = document.getElementById('recovery-code-verify-button');
  const codeMessage = document.getElementById('recovery-code-message');

  function csrfHeaders() {
    const csrfInput = recoveryForm.querySelector('input[name="_csrf"]');
    return csrfInput ? {'X-CSRF-TOKEN': csrfInput.value} : {};
  }

  async function requestVerification(url, body) {
    const response = await fetch(url, {
      method: 'POST',
      headers: {'Content-Type': 'application/json', ...csrfHeaders()},
      body: JSON.stringify(body)
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.message || '이메일 인증 처리 중 오류가 발생했습니다.');
    }
    return data;
  }

  recoveryForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const email = emailInput.value.trim();
    if (!emailInput.checkValidity()) {
      emailInput.reportValidity();
      return;
    }

    sendButton.disabled = true;
    emailMessage.className = 'form-help';
    try {
      const data = await requestVerification(
              '/email-verifications/password-reset/send', {email});
      emailMessage.innerText = data.message;
      emailMessage.className = 'form-success';
      codeGroup.hidden = false;
      codeInput.focus();
      window.setTimeout(() => sendButton.disabled = false, 60000);
    } catch (error) {
      emailMessage.innerText = error.message;
      emailMessage.className = 'form-error';
      sendButton.disabled = false;
    }
  });

  emailInput.addEventListener('input', () => {
    codeGroup.hidden = true;
    codeInput.value = '';
    emailMessage.innerText = '';
    codeMessage.innerText = '';
  });

  verifyButton.addEventListener('click', async () => {
    const email = emailInput.value.trim();
    const code = codeInput.value.trim();
    if (!/^\d{6}$/.test(code)) {
      codeMessage.innerText = '6자리 인증번호를 입력해 주세요.';
      codeMessage.className = 'form-error';
      return;
    }

    verifyButton.disabled = true;
    try {
      const data = await requestVerification(
              '/email-verifications/password-reset/verify', {email, code});
      codeMessage.innerText = data.message;
      codeMessage.className = 'form-success';
      window.location.assign('/reset-password');
    } catch (error) {
      codeMessage.innerText = error.message;
      codeMessage.className = 'form-error';
      verifyButton.disabled = false;
    }
  });
}
