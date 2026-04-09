/**
 * 1. Find active threads
 * 2. Go through active threads and see if any posts were made during specified period
 * 3. Check most recent update in output channel
 * 4. Apply logic to most recent update post, based on active threads
 * 5. Send update
 */

const botId = process.env.DISCORD_BOT_ID
const botToken = process.env.DISCORD_BOT_TOKEN

const outputChannel = process.env.OUTPUT_CHANNEL
const inputChannel = process.env.INPUT_CHANNEL
const server = process.env.DISCORD_SERVER

function getStartOfWeek() {
  const now = new Date();
  // Convert to Stockholm time as a string, then back to Date
  const stockholmNow = new Date(
    now.toLocaleString('en-US', { timeZone: 'Europe/Stockholm' })
  );

  const day = stockholmNow.getDay() || 7; // Sunday = 7
  stockholmNow.setDate(stockholmNow.getDate() - (day - 1));
  stockholmNow.setHours(0, 0, 0, 0);
  return stockholmNow;
}

function getStartOfWeekBefore(time) {
  const thisWeek = new Date(time);
  thisWeek.setDate(thisWeek.getDate() - 7);
  thisWeek.setHours(0, 0, 0, 0);
  return thisWeek;
}

const startTime = getStartOfWeekBefore(getStartOfWeek());
const endTime = getStartOfWeek();
const snowflake = dateToSnowflake(startTime);

const superStreak = "❤️‍🔥";
const streak = "🔥";

function isInTimeframe(time) {
  return time.getTime() >= startTime.getTime() && time.getTime() < endTime.getTime();
}

function discordFetch(url) {
  return fetch(`https://discord.com/api/${url}`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      "Authorization": "Bot " + botToken,
      "User-Agent": "DiscordBot (https://www.zomis.net, 1)"
    },
    body: JSON.stringify(),
  });
}

async function activeThreads() {
  const response = await discordFetch(`channels/${inputChannel}/threads/active`);
  const json = await response.json();
  return json.threads;
}

function dateToSnowflake(date) {
  const discordEpoch = 1420070400000;
  return BigInt(date.getTime() - discordEpoch) << 22n;
}

async function threadActiveInTime(id) {
  const response = await discordFetch(`channels/${id}/messages?after=${snowflake}`);
  const json = await response.json();
  for (const message of json) {
    if (isInTimeframe(new Date(message.timestamp))) {
      return true;
    }
  }
  return false;
}

async function getActiveThreads() {
  const threads = await activeThreads();
  console.log(threads);
  let arr = [];
  for (const thread of threads) {
    const active = await threadActiveInTime(thread.id);
    if (active) {
      arr.push(thread);
      console.log("Active: " + thread.id + ": " + thread.name);
    }
  }
  return arr;
}

async function getLastStats() {
//  const r = await discordFetch('channels/1460761233358721216/messages/1489234218931458218');
  const messages = await discordFetch(`channels/${outputChannel}/messages?after=${snowflake}`);

  const json = await messages.json();
  const r = json.find(m => m.content.indexOf("active posts in the game design forums") > 0);
  console.log("Matching last stats message:");
  console.log(r);

  const lines = r.content.split('\n');
  let arr = [];
  for (const line of lines) {
    const prefix = `https://discord.com/channels/${server}/`;
    if (line.indexOf(prefix) === 0) {
      const spaceIndex = line.indexOf(' ');
      const hasSpace = spaceIndex >= 0;
      const threadId = hasSpace ? line.substring(prefix.length, spaceIndex) : line.substring(prefix.length, line.length);
      const emojis = hasSpace ? line.substring(spaceIndex) : '';
      const superCount = (emojis.match(/❤️‍🔥/g) || []).length;
      const count = (emojis.match(/🔥/g) || []).length - superCount;
      console.log(threadId, emojis, superCount, count);
      arr.push({
        id: threadId,
        count: superCount * 5 + count
      });
    }
  }
  return arr;
}

function formatEmoji(count) {
  const superStreaks = Math.floor(count / 5);
  const streaks = count % 5;
  let s = "";
  for (let i = 1; i <= superStreaks; i++) {
    s += superStreak;
  }
  for (let i = 1; i <= streaks; i++) {
    s += streak;
  }
  return s;
}

function formatMessage(newStats) {
  const stats = newStats.map(s => `https://discord.com/channels/906297567011291177/${s.id} ${formatEmoji(s.count)}`).join("\n");
  return `<@&906328017255673946>\nLast weeks active posts in the game design forums:\n${stats}\n\n🔥 = Hot streak!\n❤️‍🔥 = Super hot streak! (= 5 🔥 )`;
}

async function makeUpdateMessage() {
  let stats = await getLastStats();
  // console.log(JSON.stringify(stats));

  const active = await getActiveThreads();
  console.log(active.map(e => e.id));

  stats = stats.filter(thread => active.some(a => a.id == thread.id));
  stats.forEach(thread => { thread.count++; });
  active.forEach(a => {
    if (!stats.some(thread => thread.id == a.id)) {
      stats.push({ id: a.id, count: 0 });
    }
  });

  console.log(formatMessage(stats));
  return formatMessage(stats);
}

async function postMessage(text) {
  return fetch(`https://discord.com/api/channels/${outputChannel}/messages`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": "Bot " + botToken,
      "User-Agent": "DiscordBot (https://www.zomis.net, 1)"
    },
    body: JSON.stringify(
      {
        content: text,
      }
    ),
  });
}

//await postUpdate();
//await postMessage("Test");

exports.handler = async (event) => {
  const updateMessage = await makeUpdateMessage();
  console.log(updateMessage);
  await postMessage(updateMessage);

  return {
    statusCode: 200,
    body: JSON.stringify({ message: "Lambda done" }),
  };
};

/*
  content: '<@&906328017255673946> \n' +
    'Last weeks active posts in the game design forums:\n' +
    'https://discord.com/channels/906297567011291177/1405079043241672714 ❤️‍🔥❤️‍🔥 \n' +
    'https://discord.com/channels/906297567011291177/1405128544224542820 ❤️‍🔥🔥🔥 \n' +
    'https://discord.com/channels/906297567011291177/1403313363395805236 ❤️‍🔥\n' +
    'https://discord.com/channels/906297567011291177/1403806200113528902 🔥🔥 \n' +
    'https://discord.com/channels/906297567011291177/1410990742498967562 🔥🔥 \n' +
    'https://discord.com/channels/906297567011291177/1403337733916852226 🔥 \n' +
    'https://discord.com/channels/906297567011291177/1486281354726604901\n' +
    'https://discord.com/channels/906297567011291177/1403358926317031565\n' +
    'https://discord.com/channels/906297567011291177/1405493764042260530\n' +
    'https://discord.com/channels/906297567011291177/1415263395615211550\n' +
    'https://discord.com/channels/906297567011291177/1404065292065312859\n' +
    'https://discord.com/channels/906297567011291177/1465478895724925079\n' +
    '\n' +
    '🔥  = Hot streak! \n' +
    '❤️‍🔥 = Super hot streak! (= 5 🔥 )'
*/
