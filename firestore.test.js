const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_chicken_1";
const BOB_UID = "bob_chicken_2";
const EVE_UID = "eve_outsider_3";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(ms = 2000) {
  return new Date(Date.now() - ms);
}

test("Unauthenticated users cannot read or write profiles, rooms, players, or props", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = pastDate();

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("rooms").where("status", "==", "open").get()
  );
  await assertFails(
    unauthDb.collection("rooms").doc("room_1").set({
      roomCode: "CHKN99",
      name: "Arena",
      hostId: ALICE_UID,
      status: "open",
      maxPlayers: 6,
      memberUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("UserProfile: owner create/update works; cross-user access and shadow fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "RoosterOne",
      totalKills: 0,
      totalMatches: 0,
      bestStreak: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalKills: 12,
      totalMatches: 1,
      bestStreak: 5,
      updatedAt: pastDate(1000),
    })
  );

  // Bob cannot read or mutate Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      nickname: "Hacked",
      updatedAt: pastDate(500),
    })
  );

  // Shadow field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Immutable createdAt mutation rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalKills: 20,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("GameRoom: host creates, Bob joins & leaves, filtered queries succeed, closed room locks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const created = pastDate(8000);

  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_alpha").set({
      roomCode: "CHKN42",
      name: "Chicken Arena",
      hostId: ALICE_UID,
      status: "open",
      maxPlayers: 6,
      memberUids: [ALICE_UID],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Filtered Kotlin queries succeed
  await assertSucceeds(
    bobDb.collection("rooms").where("status", "==", "open").limit(20).get()
  );
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .where("status", "==", "open")
      .where("roomCode", "==", "CHKN42")
      .limit(1)
      .get()
  );

  // Unfiltered list rejected
  await assertFails(bobDb.collection("rooms").get());

  // Bob joins open room
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_alpha").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(3000),
    })
  );

  // Bob cannot close Alice's room
  await assertFails(
    bobDb.collection("rooms").doc("room_alpha").update({
      status: "closed",
      updatedAt: pastDate(2000),
    })
  );

  // Bob leaves room cleanly
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_alpha").update({
      memberUids: [ALICE_UID],
      updatedAt: pastDate(1500),
    })
  );

  // Alice closes room (terminal state)
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_alpha").update({
      status: "closed",
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state lock: Eve cannot join closed room & Alice cannot reopen
  await assertFails(
    eveDb.collection("rooms").doc("room_alpha").update({
      memberUids: [ALICE_UID, EVE_UID],
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("rooms").doc("room_alpha").update({
      status: "open",
      updatedAt: pastDate(500),
    })
  );
});

