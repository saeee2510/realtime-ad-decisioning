// k6 load test for POST /v1/decide
// Run:  k6 run load/decide.js
// Options: k6 run -e RATE=1000 -e DURATION=60s -e BASE_URL=http://localhost:8080 load/decide.js
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = parseInt(__ENV.RATE || '500', 10);
const DURATION = __ENV.DURATION || '60s';

const SEGMENTS = ['sports', 'gaming', 'news', 'music', 'finance', 'travel'];
const PLACEMENTS = ['app_store_search', 'app_store_today', 'news_feed'];

export const options = {
  scenarios: {
    decide: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 100,
      maxVUs: 500,
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<20'],
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

export default function () {
  const body = JSON.stringify({
    userSegment: pick(SEGMENTS),
    placement: pick(PLACEMENTS),
    context: { device: 'iphone' },
  });
  const res = http.post(`${BASE_URL}/v1/decide`, body, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'status is 200 or 204': (r) => r.status === 200 || r.status === 204 });
}
