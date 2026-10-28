const { makeEnv } = require('./stubs');
const env = makeEnv();
env.ctx.setup(); env.ctx.installPhase4(); env.logs.length = 0;
env.ctx.runTests();
const fails = env.logs.filter(l => /❌|شکست|passed/.test(l));
console.log(fails.join('\n'));