test("RoomPlayer & RoomProp: members sync 3D state and props; outsiders denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const ts = pastDate(6000);

  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_live").set({
      roomCode: "GUN777",
      name: "Sandbox Battle",
      hostId: ALICE_UID,
      status: "open",
      maxPlayers: 6,
      memberUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("rooms").doc("room_live").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(5000),
    })
  );

  const members = [ALICE_UID, BOB_UID];

  // Alice creates her RoomPlayer doc
  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc("room_live")
      .collection("players")
      .doc(ALICE_UID)
      .set({
        userId: ALICE_UID,
        displayName: "Alice",
        x: 0.0,
        y: 1.0,
        z: 5.0,
        yaw: 90.0,
        pitch: 0.0,
        hp: 100,
        kills: 0,
        weapon: "RIFLE",
        drivingVehicle: false,
        shooting: false,
        memberUids: members,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Bob can list players using whereArrayContains("memberUids", BOB_UID)
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc("room_live")
      .collection("players")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Bob can deal damage to Alice
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc("room_live")
      .collection("players")
      .doc(ALICE_UID)
      .update({
        hp: 75,
        updatedAt: pastDate(1000),
      })
  );

  // Bob cannot spoof Alice's weapon or kills
  await assertFails(
    bobDb
      .collection("rooms")
      .doc("room_live")
      .collection("players")
      .doc(ALICE_UID)
      .update({
        kills: 999,
        updatedAt: pastDate(900),
      })
  );

  // Eve (not a member) cannot read Alice's doc, cannot run unfiltered list, and cannot join players
  await assertFails(
    eveDb.collection("rooms").doc("room_live").collection("players").get()
  );
  await assertFails(
    eveDb.collection("rooms").doc("room_live").collection("players").doc(ALICE_UID).get()
  );
  await assertFails(
    eveDb.collection("rooms").doc("room_live").collection("players").doc(EVE_UID).set({
      userId: EVE_UID,
      displayName: "Eve",
      x: 0.0,
      y: 0.0,
      z: 0.0,
      yaw: 0.0,
      pitch: 0.0,
      hp: 100,
      kills: 0,
      weapon: "PISTOL",
      drivingVehicle: false,
      shooting: false,
      memberUids: [EVE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  const eveEmpty = await assertSucceeds(
    eveDb
      .collection("rooms")
      .doc("room_live")
      .collection("players")
      .where("memberUids", "array-contains", EVE_UID)
      .get()
  );
  if (eveEmpty.size !== 0) {
    throw new Error("Expected Eve filtered query to return 0 documents");
  }

  // Alice spawns a red explosive barrel prop
  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc("room_live")
      .collection("props")
      .doc("prop_barrel_1")
      .set({
        ownerId: ALICE_UID,
        propType: "BARREL",
        x: 10.5,
        y: 0.0,
        z: -4.2,
        yaw: 45.0,
        hp: 40,
        destroyed: false,
        memberUids: members,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Bob lists props & blows up the barrel
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc("room_live")
      .collection("props")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc("room_live")
      .collection("props")
      .doc("prop_barrel_1")
      .update({
        hp: 0,
        destroyed: true,
        updatedAt: pastDate(500),
      })
  );

  // Eve cannot read or modify room props
  await assertFails(
    eveDb
      .collection("rooms")
      .doc("room_live")
      .collection("props")
      .doc("prop_barrel_1")
      .get()
  );
});

test("RoomMessage: members send/list chat and sync memberUids; outsiders, spoofing, edits & bad text denied", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const ts = pastDate(5000);

  // Alice creates open room and Bob joins
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_chat").set({
      roomCode: "CHAT99",
      name: "Chat Arena",
      hostId: ALICE_UID,
      status: "open",
      maxPlayers: 6,
      memberUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_chat").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(4000),
    })
  );

  const members = [ALICE_UID, BOB_UID];

  // Alice sends a valid Kotlin-mirrored chat message (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc("room_chat")
      .collection("messages")
      .doc("msg_1")
      .set({
        senderId: ALICE_UID,
        senderName: "Alice",
        text: "Го на рампу!",
        memberUids: members,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Bob lists chat messages with Kotlin-mirrored whereArrayContains query
  const bobSnap = await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc("room_chat")
      .collection("messages")
      .where("memberUids", "array-contains", BOB_UID)
      .limit(60)
      .get()
  );
  if (bobSnap.size !== 1) {
    throw new Error(`Expected Bob to read 1 chat message, got ${bobSnap.size}`);
  }

  // Bob replies
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc("room_chat")
      .collection("messages")
      .doc("msg_2")
      .set({
        senderId: BOB_UID,
        senderName: "Bob",
        text: "Уже еду на танке!",
        memberUids: members,
        createdAt: pastDate(2000),
        updatedAt: pastDate(2000),
      })
  );

  // Host Alice can sync memberUids on existing chat message
  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc("room_chat")
      .collection("messages")
      .doc("msg_1")
      .update({
        memberUids: members,
        updatedAt: pastDate(1000),
      })
  );

  // Negative: editing message text is forbidden (chat history is immutable)
  await assertFails(
    aliceDb
      .collection("rooms")
      .doc("room_chat")
      .collection("messages")
      .doc("msg_1")
      .update({
        text: "Edited text",
        updatedAt: pastDate(800),
      })
  );

  // Negative: spoofing senderId is rejected
  await assertFails(
    bobDb
      .collection("rooms")
      .doc("room_chat")
      .collection("messages")
      .doc("msg_spoof")
      .set({
        senderId: ALICE_UID,
        senderName: "Alice",
        text: "Spoofed!",
        memberUids: members,
        createdAt: pastDate(1000),
        updatedAt: pastDate(1000),
      })
  );

  // Negative: empty or oversized (>200 chars) text rejected
  await assertFails(
    aliceDb
      .collection("rooms")
      .doc("room_chat")
      .collection("messages")
      .doc("msg_empty")
      .set({
        senderId: ALICE_UID,
        senderName: "Alice",
        text: "",
        memberUids: members,
        createdAt: pastDate(1000),
        updatedAt: pastDate(1000),
      })
  );
  await assertFails(
    aliceDb
      .collection("rooms")
      .doc("room_chat")
      .collection("messages")
      .doc("msg_long")
      .set({
        senderId: ALICE_UID,
        senderName: "Alice",
        text: "A".repeat(201),
        memberUids: members,
        createdAt: pastDate(1000),
        updatedAt: pastDate(1000),
      })
  );

  // Negative: shadow field rejected
  await assertFails(
    aliceDb
      .collection("rooms")
      .doc("room_chat")
      .collection("messages")
      .doc("msg_shadow")
      .set({
        senderId: ALICE_UID,
        senderName: "Alice",
        text: "Shadow",
        isPinned: true,
        memberUids: members,
        createdAt: pastDate(1000),
        updatedAt: pastDate(1000),
      })
  );

  // Negative: unauthenticated & outsider Eve cannot read, list unfiltered, or send
  await assertFails(
    unauthDb.collection("rooms").doc("room_chat").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    eveDb.collection("rooms").doc("room_chat").collection("messages").get()
  );
  await assertFails(
    eveDb.collection("rooms").doc("room_chat").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    eveDb
      .collection("rooms")
      .doc("room_chat")
      .collection("messages")
      .doc("msg_eve")
      .set({
        senderId: EVE_UID,
        senderName: "Eve",
        text: "Intruder!",
        memberUids: [EVE_UID],
        createdAt: pastDate(1000),
        updatedAt: pastDate(1000),
      })
  );

  // Negative: Bob cannot delete Alice's message; Alice (host) can delete Bob's message
  await assertFails(
    bobDb.collection("rooms").doc("room_chat").collection("messages").doc("msg_1").delete()
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_chat").collection("messages").doc("msg_2").delete()
  );
});

