// 커뮤니티 목록 화면에 부하를 건다. M1(댓글 수 세기) 을 재는 것이 목적이다.
//
// 같은 화면을 카테고리만 바꿔 두 번 돌린다. 두 조회는 정렬·쪽·OFFSET 이 모두 같고
// 다른 것은 1쪽에 오는 글들의 댓글 수뿐이다(측정용 데이터가 그렇게 심겨 있다).
//
//   카테고리 1 : 글 20개, 각각 댓글 500  ← 무거운 쪽
//   카테고리 3 : 글 20개, 댓글 없음      ← 대조군
//
// 실행:
//   k6 run -e CATEGORY=1 monitoring/k6/community-list.js
//   k6 run -e CATEGORY=3 monitoring/k6/community-list.js
//
// 두 번을 동시에 돌리지 않는다. 같은 CPU 를 나눠 쓰게 되어 비교가 깨진다.

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8081';
const CATEGORY = __ENV.CATEGORY || '1';

export const options = {
    scenarios: {
        ramp: {
            // 도착률을 올린다. 요청을 '보내는 속도'를 우리가 정하는 방식이다.
            //
            // constant-vus 같은 방식은 서버가 느려지면 다음 요청을 늦게 보낸다.
            // 그러면 서버가 힘들어할수록 부하가 저절로 줄어서 한계가 안 드러난다.
            // 도착률 방식은 서버 사정과 무관하게 계속 보내므로 무너지는 지점이 보인다.
            executor: 'ramping-arrival-rate',
            startRate: 50,
            timeUnit: '1s',
            preAllocatedVUs: 100,
            maxVUs: 800,
            stages: [
                { target: 50, duration: '30s' },   // 워밍업. 이 구간 값은 버린다
                { target: 100, duration: '30s' },
                { target: 200, duration: '30s' },
                { target: 400, duration: '30s' },
                { target: 600, duration: '30s' },
                { target: 600, duration: '30s' },  // 마지막은 유지해서 안정값을 본다
            ],
        },
    },
    thresholds: {
        // 넘으면 k6 가 빨간불을 준다. 멈추지는 않는다 — 어디서 넘는지를 봐야 하므로.
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
    },
    // 응답이 18KB 짜리 HTML 이다. 본문을 다 들고 있으면 k6 자신이 무거워져서
    // 서버가 아니라 부하 생성기를 재게 된다. 필요한 검사만 하고 버린다.
    discardResponseBodies: false,
};

export default function () {
    const res = http.get(`${BASE}/community?categoryId=${CATEGORY}&page=1`, {
        tags: { name: 'community_list' },
    });

    // 상태 코드만 보면 안 된다. 로그인 화면으로 넘어가거나 빈 목록이 와도 200 이다.
    // 실제로 글이 그려졌는지를 본문으로 확인한다.
    check(res, {
        'HTTP 200': (r) => r.status === 200,
        '글이 그려졌다': (r) => r.body && r.body.includes('측정 게시글'),
    });
}
