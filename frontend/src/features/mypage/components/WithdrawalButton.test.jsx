import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import WithdrawalButton from './WithdrawalButton';
import { withdrawUser } from '../api/myPageApi';
jest.mock('../api/myPageApi', () => ({ withdrawUser: jest.fn() }));
function Location() { return <p data-testid="path">{useLocation().pathname}</p>; }
beforeAll(() => {
  jest.spyOn(window, 'alert').mockImplementation(() => {});
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
});
beforeEach(() => { localStorage.setItem('at', 'test'); localStorage.setItem('rt', 'test'); localStorage.setItem('userId', '7'); });
afterEach(() => { localStorage.clear(); jest.clearAllMocks(); });
const show = () => render(<MemoryRouter initialEntries={['/mypage']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><WithdrawalButton /><Location /></MemoryRouter>);
test('버튼 클릭만으로 탈퇴하지 않으며 접수 202 후 세션을 지우고 로그인으로 이동한다', async () => {
  withdrawUser.mockResolvedValue({ status: 202 });
  show();
  fireEvent.click(screen.getByRole('button', { name: '회원탈퇴', exact: true }));
  expect(withdrawUser).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('현재 비밀번호'), { target: { value: 'password' } });
  fireEvent.click(screen.getByRole('button', { name: '회원탈퇴 확인' }));
  await screen.findByText('/login');
  expect(window.alert).toHaveBeenCalledWith('탈퇴 요청이 접수됐어요.');
  expect(localStorage.getItem('at')).toBeNull();
  expect(localStorage.getItem('rt')).toBeNull();
  expect(localStorage.getItem('userId')).toBeNull();
});
test('비밀번호 오류 시 세션은 유지하고 닫았다 열면 입력은 비운다', async () => {
  withdrawUser.mockRejectedValue({ response: { status: 400 } }); show();
  fireEvent.click(screen.getByRole('button', { name: '회원탈퇴', exact: true }));
  fireEvent.change(screen.getByLabelText('현재 비밀번호'), { target: { value: 'wrong' } });
  fireEvent.click(screen.getByRole('button', { name: '회원탈퇴 확인' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('비밀번호를 확인');
  expect(localStorage.getItem('at')).toBe('test');
  fireEvent.click(screen.getByRole('button', { name: '취소' }));
  fireEvent.click(screen.getByRole('button', { name: '회원탈퇴', exact: true }));
  expect(screen.getByLabelText('현재 비밀번호')).toHaveValue('');
});

test('탈퇴 진행 중 409는 별도 안내하고 세션을 유지한다', async () => {
  withdrawUser.mockRejectedValue({
    response: {
      status: 409,
      data: { code: 'WITHDRAWAL_ALREADY_PENDING' }
    }
  });

  show();

  fireEvent.click(screen.getByRole('button', { name: '회원탈퇴', exact: true }));
  fireEvent.change(screen.getByLabelText('현재 비밀번호'), {
    target: { value: 'password' }
  });
  fireEvent.click(screen.getByRole('button', { name: '회원탈퇴 확인' }));

  expect(await screen.findByRole('alert'))
    .toHaveTextContent('회원 탈퇴가 이미 진행 중입니다.');
  expect(localStorage.getItem('at')).toBe('test');
  expect(localStorage.getItem('rt')).toBe('test');
  expect(localStorage.getItem('userId')).toBe('7');
  expect(screen.getByTestId('path')).toHaveTextContent('/mypage');
});