test("Global Leaderboard & UserProfile totalWins: owner writes, filtered list succeeds, cross-user writes & unfiltered list fail", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  // UserProfile with totalWins create & partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "AlphaRooster",
      totalKills: 0,
      totalWins: 0,
      totalMatches: 0,
      bestStreak: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalKills: 14,
      totalWins: 2,
      totalMatches: 3,
      bestStreak: 7,
      updatedAt: pastDate(2000),
    })
  );

  // Owner get on non-existent leaderboard doc succeeds (mirrors ensureProfile check)
  const missingSelf = await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).get()
  );
  if (missingSelf.exists) {
    throw new Error("Expected leaderboard doc to not exist yet");
  }

  // Owner creates leaderboard entry (exact Kotlin payload, no `id` key)
  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "AlphaRooster",
      kills: 14,
      wins: 2,
      matchesPlayed: 3,
      bestStreak: 7,
      visibility: "public",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob creates his own leaderboard entry
  await assertSucceeds(
    bobDb.collection("leaderboard").doc(BOB_UID).set({
      userId: BOB_UID,
      nickname: "TurboHen",
      kills: 25,
      wins: 4,
      matchesPlayed: 5,
      bestStreak: 9,
      visibility: "public",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Kotlin-mirrored filtered leaderboard query succeeds and returns both players
  const lbSnap = await assertSucceeds(
    bobDb
      .collection("leaderboard")
      .where("visibility", "==", "public")
      .limit(50)
      .get()
  );
  if (lbSnap.size !== 2) {
    throw new Error(`Expected 2 leaderboard entries, got ${lbSnap.size}`);
  }

  // Authenticated user can get another player's public leaderboard entry
  await assertSucceeds(bobDb.collection("leaderboard").doc(ALICE_UID).get());

  // Owner updates own stats
  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      nickname: "AlphaRooster",
      kills: 30,
      wins: 5,
      matchesPlayed: 6,
      bestStreak: 11,
      visibility: "public",
      updatedAt: pastDate(1000),
    })
  );

  // Negative: unfiltered list rejected (enforces Query Trust Test)
  await assertFails(bobDb.collection("leaderboard").get());

  // Negative: unauthenticated users cannot read or list leaderboard
  await assertFails(
    unauthDb.collection("leaderboard").where("visibility", "==", "public").limit(50).get()
  );
  await assertFails(unauthDb.collection("leaderboard").doc(ALICE_UID).get());

  // Negative: cross-user write/update/delete denied
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({
      kills: 0,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).delete()
  );
  await assertFails(
    bobDb.collection("leaderboard").doc("other_uid").set({
      userId: "other_uid",
      nickname: "Spoof",
      kills: 999,
      wins: 999,
      matchesPlayed: 999,
      bestStreak: 99,
      visibility: "public",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Negative: immutable fields (userId, createdAt), shadow fields, invalid visibility, or negative stats rejected
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      createdAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      kills: -5,
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      visibility: "private",
      updatedAt: pastDate(200),
    })
  );
});

