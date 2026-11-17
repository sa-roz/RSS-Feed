const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv.cleanup();
});

test("Unauthenticated user cannot read or write feeds", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  const feedRef = unauthedDb.doc(`users/${ALICE_UID}/feeds/feed_1`);

  await assertFails(feedRef.get());
  await assertFails(
    feedRef.set({
      id: "feed_1",
      name: "Tech News",
      url: "https://example.com/rss",
      category: "Tech",
      userId: ALICE_UID,
      updatedAt: new Date(),
    })
  );
});

test("Alice can create and read her own feed with valid data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const feedRef = aliceDb.doc(`users/${ALICE_UID}/feeds/feed_1`);

  await assertSucceeds(
    feedRef.set({
      id: "feed_1",
      name: "Tech News",
      url: "https://example.com/rss",
      category: "Tech",
      userId: ALICE_UID,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(feedRef.get());
});

test("Alice cannot create feed with mismatched userId or invalid data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const feedRef = aliceDb.doc(`users/${ALICE_UID}/feeds/feed_2`);

  // Mismatched userId
  await assertFails(
    feedRef.set({
      id: "feed_2",
      name: "Tech News",
      url: "https://example.com/rss",
      category: "Tech",
      userId: BOB_UID,
      updatedAt: new Date(),
    })
  );

  // Empty name
  await assertFails(
    feedRef.set({
      id: "feed_2",
      name: "",
      url: "https://example.com/rss",
      category: "Tech",
      userId: ALICE_UID,
      updatedAt: new Date(),
    })
  );
});

test("Bob cannot read or write Alice's feeds", async () => {
  // Alice creates her feed first
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await aliceDb.doc(`users/${ALICE_UID}/feeds/feed_1`).set({
    id: "feed_1",
    name: "Tech News",
    url: "https://example.com/rss",
    category: "Tech",
    userId: ALICE_UID,
    updatedAt: new Date(),
  });

  const bobDb = testEnv.authenticatedContext(BOOB = BOB_UID).firestore();
  const targetRef = bobDb.doc(`users/${ALICE_UID}/feeds/feed_1`);

  await assertFails(targetRef.get());
  await assertFails(
    targetRef.update({
      name: "Hacked",
    })
  );
  await assertFails(targetRef.delete());
});

test("Alice can update and delete her own feed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const feedRef = aliceDb.doc(`users/${ALICE_UID}/feeds/feed_1`);

  await feedRef.set({
    id: "feed_1",
    name: "Tech News",
    url: "https://example.com/rss",
    category: "Tech",
    userId: ALICE_UID,
    updatedAt: new Date(),
  });

  await assertSucceeds(
    feedRef.update({
      name: "Updated Tech News",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(feedRef.delete());
});
