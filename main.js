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
if (!outputChannel || !inputChannel ||!server) {
  console.error("Missing one or more environment variables: DISCORD_SERVER, INPUT_CHANNEL, OUTPUT_CHANNEL");
  throw new Error("Missing environment variables");
}

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

function getStartOfWeekAfter(time) {
  const thisWeek = new Date(time);
  thisWeek.setDate(thisWeek.getDate() + 7);
  thisWeek.setHours(0, 0, 0, 0);
  return thisWeek;
}

const superStreak = "❤️‍🔥";
const streak = "🔥";

function isInTimeframe(time, timeframe) {
  return time.getTime() >= timeframe.startTime.getTime() && time.getTime() < timeframe.endTime.getTime();
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

async function threadActiveInTime(id, timeframe) {
  const snowflake = dateToSnowflake(timeframe.startTime);
  const response = await discordFetch(`channels/${id}/messages?after=${snowflake}`);
  const json = await response.json();
  let messageCount = 0;
  let users = new Set();
  for (const message of json) {
    if (isInTimeframe(new Date(message.timestamp), timeframe)) {
      messageCount++;
      users.add(message.author.id);
    }
  }
  return { messageCount, users: users.size };
}

async function getActiveThreads(timeframe) {
  const threads = await activeThreads();
  console.log(threads);
  let arr = [];
  for (const thread of threads) {
    const { messageCount, users } = await threadActiveInTime(thread.id, timeframe);
    if (messageCount > 0) {
      thread.messageCount = messageCount;
      thread.users = users;
      arr.push(thread);
      console.log(`Active: ${thread.id} ${thread.name} (${messageCount} messages by ${users} users)`);
    }
  }
  return arr;
}

async function getLastStats(timeframe) {
  const snowflake = dateToSnowflake(timeframe.startTime);
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
  return s.length === 0 ? s : s + " ";
}

function formatMessage(newStats) {
  const stats = newStats.map(s => `https://discord.com/channels/906297567011291177/${s.id} ${formatEmoji(s.count)}(${s.messageCount} messages by ${s.users} users)`).join("\n");
  return `<@&906328017255673946>\nLast weeks active posts in the game design forums:\n${stats}\n\n🔥 = Hot streak!\n❤️‍🔥 = Super hot streak! (= 5 🔥 )`;
}

function formatDisappearingMessage(disappearedMessage) {
  if (disappearedMessage.length === 0) return "";
  return "\n\nPrevious streaks lost:\n" + disappearedMessage;
}

async function makeUpdateMessage(timeframe) {
  let stats = await getLastStats(timeframe);
  // console.log(JSON.stringify(stats));

  const active = await getActiveThreads(timeframe);
  console.log(active.map(e => e.id));

  const disappearedStats = stats.filter(stat => !active.some(a => a.id == stat.id) && stat.count > 0);
  const disappearedMessage = disappearedStats.map(s => `https://discord.com/channels/906297567011291177/${s.id} lost its streak of ${formatEmoji(s.count)}`).join("\n");
  stats = stats.filter(thread => active.some(a => a.id == thread.id));
  stats.forEach(stat => { stat.count++; });
  active.forEach(a => {
    const existingStat = stats.find(stat => stat.id == a.id);

    if (!existingStat) {
      console.log("Newcomer! " + a.name);
      stats.push({ id: a.id, count: 0, messageCount: a.messageCount, users: a.users });
    } else {
      existingStat.messageCount = a.messageCount;
      existingStat.users = a.users;
    }
  });

  return formatMessage(stats) + formatDisappearingMessage(disappearedMessage);
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

if (typeof exports !== "undefined") {
  const startTime = getStartOfWeekBefore(getStartOfWeek());
  const endTime = getStartOfWeek();

  exports.handler = async (event) => {
    const updateMessage = await makeUpdateMessage({ startTime, endTime });
    console.log(updateMessage);
    await postMessage(updateMessage);

    return {
      statusCode: 200,
      body: JSON.stringify({ message: "Lambda done" }),
    };
  };
} else {
  const startTime = getStartOfWeek();
  const endTime = getStartOfWeekAfter(startTime);

  const updateMessage = await makeUpdateMessage({ startTime, endTime });
  console.log(updateMessage);
}

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