test("DailyMissions & UserProfile coins: owner creates 3 tasks, progresses, completes, claims coins; terminal lock & cross-user access enforced", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);
  const dayKey = "2026-11-19";

  // Owner profile with coins balance
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "MissionChicken",
      totalKills: 0,
      totalWins: 0,
      coins: 0,
      totalMatches: 0,
      bestStreak: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create 3 daily missions mirroring Kotlin ensureDailyMissions payload (no `id` key)
  const missions = [
    { slot: 0, type: "KILLS", title: "Устрани 5 врагов", target: 5, reward: 150 },
    { slot: 1, type: "DRIVE_METERS", title: "Проедь 500 м на машине", target: 500, reward: 120 },
    { slot: 2, type: "BUILD_PROPS", title: "Построй 6 объектов", target: 6, reward: 100 },
  ];

  for (const m of missions) {
    await assertSucceeds(
      aliceDb
        .collection("users")
        .doc(ALICE_UID)
        .collection("daily_missions")
        .doc(`${dayKey}_slot_${m.slot}`)
        .set({
          userId: ALICE_UID,
          dayKey,
          slotIndex: m.slot,
          missionType: m.type,
          title: m.title,
          target: m.target,
          progress: 0,
          rewardCoins: m.reward,
          status: "active",
          createdAt: created,
          updatedAt: created,
        })
    );
  }

  // Kotlin-mirrored scoped list query succeeds and returns all 3 daily missions
  const listSnap = await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("daily_missions")
      .where("userId", "==", ALICE_UID)
      .where("dayKey", "==", dayKey)
      .limit(3)
      .get()
  );
  if (listSnap.size !== 3) {
    throw new Error(`Expected 3 daily missions, got ${listSnap.size}`);
  }

  const killsDoc = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("daily_missions")
    .doc(`${dayKey}_slot_0`);

  // Progress update (active -> active)
  await assertSucceeds(
    killsDoc.update({
      progress: 3,
      status: "active",
      updatedAt: pastDate(3000),
    })
  );

  // Negative: cannot mark completed or claimed before reaching target
  await assertFails(
    killsDoc.update({
      progress: 3,
      status: "completed",
      updatedAt: pastDate(2500),
    })
  );
  await assertFails(
    killsDoc.update({
      progress: 3,
      status: "claimed",
      updatedAt: pastDate(2500),
    })
  );

  // Negative: progress cannot decrease or exceed target
  await assertFails(
    killsDoc.update({
      progress: 1,
      status: "active",
      updatedAt: pastDate(2500),
    })
  );
  await assertFails(
    killsDoc.update({
      progress: 10,
      status: "completed",
      updatedAt: pastDate(2500),
    })
  );

  // Reach target -> completed
  await assertSucceeds(
    killsDoc.update({
      progress: 5,
      status: "completed",
      updatedAt: pastDate(2000),
    })
  );

  // Negative: cannot inflate rewardCoins or tamper immutable fields
  await assertFails(
    killsDoc.update({
      rewardCoins: 999,
      updatedAt: pastDate(1800),
    })
  );

  // Claim reward: mission transitions completed -> claimed and user coins increment
  await assertSucceeds(
    killsDoc.update({
      status: "claimed",
      updatedAt: pastDate(1500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      coins: 150,
      updatedAt: pastDate(1500),
    })
  );

  // Negative: terminal state lock — claimed mission cannot be mutated or reverted!
  await assertFails(
    killsDoc.update({
      status: "completed",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    killsDoc.update({
      status: "active",
      updatedAt: pastDate(1000),
    })
  );

  // Negative: unauthenticated & cross-user Bob cannot read, list, create, or claim Alice's missions
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("daily_missions")
      .doc(`${dayKey}_slot_0`)
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("daily_missions")
      .where("userId", "==", ALICE_UID)
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("daily_missions")
      .doc(`${dayKey}_slot_1`)
      .update({
        progress: 500,
        status: "completed",
        updatedAt: pastDate(500),
      })
  );
});

