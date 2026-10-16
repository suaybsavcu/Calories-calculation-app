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

test("Unauthenticated user: cannot read user profiles or meals", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("meals").get());
});

test("Authenticated user: can create, read, and update their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set({
    userId: ALICE_UID,
    email: "alice@example.com",
    displayName: "Alice",
    dailyCalorieGoal: 2000,
    dailyProteinGoal: 130,
    dailyCarbsGoal: 220,
    dailyFatGoal: 65,
    createdAt: new Date(),
    updatedAt: new Date()
  }));

  await assertSucceeds(profileRef.get());

  await assertSucceeds(profileRef.update({
    dailyCalorieGoal: 2200,
    updatedAt: new Date()
  }));
});

test("Authenticated user: cannot read or write another user's profile", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceProfileRef = bobDb.collection("users").doc(ALICE_UID);

  await assertFails(aliceProfileRef.get());
  await assertFails(aliceProfileRef.set({
    userId: ALICE_UID,
    dailyCalorieGoal: 1800,
    createdAt: new Date(),
    updatedAt: new Date()
  }));
});

test("Authenticated user: can create and read their own meal log", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const mealRef = aliceDb.collection("users").doc(ALICE_UID).collection("meals").doc("meal_1");

  await assertSucceeds(mealRef.set({
    userId: ALICE_UID,
    name: "Avocado Salad",
    mealType: "LUNCH",
    calories: 380,
    proteinGrams: 12.5,
    carbsGrams: 28.0,
    fatGrams: 24.0,
    healthScore: 88,
    analysisSummary: "Healthy fats and fresh vegetables.",
    ingredients: ["Avocado", "Spinach", "Tomatoes"],
    loggedDate: "2026-10-16",
    createdAt: new Date(),
    updatedAt: new Date()
  }));

  await assertSucceeds(mealRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("meals").get());
});

test("Authenticated user: cannot write meal log with mismatched userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const mealRef = aliceDb.collection("users").doc(ALICE_UID).collection("meals").doc("meal_2");

  await assertFails(mealRef.set({
    userId: BOB_UID, // Spoofed userId
    name: "Burger",
    mealType: "DINNER",
    calories: 850,
    loggedDate: "2026-10-16",
    createdAt: new Date(),
    updatedAt: new Date()
  }));
});
