const test = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const path = require('node:path');
const fs = require('node:fs');
const root = path.resolve(__dirname, '../..');
const source = path.join(root, 'main/resources/static/js/korea-time.js');

for (const timezone of ['UTC', 'Asia/Seoul', 'America/Los_Angeles']) {
  test('채팅·알림은 브라우저 시간대와 관계없이 한국 시각으로 표시한다: ' + timezone, () => {
    const probe = `
      const vm = require('node:vm');
      const fs = require('node:fs');
      const context = {window: {}, Intl, Date};
      vm.runInNewContext(fs.readFileSync(process.argv[1], 'utf8'), context);
      const format = context.window.CakeShopTime.dateTime;
      process.stdout.write(JSON.stringify([
        format('2026-11-05T00:05:00'),
        format('2026-11-04T15:05:00Z'),
        format('2026-11-05T00:05:00+09:00'),
        format('2026-11-05 00:05:00.123456'),
        format('2026-11-04T07:05:00-08:00'),
        format('invalid'), format(null),
        context.window.CakeShopTime.time('2026-11-04T15:05:00Z'),
        context.window.CakeShopTime.pickup('2026-11-05T00:05:00'),
        context.window.CakeShopTime.parse('2026-11-05T00:05:00') > context.window.CakeShopTime.parse('2026-11-04T14:05:00Z')
      ]));
    `;
    const result = spawnSync(process.execPath, ['-e', probe, source],
      {encoding: 'utf8', env: {...process.env, TZ: timezone}});
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(result.stdout), [...Array(5).fill('2026-11-05 00:05'), '', '', '00:05', '11월 05일 00:05', true]);
  });
}

test('고객·관리자 알림 화면은 한국 시간 포맷터를 로드하고 사용한다', () => {
  for (const role of ['customer', 'admin']) {
    const html = fs.readFileSync(path.join(root, `main/resources/templates/${role}/notification/list.html`), 'utf8');
    assert.match(html, /<script defer th:src="@\{\/js\/korea-time\.js\}"><\/script>/);
    assert.match(html, /CakeShopTime\.dateTime\(displayDate\)/);
    assert.doesNotMatch(html, /displayDate\.replace\("T", " "\)\.substring/);
  }
});

test('채팅 화면은 공용 포맷터를 채팅 스크립트보다 먼저 로드한다', () => {
  for (const [role, file, script] of [
    ['customer', 'room', 'chat-customer'], ['admin', 'list', 'chat-admin']
  ]) {
    const html = fs.readFileSync(path.join(root, `main/resources/templates/${role}/chat/${file}.html`), 'utf8');
    assert.ok(html.indexOf('/js/korea-time.js') >= 0);
    assert.ok(html.indexOf('/js/korea-time.js') < html.indexOf(`/js/${script}.js`));
    const js = fs.readFileSync(path.join(root, `main/resources/static/js/${script}.js`), 'utf8');
    assert.match(js, /CakeShopTime\.time\(isoString\)/);
    assert.match(js, /CakeShopTime\.pickup\(rawTime\)/);
    assert.doesNotMatch(js, /\.getHours\(\)/);
  }
});