test("Extended GameRoom settings: host live-edits rules, locks entry, kicks players, and deletes room; non-host & locked joins fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const created = pastDate(8000);

  // Host Alice creates room with all extended room configuration fields
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_ext").set({
      roomCode: "TURB99",
      name: "Turbo Lobby",
      hostId: ALICE_UID,
      status: "open",
      maxPlayers: 6,
      mapId: "TURBO_SPEEDWAY",
      gameMode: "RACING",
      privateRoom: true,
      password: "nitro",
      pinned: true,
      joinLocked: false,
      allowGuestPropSpawn: false,
      allowGuestVehicleSpawn: true,
      friendlyFire: false,
      infiniteAmmo: true,
      godMode: false,
      memberUids: [ALICE_UID],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob joins open unlocked room and creates his player doc
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_ext").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(5000),
    })
  );
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc("room_ext")
      .collection("players")
      .doc(BOB_UID)
      .set({
        userId: BOB_UID,
        displayName: "Bob",
        x: 0.0,
        y: 0.0,
        z: 0.0,
        yaw: 0.0,
        pitch: 0.0,
        hp: 100,
        kills: 0,
        weapon: "RIFLE",
        drivingVehicle: false,
        shooting: false,
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: pastDate(4500),
        updatedAt: pastDate(4500),
      })
  );

  // Negative: Non-host Bob cannot change mapId, rules, pin, or delete room
  await assertFails(
    bobDb.collection("rooms").doc("room_ext").update({
      mapId: "TACTICAL_COMPOUND",
      updatedAt: pastDate(4000),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_ext").update({
      godMode: true,
      updatedAt: pastDate(4000),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_ext").delete()
  );

  // Host Alice live-updates map, mode, rules, and locks entry
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_ext").update({
      name: "Tactical Fortress",
      mapId: "TACTICAL_COMPOUND",
      gameMode: "PVP",
      joinLocked: true,
      godMode: true,
      allowGuestPropSpawn: true,
      updatedAt: pastDate(3000),
    })
  );

  // Negative: Eve cannot join while joinLocked == true
  await assertFails(
    eveDb.collection("rooms").doc("room_ext").update({
      memberUids: [ALICE_UID, BOB_UID, EVE_UID],
      updatedAt: pastDate(2500),
    })
  );

  // Host Alice kicks Bob (removes Bob from memberUids & deletes Bob's player doc)
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_ext").update({
      memberUids: [ALICE_UID],
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc("room_ext")
      .collection("players")
      .doc(BOB_UID)
      .delete()
  );

  // Host Alice deletes room cleanly
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_ext").delete()
  );
});

