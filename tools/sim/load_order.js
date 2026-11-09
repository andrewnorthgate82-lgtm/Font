// بررسی ترتیب بارگذاری: Apps Script فایل‌ها را با ترتیب نامعلوم بار می‌کند؛ متغیرهای سطح بالا نباید به فایل دیگری وابسته باشند.
const fs = require('fs'), vm = require('vm'), path = require('path');
const dir = path.join(__dirname, '..', '..', 'nasra-gas');
const files = fs.readdirSync(dir).filter(f => f.endsWith('.gs'));
let bad = 0;
for (let k = 0; k < 300; k++) {
  const order = files.slice().sort(() => Math.random() - 0.5), ctx = vm.createContext({});
  try { order.forEach(f => vm.runInContext(fs.readFileSync(path.join(dir, f), 'utf8'), ctx, { filename: f })); }
  catch (e) { bad++; if (bad < 3) console.log(order.join(','), e.message); }
}
console.log('load_order: bad orders ' + bad + '/300');
