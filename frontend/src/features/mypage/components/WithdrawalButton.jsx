import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { withdrawUser } from '../api/myPageApi';

export default function WithdrawalButton() {
  const moveUrl = useNavigate();
  const dialog = useRef(null);
  const trigger = useRef(null);
  // [USR-04] 비밀번호는 모달에서만 보관하며 닫으면 지운다. 저장소/로그에 기록하지 않는다.
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) dialog.current.showModal();
  }, [open]);

  const closeHandler = () => {
    if (pending) return;
    dialog.current.close();
    setOpen(false); setPassword(''); setError('');
    trigger.current.focus();
  };

  const withdrawalHandler = e => {
    e.preventDefault();

    if (!password.trim()) {
      setError('비밀번호를 입력해주세요.');
      return;
    }

    setPending(true);
    setError('');

    withdrawUser(password)
      .then(response => {
        if (response.status !== 202) {
          throw new Error('Unexpected withdrawal response');
        }

        // 202는 탈퇴 완료가 아닌 요청 접수를 의미한다.
        window.alert('탈퇴 요청이 접수됐어요.');

        localStorage.removeItem('at');
        localStorage.removeItem('rt');
        localStorage.removeItem('userId');

        moveUrl('/login', { replace: true });
      })
      .catch(requestError => {
        const status = requestError.response?.status;
        const code = requestError.response?.data?.code;

        if (code === 'WITHDRAWAL_ALREADY_PENDING' || status === 409) {
          setError('회원 탈퇴가 이미 진행 중입니다.');
        } else if (code === 'USER_ALREADY_WITHDRAWN') {
          setError('이미 탈퇴한 회원입니다.');
        } else if (status === 400) {
          setError('비밀번호를 확인해주세요.');
        } else if (status === 401) {
          setError('로그인이 만료되었습니다. 다시 로그인한 후 시도해주세요.');
        } else {
          setError('탈퇴 요청을 접수하지 못했습니다. 잠시 후 다시 시도해주세요.');
        }
      })
      .finally(() => setPending(false));
  };

  return <>
    <button type="button" ref={trigger} className="mypage-withdraw-trigger" onClick={() => setOpen(true)}>회원탈퇴</button>
    {open && <dialog ref={dialog} className="mypage-withdraw-dialog" aria-labelledby="withdraw-title" onCancel={e => { e.preventDefault(); closeHandler(); }}>
      <h2 id="withdraw-title">회원탈퇴</h2>
      <p>
        탈퇴 요청이 접수되면 로그아웃됩니다.<br />
        실제 탈퇴는 매치 정리가 끝난 뒤 완료됩니다.<br />
        계속하려면 현재 비밀번호를 입력해주세요.
      </p>
      <form onSubmit={withdrawalHandler} aria-busy={pending}>
        <label htmlFor="withdraw-password">현재 비밀번호</label>
        <input id="withdraw-password" type="password" autoComplete="current-password" autoFocus value={password} disabled={pending} onChange={e => setPassword(e.target.value)} />
        {error && <p className="mypage-error" role="alert">{error}</p>}
        <div className="mypage-actions"><button type="button" disabled={pending} onClick={closeHandler}>취소</button><button type="submit" disabled={pending} className="mypage-danger">{pending ? '탈퇴 처리 중...' : '회원탈퇴 확인'}</button></div>
      </form>
    </dialog>}
  </>;
}