test("UserProfile skins & customization: owner saves selectedSkin, weaponSkin, and nickname; invalid skins & cross-user edits fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "CyberRooster",
      totalKills: 0,
      totalWins: 0,
      coins: 0,
      totalMatches: 0,
      bestStreak: 0,
      selectedSkin: "CYBER_NEON",
      weaponSkin: "TACTICAL",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner updates skins & nickname
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      nickname: "GoldenAce",
      selectedSkin: "GOLDEN_KING",
      weaponSkin: "CLASSIC",
      updatedAt: pastDate(1000),
    })
  );

  // Invalid character skin rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      selectedSkin: "HACKED_SKIN",
      updatedAt: pastDate(500),
    })
  );

  // Invalid weapon skin rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      weaponSkin: "DIAMOND_CHEAT",
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot modify Alice's skins
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      selectedSkin: "SHADOW_NINJA",
      updatedAt: pastDate(500),
    })
  );
});

test("RoomPlayer per-player host privileges: host toggles godMode & canBuild; self-grant, non-host toggle & damage vs godMode player fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  // Alice creates room, Bob joins, both create player docs
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_priv").set({
      roomCode: "PRIV01",
      name: "Privileges Arena",
      hostId: ALICE_UID,
      status: "open",
      maxPlayers: 6,
      memberUids: [ALICE_UID],
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_priv").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(4500),
    })
  );

  const members = [ALICE_UID, BOB_UID];
  const basePlayer = (uid, name) => ({
    userId: uid,
    displayName: name,
    x: 0,
    y: 2,
    z: 10,
    yaw: 0,
    pitch: 0,
    hp: 100,
    kills: 0,
    weapon: "RIFLE",
    drivingVehicle: false,
    shooting: false,
    memberUids: members,
    createdAt: created,
    updatedAt: created,
  });

  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_priv").collection("players").doc(ALICE_UID).set(basePlayer(ALICE_UID, "HostAlice"))
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_priv").collection("players").doc(BOB_UID).set(basePlayer(BOB_UID, "GuestBob"))
  );

  // Negative: Bob CANNOT self-grant godMode or canBuild
  await assertFails(
    bobDb.collection("rooms").doc("room_priv").collection("players").doc(BOB_UID).update({
      godMode: true,
      updatedAt: pastDate(3500),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_priv").collection("players").doc(BOB_UID).update({
      canBuild: true,
      updatedAt: pastDate(3500),
    })
  );

  // Positive: Host Alice grants Bob godMode and revokes Bob's canBuild
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_priv").collection("players").doc(BOB_UID).update({
      godMode: true,
      canBuild: false,
      updatedAt: pastDate(3000),
    })
  );

  // Negative: Opponent combat damage is blocked while Bob has godMode == true
  await assertFails(
    aliceDb.collection("rooms").doc("room_priv").collection("players").doc(BOB_UID).update({
      hp: 40,
      updatedAt: pastDate(2500),
    })
  );

  // Positive: Bob can still update his own normal movement state without touching privileges
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_priv").collection("players").doc(BOB_UID).update({
      x: 12.5,
      z: -4.0,
      updatedAt: pastDate(2000),
    })
  );

  // Negative: Bob cannot toggle Alice's privileges
  await assertFails(
    bobDb.collection("rooms").doc("room_priv").collection("players").doc(ALICE_UID).update({
      godMode: false,
      canBuild: false,
      updatedAt: pastDate(1500),
    })
  );

  // Positive: Host Alice disables Bob's godMode and re-enables canBuild -> damage works again
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_priv").collection("players").doc(BOB_UID).update({
      godMode: false,
      canBuild: true,
      updatedAt: pastDate(1200),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_priv").collection("players").doc(BOB_UID).update({
      hp: 70,
      updatedAt: pastDate(800),
    })
  );
});




